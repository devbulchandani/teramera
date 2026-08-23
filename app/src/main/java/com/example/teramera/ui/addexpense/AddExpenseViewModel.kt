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
    // server mode: selected payers (userId → amount); single payer defaults to full amount
    val payers: Map<String, Long> = emptyMap(),
    val payerMode: Boolean = false,
    val includedServer: Set<String> = emptySet(),
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
    val serverMembers: List<com.example.teramera.core.network.MemberDto> = emptyList(),
    val selfServerId: String? = null,
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
    private val ledgerApi: com.example.teramera.core.network.LedgerApi,
) : ViewModel() {

    private val serverMembers = MutableStateFlow<List<com.example.teramera.core.network.MemberDto>>(emptyList())

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
                serverMembers = serverMembers.value,
                selfServerId = server.selfId,
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

    fun setGroup(groupId: String?) {
        viewModelScope.launch {
            val isServerGroup = groupId != null &&
                uiState.value.serverMode &&
                uiState.value.groups.any { it.id == groupId }
            if (isServerGroup) {
                try {
                    serverMembers.value = ledgerApi.groupDetail(groupId!!).members
                    draft.update { d ->
                        d.copy(
                            groupId = groupId,
                            includedServer = serverMembers.value.map { it.id }.toSet(),
                            payers = emptyMap(), // defaults to self paying full
                            payerMode = false,
                            splitType = SplitType.EQUAL,
                        )
                    }
                    return@launch
                } catch (_: Exception) {
                    // fall through to local behaviour
                }
            }
            draft.update { draft ->
                val members = currentMembers(groupId)
                draft.copy(
                    groupId = groupId,
                    included = if (members.isEmpty()) emptySet()
                    else draft.included.filter { it in members || it == SELF_ID }
                        .toSet().ifEmpty { setOf(SELF_ID) + members },
                )
            }
        }
    }

    /** Server-mode payer toggling; amounts auto-default to an even share of the total. */
    fun togglePayer(userId: String) = draft.update { d ->
        val next = d.payers.toMutableMap()
        if (!next.remove(userId).let { it != null }) next[userId] = 0L
        d.copy(payers = normalizePayers(next, d.amountMinor, d.payerMode))
    }

    fun setPayerAmount(userId: String, minor: Long) = draft.update { d ->
        d.copy(payers = normalizePayers(d.payers + (userId to minor.coerceAtLeast(0)), d.amountMinor, true))
    }

    fun togglePayerMode() = draft.update { d ->
        d.copy(payerMode = !d.payerMode, payers = normalizePayers(d.payers, d.amountMinor, !d.payerMode))
    }

    fun toggleParticipantServer(userId: String) = draft.update { d ->
        // the current payer set must stay inside the split
        val next = d.includedServer.toMutableSet()
        if (!next.remove(userId)) next.add(userId)
        if (next.isEmpty()) return@update d
        if (d.payers.keys.any { it !in next } && d.paymentsSum() > 0) return@update d
        d.copy(includedServer = next)
    }

    private fun AddDraft.paymentsSum(): Long =
        if (payers.isEmpty()) amountMinor else payers.values.sum()

    private fun normalizePayers(
        payers: Map<String, Long>,
        totalMinor: Long,
        multi: Boolean,
    ): Map<String, Long> {
        if (payers.isEmpty()) return emptyMap()
        return when {
            !multi || payers.size == 1 ->
                payers.mapValues { totalMinor } // single payer fronts the whole amount
            else -> {
                // even default for un-edited entries; edited ones keep their value.
                // caller validates the sum before save.
                val zeroed = payers.filterValues { it == 0L }
                if (zeroed.isEmpty()) payers
                else {
                    val each = totalMinor / payers.size
                    var remainder = totalMinor - each * payers.size
                    val result = payers.toMutableMap()
                    for ((uid, v) in result) {
                        if (v == 0L) {
                            result[uid] = each + if (remainder > 0) { remainder--; 1 } else 0
                        }
                    }
                    result
                }
            }
        }
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
        viewModelScope.launch {
            val state = uiState.value
            val d = state.draft

        // Signed in with a group → push to the backend.
        if (state.serverMode && d.groupId != null) {
            val selfId = syncRepository.selfUserId()
            if (selfId == null) {
                saveState.value = SaveState(error = "Not signed in")
                return@launch
            }
            val payers = d.payers.ifEmpty { mapOf(selfId to d.amountMinor) }
            val sum = payers.values.sum()
            if (sum != d.amountMinor) {
                saveState.value = SaveState(error =
                    "Payer amounts add up to ₹${sum / 100}, not ₹${d.amountMinor / 100}")
                return@launch
            }
                saveState.value = SaveState(saving = true)
                try {
                    ledgerApi.createExpense(
                        com.example.teramera.core.network.CreateExpenseRequestDto(
                            groupId = d.groupId,
                            title = d.title.trim(),
                            amountMinor = d.amountMinor,
                            splitType = "EQUAL",
                            participantIds = d.includedServer.toList(),
                            payments = payers.map { (uid, minor) ->
                                com.example.teramera.core.network.PaymentDto(uid, minor)
                            },
                        )
                    )
                    syncRepository.refreshNow()
                    draft.value = AddDraft()
                    saveState.value = SaveState()
                    onDone()
                } catch (e: Exception) {
                    saveState.value = SaveState(error = e.message ?: "Couldn't reach the server")
                }
            return@launch
        }

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
