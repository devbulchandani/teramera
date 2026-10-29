package com.example.teramera.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.network.CreateExpenseRequestDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.core.network.MemberDto
import com.example.teramera.core.network.PaymentDto
import com.example.teramera.core.network.TokenStore
import com.example.teramera.data.local.GroupEntity
import com.example.teramera.data.local.TerameraDao
import com.example.teramera.data.local.UserEntity
import com.example.teramera.data.repository.ExpensesRepository
import com.example.teramera.data.repository.SplitInput
import com.example.teramera.data.repository.SplitResult
import com.example.teramera.data.repository.SplitType
import com.example.teramera.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "1234.56" → 123456 paise. */
internal fun parseAmountMinor(text: String): Long {
    val clean = text.ifEmpty { "0" }
    val parts = clean.split(".")
    val rupees = parts[0].toLongOrNull() ?: 0L
    val paise = when (parts.getOrNull(1)) {
        null -> 0L
        else -> parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    }
    return rupees * 100 + paise
}

data class AddDraft(
    val step: Int = 1, // 1 amount · 2 details · 3 participants
    val amountText: String = "",
    val title: String = "",
    val groupId: String? = null,
    val paidByUserId: String = "u_dev",
    val splitType: SplitType = SplitType.EQUAL,
    val included: Set<String> = emptySet(),
    val rawValues: Map<String, Long> = emptyMap(),
    // server mode: selected payers (userId → amount); empty = the current user paid all
    val payers: Map<String, Long> = emptyMap(),
    val payerMode: Boolean = false,
    val includedServer: Set<String> = emptySet(),
) {
    val amountMinor: Long get() = parseAmountMinor(amountText)
}

/** "Add a friend by email" — how a group-less split reaches someone you're square with. */
data class FriendSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val error: String? = null,
    val found: List<MemberDto> = emptyList(),
)

data class AddExpenseUiState(
    val draft: AddDraft = AddDraft(),
    val self: UserEntity? = null,
    val friends: List<UserEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val membersByGroup: Map<String, List<String>> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val serverMode: Boolean = false,
    /** Members of the selected group (empty outside a group). */
    val serverMembers: List<MemberDto> = emptyList(),
    /** People you already share balances with (plus anyone found by email). */
    val serverFriends: List<MemberDto> = emptyList(),
    val selfServerId: String? = null,
    val membersLoading: Boolean = false,
    val membersError: String? = null,
    val friendSearch: FriendSearchState = FriendSearchState(),
) {
    /** The signed-in user id, or the local device profile's id when offline. */
    val selfId: String
        get() = selfServerId ?: self?.id ?: AddExpenseViewModel.SELF

    /** Selected payers; empty means "you paid it all". */
    val payerIds: Set<String>
        get() = draft.payers.keys.ifEmpty { setOf(selfId) }

    /** Everyone available for the active server-mode split. */
    val serverPeople: List<MemberDto>
        get() = if (draft.groupId != null) {
            serverMembers
        } else {
            val selfMember = selfServerId?.let { id ->
                MemberDto(id = id, name = self?.name?.ifBlank { "You" } ?: "You", isSelf = true)
            }
            (listOfNotNull(selfMember) + serverFriends).distinctBy { it.id }
        }
}

