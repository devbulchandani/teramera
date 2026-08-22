package com.example.teramera.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.network.TokenStore
import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.GroupEntity
import com.example.teramera.data.local.MembershipEntity
import com.example.teramera.data.local.UserEntity
import com.example.teramera.data.local.TerameraDao
import com.example.teramera.data.repository.ExpensesRepository
import com.example.teramera.data.repository.SplitInput
import com.example.teramera.data.repository.SplitResult
import com.example.teramera.data.repository.SplitType
import com.example.teramera.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddDraft(
    val step: Int = 1, // 1 amount · 2 details · 3 participants
    val amountText: String = "",
    val title: String = "",
    val groupId: String? = null,
    val paidByUserId: String = "u_dev",
    val splitType: SplitType = SplitType.EQUAL,
    val included: Set<String> = emptySet(),
    val rawValues: Map<String, Long> = emptyMap(),
) {
    val amountMinor: Long
        get() = run {
            val clean = amountText.ifEmpty { "0" }
            val parts = clean.split(".")
            val rupees = parts[0].toLongOrNull() ?: 0L
            val paise = when (parts.getOrNull(1)) {
                null -> 0L
                else -> (parts[1].padEnd(2, '0').take(2)).toLongOrNull() ?: 0L
            }
            rupees * 100 + paise
        }
}

data class AddExpenseUiState(
    val draft: AddDraft = AddDraft(),
    val self: UserEntity? = null,
    val friends: List<UserEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val membersByGroup: Map<String, List<String>> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val serverMode: Boolean = false,
)

private data class SaveState(val saving: Boolean = false, val error: String? = null)
private data class ServerState(
    val loggedIn: Boolean = false,
    val syncedGroups: List<GroupEntity> = emptyList(),
    val selfId: String? = null,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val dao: TerameraDao,
    private val expensesRepository: ExpensesRepository,
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val SELF_ID = "u_dev"
    private val draft = MutableStateFlow(AddDraft())
    private val saveState = MutableStateFlow(SaveState())

    private val localData = combine(dao.users(), dao.groups(), dao.memberships()) { users, groups, memberships ->
        Triple(users, groups, memberships.groupBy({ it.groupId }, { it.userId }))
    }

    private val serverData = combine(dao.syncedGroups(), tokenStore.tokens) { syncedGroups, tokens ->
        ServerState(
            loggedIn = tokens != null,
            // server groups shadow the local demo seed when present
            syncedGroups = if (tokens != null) {
                syncedGroups.map { GroupEntity(id = it.id, name = it.name) }
            } else emptyList(),
            selfId = tokens?.userId,
        )
    }

    val uiState: StateFlow<AddExpenseUiState> =
        combine(localData, serverData, draft, saveState) { (users, localGroups, membersByGroup), server, d, save ->
            val useServer = server.loggedIn && server.syncedGroups.isNotEmpty()
            val effectiveGroups = if (useServer) server.syncedGroups else localGroups
            var draftOut = d
            // keep the selected group valid for whichever mode is active
            if (!effectiveGroups.any { it.id == d.groupId }) {
                draftOut = d.copy(groupId = null)
            }
            AddExpenseUiState(
                draft = draftOut,
                self = users.firstOrNull { it.isSelf },
                friends = users.filterNot { it.isSelf },
                groups = effectiveGroups,
                membersByGroup = membersByGroup,
                saving = save.saving,
                error = save.error,
                serverMode = useServer,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState())

    fun onKey(ch: Char) = draft.update {
        val t = it.amountText
        when {
            ch == '.' && (t.contains('.') || t.isEmpty()) -> it
            t.contains('.') && t.substringAfter('.').length >= 2 -> it
            t.replace(".", "").length >= 9 -> it
            else -> it.copy(amountText = t + ch)
        }
    }

    fun onBackspace() = draft.update { it.copy(amountText = it.amountText.dropLast(1)) }

    fun nextStep() = draft.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }

    fun previousStep() = draft.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }

    fun setTitle(title: String) = draft.update { it.copy(title = title) }

    fun setGroup(groupId: String?) = draft.update { draft ->
        val members = currentMembers(groupId)
        // keep only members of the newly selected group; default to all included
        draft.copy(
            groupId = groupId,
            included = if (members.isEmpty()) emptySet()
            else draft.included.filter { it in members || it == SELF_ID }.toSet().ifEmpty { setOf(SELF_ID) + members },
        )
    }

    fun setSplitType(type: SplitType) = draft.update { it.copy(splitType = type) }

    fun setPayer(userId: String) = draft.update {
        it.copy(paidByUserId = userId, included = it.included + userId)
    }

    fun toggleParticipant(userId: String) = draft.update { d ->
        // the payer must stay in the split — they fronted the money
        if (userId == d.paidByUserId) return@update d
        val next = d.included.toMutableSet()
        if (!next.add(userId)) next.remove(userId)
        d.copy(included = next)
    }

    fun setRawValue(userId: String, value: Long) =
        draft.update { it.copy(rawValues = it.rawValues + (userId to value)) }

    fun participants(state: AddExpenseUiState): List<String> {
        val pool = participantPool(state)
        return (state.draft.included.filter { it != SELF_ID } + SELF_ID).filter { it in pool }.ifEmpty { listOf(SELF_ID) }
    }

    fun participantPool(state: AddExpenseUiState): List<String> {
        val groupId = state.draft.groupId
        val base = if (groupId != null) {
            state.membersByGroup[groupId].orEmpty().filter { it != SELF_ID }
        } else {
            state.friends.map { it.id }
        }
        return base + SELF_ID
    }

    fun previewShares(state: AddExpenseUiState): List<Pair<String, Long>>? =
        when (val result = ExpensesRepository.computeSplit(currentInput(state))) {
            is SplitResult.Ok -> result.shares
            is SplitResult.Invalid -> null
        }

    fun validationError(state: AddExpenseUiState): String? =
        when (val result = ExpensesRepository.computeSplit(currentInput(state))) {
            is SplitResult.Invalid -> result.reason
            else -> null
        }

    fun save(onDone: () -> Unit) {
        val state = uiState.value
        val d = state.draft

        // Signed in with a group → push to the backend; falls back to local on failure.
        if (state.serverMode && d.groupId != null) {
            viewModelScope.launch {
                saveState.value = SaveState(saving = true)
                val result = syncRepository.pushEqualExpense(
                    groupId = d.groupId!!,
                    title = d.title.trim(),
                    amountMinor = d.amountMinor,
                )
                when (result) {
                    is SyncRepository.Result.Success -> {
                        draft.value = AddDraft()
                        saveState.value = SaveState()
                        onDone()
                    }
                    is SyncRepository.Result.Failure ->
                        saveState.value = SaveState(error = result.message)
                }
            }
            return
        }

        viewModelScope.launch {
            saveState.value = SaveState(saving = true)
            val result = expensesRepository.saveExpense(
                groupId = d.groupId,
                paidByUserId = d.paidByUserId,
                title = d.title,
                input = currentInput(state),
            )
            result.fold(
                onSuccess = {
                    draft.value = AddDraft()
                    saveState.value = SaveState()
                    onDone()
                },
                onFailure = { saveState.value = SaveState(error = it.message) },
            )
        }
    }

    private fun currentInput(state: AddExpenseUiState) = SplitInput(
        type = state.draft.splitType,
        totalMinor = state.draft.amountMinor,
        participants = participants(state),
        rawValues = state.draft.rawValues,
    )

    private fun currentMembers(groupId: String?): List<String> = uiState.value.membersByGroup[groupId].orEmpty()

    companion object {
        const val SELF = "u_dev"
    }
}