private data class SaveState(val saving: Boolean = false, val error: String? = null)
private data class MemberLoad(val loading: Boolean = false, val error: String? = null)
private data class ServerState(
    val loggedIn: Boolean = false,
    val syncedGroups: List<GroupEntity> = emptyList(),
    val friends: List<MemberDto> = emptyList(),
    val selfId: String? = null,
    val friendSearch: FriendSearchState = FriendSearchState(),
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val dao: TerameraDao,
    private val expensesRepository: ExpensesRepository,
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
    private val ledgerApi: LedgerApi,
) : ViewModel() {

    private val serverMembers = MutableStateFlow<List<MemberDto>>(emptyList())

    private val SELF_ID = "u_dev"
    private val draft = MutableStateFlow(AddDraft())
    private val saveState = MutableStateFlow(SaveState())
    private val memberLoad = MutableStateFlow(MemberLoad())
    private val friendSearch = MutableStateFlow(FriendSearchState())

    private val localData = combine(dao.users(), dao.groups(), dao.memberships()) { users, groups, memberships ->
        Triple(users, groups, memberships.groupBy({ it.groupId }, { it.userId }))
    }

    private val serverData = combine(
        dao.syncedGroups(),
        dao.syncedBalances(),
        tokenStore.tokens,
        friendSearch,
    ) { syncedGroups, balances, tokens, search ->
        val settled = balances.map { MemberDto(id = it.userId, name = it.name.ifBlank { "Friend" }, isSelf = false) }
        ServerState(
            loggedIn = tokens != null,
            syncedGroups = if (tokens != null) {
                syncedGroups.map { GroupEntity(id = it.id, name = it.name) }
            } else {
                emptyList()
            },
            // friends found by email join the already-balanced ones — nobody is listed twice
            friends = (settled + search.found).distinctBy { it.id },
            selfId = tokens?.userId,
            friendSearch = search,
        )
    }

    val uiState: StateFlow<AddExpenseUiState> =
        combine(localData, serverData, draft, saveState, memberLoad) { (users, localGroups, membersByGroup), server, d, save, load ->
            val useServer = server.loggedIn
            val effectiveGroups = if (useServer) server.syncedGroups else localGroups
            var draftOut = d
            // keep the selected group valid for whichever mode is active
            if (!effectiveGroups.any { it.id == d.groupId }) {
                draftOut = d.copy(groupId = null, includedServer = emptySet())
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
                serverFriends = server.friends,
                selfServerId = server.selfId,
                membersLoading = load.loading,
                membersError = load.error,
                friendSearch = server.friendSearch,
            )
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState())

    /** Called once when the sheet opens so a previous draft never leaks in. */
    fun prepare() {
        draft.value = AddDraft()
        saveState.value = SaveState()
        memberLoad.value = MemberLoad()
        friendSearch.value = FriendSearchState()
    }

    // ---- add a friend by email (group-less splits) ----

    fun onFriendQueryChange(query: String) = friendSearch.update {
        it.copy(query = query, error = null)
    }

    fun addFriendByEmail() {
        val email = friendSearch.value.query.trim()
        if (email.isEmpty() || friendSearch.value.searching) return
        viewModelScope.launch {
            friendSearch.update { it.copy(searching = true, error = null) }
            try {
                val found = ledgerApi.findUserByEmail(email)
                val selfId = uiState.value.selfServerId
                if (found.id == selfId) {
                    friendSearch.value = FriendSearchState(
                        found = friendSearch.value.found,
                        error = "That's you — pick someone else",
                    )
                    return@launch
                }
                val person = MemberDto(id = found.id, name = found.name.ifBlank { email }, isSelf = false)
                // one emission: keep everyone found so far so earlier picks stay in the pool
                friendSearch.value = FriendSearchState(found = friendSearch.value.found + person)
                draft.update { d ->
                    d.copy(
                        includedServer = d.includedServer + found.id,
                        // the new person has no raw value yet; exact splits will prompt for it
                    )
                }
            } catch (e: retrofit2.HttpException) {
                friendSearch.value = FriendSearchState(
                    query = email,
                    found = friendSearch.value.found,
                    error = when (e.code()) {
                        404 -> "No teramera user with that email yet"
                        else -> "Search failed (${e.code()})"
                    },
                )
            } catch (e: Exception) {
                friendSearch.value = FriendSearchState(
                    query = email,
                    found = friendSearch.value.found,
                    error = e.message ?: "Couldn't reach the server",
                )
            }
        }
    }

    fun onKey(ch: Char) = draft.update { d ->
        val t = d.amountText
        val nextText = when {
            ch == '.' && (t.contains('.') || t.isEmpty()) -> t
            t.contains('.') && t.substringAfter('.').length >= 2 -> t
            t.replace(".", "").length >= 9 -> t
            else -> t + ch
        }
        d.withAmountText(nextText)
    }

    fun onBackspace() = draft.update { d -> d.withAmountText(d.amountText.dropLast(1)) }

    /** Amount changes re-spread payments so a multi-payer draft stays balanced. */
    private fun AddDraft.withAmountText(text: String): AddDraft {
        val next = copy(amountText = text)
        val payers = if (payers.isEmpty()) payers else evenPayers(payers.keys, next.amountMinor)
        return next.copy(payers = payers)
    }

    fun nextStep() = draft.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }

    fun previousStep() = draft.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }

    fun setTitle(title: String) = draft.update { it.copy(title = title) }

    fun setGroup(groupId: String?) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.serverMode) {
                if (groupId == null) {
                    draft.update { d ->
                        d.copy(
                            groupId = null,
                            includedServer = defaultServerPeople(state),
                            payers = emptyMap(),
                            payerMode = false,
                            splitType = SplitType.EQUAL,
                            rawValues = emptyMap(),
                        )
                    }
                    return@launch
                }
                if (loadServerMembers(groupId)) {
                    draft.update { d ->
                        d.copy(
                            groupId = groupId,
                            includedServer = serverMembers.value.map { it.id }.toSet(),
                            payers = emptyMap(),
                            payerMode = false,
                            splitType = SplitType.EQUAL,
                            rawValues = emptyMap(),
                        )
                    }
                }
                return@launch
            }

            draft.update { d ->
                val members = currentMembers(groupId)
                d.copy(
                    groupId = groupId,
                    included = if (members.isEmpty()) emptySet()
                    else d.included.filter { it in members || it == SELF_ID }
                        .toSet().ifEmpty { setOf(SELF_ID) + members },
                )
            }
        }
    }

    /** Sign-in with no group selected: default to you + the friends you already share with. */
    fun ensureServerDefaults() {
        val state = uiState.value
        if (!state.serverMode) return
        if (state.draft.groupId != null || state.draft.includedServer.isNotEmpty()) return
        val people = defaultServerPeople(state)
        if (people.size >= 2) draft.update { it.copy(includedServer = people) }
    }

    private fun defaultServerPeople(state: AddExpenseUiState): Set<String> =
        (listOfNotNull(state.selfServerId) + state.serverFriends.map { it.id }).toSet()

    private suspend fun loadServerMembers(groupId: String): Boolean {
        memberLoad.value = MemberLoad(loading = true)
        return try {
            serverMembers.value = ledgerApi.groupDetail(groupId).members
            memberLoad.value = MemberLoad()
            true
        } catch (e: Exception) {
            serverMembers.value = emptyList()
            memberLoad.value = MemberLoad(error = e.message ?: "Couldn't load group members")
            false
        }
    }

    fun retryMembers() {
        val groupId = draft.value.groupId ?: return
        viewModelScope.launch {
            if (loadServerMembers(groupId)) {
                draft.update { d ->
                    d.copy(includedServer = d.includedServer.ifEmpty { serverMembers.value.map { it.id }.toSet() })
                }
            }
        }
    }

    /** Selecting another payer re-splits the total evenly between all selected payers. */
    fun togglePayer(userId: String) = draft.update { d ->
        // an empty map means "you paid it all"; materialise that before adding someone
        val current = d.payers.ifEmpty { mapOf(uiState.value.selfId to d.amountMinor) }
        val next = current.keys.toMutableSet()
        if (!next.remove(userId)) next.add(userId)
        when {
            next.isEmpty() -> d.copy(payers = emptyMap())
            // one payer left → they front the whole amount
            next.size == 1 -> d.copy(payers = mapOf(next.first() to d.amountMinor))
            else -> d.copy(payers = evenPayers(next, d.amountMinor))
        }
    }

    fun setPayerAmount(userId: String, minor: Long) = draft.update { d ->
        if (userId !in d.payers) return@update d
        d.copy(payers = d.payers + (userId to minor.coerceAtLeast(0)))
    }

    fun togglePayerMode() = draft.update { d ->
        val next = !d.payerMode
        d.copy(payerMode = next, payers = if (next) d.payers else emptyMap())
    }

    fun toggleParticipantServer(userId: String) = draft.update { d ->
        val next = d.includedServer.toMutableSet()
        if (!next.remove(userId)) next.add(userId)
        if (next.isEmpty()) return@update d
        d.copy(includedServer = next)
    }

    private fun evenPayers(ids: Collection<String>, totalMinor: Long): Map<String, Long> {
        val people = ids.toList()
        if (people.isEmpty()) return emptyMap()
        val per = totalMinor / people.size
        var remainder = totalMinor - per * people.size
        return people.associateWith { per + if (remainder > 0) { remainder--; 1L } else 0L }
    }

    fun setSplitType(type: SplitType) = draft.update { it.copy(splitType = type) }

    fun toggleParticipant(userId: String) = draft.update { d ->
        // whoever fronted the money must stay in the split
        val payers = d.payers.keys.ifEmpty { setOf(d.paidByUserId) }
        if (userId in payers) return@update d
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

    // ---- server-mode split (all four types) ----

    fun serverShares(state: AddExpenseUiState): List<Pair<String, Long>>? =
        when (val result = ExpensesRepository.computeSplit(currentServerInput(state))) {
            is SplitResult.Ok -> result.shares
            is SplitResult.Invalid -> null
        }

    fun serverValidationError(state: AddExpenseUiState): String? =
        when (val result = ExpensesRepository.computeSplit(currentServerInput(state))) {
            is SplitResult.Invalid -> result.reason
            else -> null
        }

    private fun currentServerInput(state: AddExpenseUiState) = SplitInput(
        type = state.draft.splitType,
        totalMinor = state.draft.amountMinor,
        participants = state.draft.includedServer.toList(),
        rawValues = state.draft.rawValues,
    )

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val d = state.draft

            // Signed in → push to the backend (with or without a group).
            if (state.serverMode) {
                val selfId = state.selfServerId
                if (selfId == null) {
                    saveState.value = SaveState(error = "Not signed in")
                    return@launch
                }
                val participants = d.includedServer.toList()
                if (participants.size < 2) {
                    saveState.value = SaveState(error = "Pick at least two people")
                    return@launch
                }
                val split = ExpensesRepository.computeSplit(currentServerInput(state))
                if (split is SplitResult.Invalid) {
                    saveState.value = SaveState(error = split.reason)
                    return@launch
                }
                val payers = d.payers.ifEmpty { mapOf(selfId to d.amountMinor) }
                val sum = payers.values.sum()
                if (sum != d.amountMinor) {
                    saveState.value = SaveState(
                        error = "Payer amounts add up to ₹${sum / 100}, not ₹${d.amountMinor / 100}",
                    )
                    return@launch
                }
                saveState.value = SaveState(saving = true)
                try {
                    ledgerApi.createExpense(
                        CreateExpenseRequestDto(
                            groupId = d.groupId,
                            title = d.title.trim(),
                            amountMinor = d.amountMinor,
                            splitType = d.splitType.name,
                            // EQUAL uses the participant list; the other three need raw values
                            participantIds = if (d.splitType == SplitType.EQUAL) participants else null,
                            participants = if (d.splitType == SplitType.EQUAL) {
                                null
                            } else {
                                participants.associateWith { d.rawValues[it] ?: 0L }
                            },
                            payments = payers.map { (uid, minor) -> PaymentDto(uid, minor) },
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

            val localPayers = d.payers
            val localPaid = localPayers.values.sum()
            if (localPayers.isNotEmpty() && localPaid != d.amountMinor) {
                saveState.value = SaveState(
                    error = "Payer amounts add up to ₹${localPaid / 100}, not ₹${d.amountMinor / 100}",
                )
                return@launch
            }

            saveState.value = SaveState(saving = true)
            val result = expensesRepository.saveExpense(
                groupId = d.groupId,
                paidByUserId = localPayers.keys.firstOrNull() ?: d.paidByUserId,
                title = d.title,
                input = currentInput(state),
                payments = localPayers,
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
