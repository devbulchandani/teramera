package com.example.teramera.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.repository.DebtTransfer
import com.example.teramera.data.repository.GroupDetail
import com.example.teramera.data.repository.GroupDetailRepository
import com.example.teramera.data.sync.SyncRepository
import com.example.teramera.core.network.CreateSettlementRequestDto
import com.example.teramera.core.network.GroupDetailDto
import com.example.teramera.core.network.userMessage
import com.example.teramera.core.network.LedgerApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import javax.inject.Inject

data class GroupDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: GroupDetail? = null,
)

data class AddMemberState(
    val visible: Boolean = false,
    val searching: Boolean = false,
    val found: com.example.teramera.core.network.FoundUserDto? = null,
    val error: String? = null,
    val dummyBusy: Boolean = false,
    val dummyError: String? = null,
)

data class ClaimDummyState(
    val busy: Boolean = false,
    val error: String? = null,
)

data class GroupManageState(
    val busy: Boolean = false,
    val error: String? = null,
)

data class GroupSettleState(
    val visible: Boolean = false,
    val transfer: DebtTransfer? = null,
    val method: PaymentMethod = PaymentMethod.UPI,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupDetailRepository,
    private val ledgerApi: LedgerApi,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val remote = MutableStateFlow<GroupDetail?>(null)
    private val remoteError = MutableStateFlow<String?>(null)
    private val loadingRemote = MutableStateFlow(true)
    private val addMember = MutableStateFlow(AddMemberState())
    private val localDetail = MutableStateFlow<GroupDetail?>(null)

    val detail: StateFlow<GroupDetailUiState> =
        combine(
            remote,
            localDetail,
            remoteError,
            loadingRemote,
        ) { r, local, error, loading ->
            when {
                r != null -> GroupDetailUiState(loading = false, detail = r)
                // local cache has this group (offline-created) — show it, refresh quietly
                local != null -> GroupDetailUiState(loading = false, detail = local)
                loading -> GroupDetailUiState(loading = true)
                else -> GroupDetailUiState(
                    loading = false,
                    error = error ?: "Couldn't load this group. Check your connection and try again.",
                )
            }
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState(loading = true))

    val addMemberState: StateFlow<AddMemberState> = addMember

    init {
        refresh()
        // Local cache is a bonus, never a gate. If Room is slow or wedged, the
        // remote result below still reaches the screen.
        viewModelScope.launch {
            repository.groupDetail(groupId)
                .timeout(5.seconds)
                .catch { /* remote state is enough */ }
                .collect { localDetail.value = it }
        }
        // re-fetch whenever any sync completes (expense added, settle, etc.)
        viewModelScope.launch {
            syncRepository.dataVersion.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadingRemote.value = true
            remoteError.value = null
            try {
                // Reading the auth store can itself stall/corrupt; keep it inside
                // the timeout and the finally block so Loading can never stick.
                val loggedIn = withTimeoutOrNull(5_000) { syncRepository.isLoggedIn() }
                if (loggedIn != true) {
                    remoteError.value = if (loggedIn == null) {
                        "Couldn't read your session. Try again."
                    } else {
                        "Sign in again to see this group."
                    }
                    return@launch
                }
                val d = withTimeoutOrNull(12_000) { ledgerApi.groupDetail(groupId) }
                if (d == null) {
                    remoteError.value = "Loading timed out. Check your connection and try again."
                    return@launch
                }
                val selfId = syncRepository.selfUserId().orEmpty()
                remote.value = GroupDetail(
                    groupId = d.id,
                    groupName = d.name,
                    selfUserId = selfId,
                    memberInitials = d.members.map { initialsOfName(it.name) to it.isSelf },
                    memberIds = d.members.map { it.id },
                    totalSpentMinor = d.totalSpentMinor,
                    expenses = d.expenses.map {
                        com.example.teramera.data.repository.ExpenseLine(
                            id = it.id,
                            title = it.title,
                            payerName = memberName(d, it.paidByUserId),
                            canDelete = it.canDelete,
                            payerCount = it.payerCount,
                            amountMinor = it.amountMinor,
                            myShareMinor = it.myShareMinor,
                            participantCount = it.participantCount,
                            createdAt = it.createdAt,
                        )
                    },
                    simplifiedDebts = d.simplifiedDebts.map {
                        com.example.teramera.data.repository.DebtTransfer(
                            fromUserId = it.fromUserId,
                            fromName = it.fromName,
                            toUserId = it.toUserId,
                            toName = it.toName,
                            amountMinor = it.amountMinor,
                        )
                    },
                    worstCasePayments = d.worstCasePayments,
                    isOwner = d.createdBy.isNotEmpty() && d.createdBy == selfId,
                    dummyMembers = d.members
                        .filter { it.isDummy }
                        .map { com.example.teramera.data.repository.DummyMember(it.id, it.name) },
                    members = d.members.map {
                        com.example.teramera.data.repository.GroupMember(
                            id = it.id,
                            name = it.name,
                            isSelf = it.isSelf,
                            isDummy = it.isDummy,
                            paidMinor = it.paidMinor,
                        )
                    },
                )
            } catch (e: retrofit2.HttpException) {
                remoteError.value = when (e.code()) {
                    403 -> "You're not a member of this group."
                    404 -> {
                        syncRepository.removeLocalGroup(groupId)
                        "This group no longer exists. It has been removed from your list."
                    }
                    else -> "Server error (${e.code()})"
                }
            } catch (e: Exception) {
                remoteError.value = e.userMessage("Couldn't reach the server.")
            } finally {
                loadingRemote.value = false
            }
        }
    }

    // ---- settle up (group-scoped) ----

    private val settleState = MutableStateFlow(GroupSettleState())
    val settle: StateFlow<GroupSettleState> = settleState

    /** Opens the picker; choosing a transfer switches the sheet to that debt. */
    fun openSettlePicker() {
        settleState.value = GroupSettleState(visible = true)
    }

    fun startSettle(transfer: DebtTransfer) {
        settleState.value = GroupSettleState(visible = true, transfer = transfer)
    }

    fun dismissSettle() {
        if (!settleState.value.busy) settleState.value = GroupSettleState()
    }

    fun setSettleMethod(method: PaymentMethod) {
        settleState.value = settleState.value.copy(method = method)
    }

    /** Records a payment against a simplified group debt, in either direction. */
    fun confirmSettle() {
        val transfer = settleState.value.transfer ?: return
        if (settleState.value.busy) return
        viewModelScope.launch {
            settleState.value = settleState.value.copy(busy = true, error = null)
            try {
                ledgerApi.createSettlement(
                    CreateSettlementRequestDto(
                        groupId = groupId,
                        payerUserId = transfer.fromUserId,
                        paidToUserId = transfer.toUserId,
                        amountMinor = transfer.amountMinor,
                        method = settleState.value.method.name,
                    )
                )
                settleState.value = GroupSettleState()
                syncRepository.refreshNow()
                refresh()
            } catch (e: Exception) {
                settleState.value = settleState.value.copy(
                    busy = false,
                    error = e.userMessage("Couldn't record the payment"),
                )
            }
        }
    }

    // ---- manage group (rename / delete) ----

    private val manageState = MutableStateFlow(GroupManageState())
    val manage: StateFlow<GroupManageState> = manageState

    fun clearManageError() {
        manageState.value = manageState.value.copy(error = null)
    }

    fun renameGroup(name: String, onDone: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || manageState.value.busy) return
        viewModelScope.launch {
            manageState.value = GroupManageState(busy = true)
            try {
                ledgerApi.updateGroup(
                    groupId,
                    com.example.teramera.core.network.UpdateGroupRequestDto(trimmed),
                )
                manageState.value = GroupManageState()
                syncRepository.refreshNow()
                refresh()
                onDone()
            } catch (e: Exception) {
                manageState.value = GroupManageState(error = e.userMessage("Couldn't rename group"))
            }
        }
    }

    fun deleteGroup(onDone: () -> Unit) {
        if (manageState.value.busy) return
        viewModelScope.launch {
            manageState.value = GroupManageState(busy = true)
            try {
                ledgerApi.deleteGroup(groupId)
                manageState.value = GroupManageState()
                syncRepository.purgeDeletedGroup(groupId)
                syncRepository.refreshNow()
                onDone()
            } catch (e: Exception) {
                manageState.value = GroupManageState(error = e.userMessage("Couldn't delete group"))
            }
        }
    }

    // ---- add member ----

    fun showAddMember() {
        addMember.value = AddMemberState(visible = true)
    }

    fun dismissAddMember() {
        addMember.value = AddMemberState()
    }

    fun findFriend(query: String) {
        viewModelScope.launch {
            addMember.value = addMember.value.copy(searching = true, error = null, found = null)
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@launch
            try {
                // v0.3.4: phone lookup was removed; only email-based friend add is supported.
                val found = ledgerApi.findUserByEmail(trimmed)
                addMember.value = AddMemberState(visible = true, found = found)
            } catch (e: retrofit2.HttpException) {
                val message = when {
                    e.code() == 404 -> "No teramera user with that email yet"
                    else -> e.userMessage("Search failed")
                }
                addMember.value = AddMemberState(visible = true, error = message)
            } catch (e: Exception) {
                addMember.value = AddMemberState(visible = true, error = e.userMessage("Search failed"))
            }
        }
    }

    /** Emails a download+join link to someone who hasn't signed up yet. */
    fun inviteByEmail(email: String) {
        viewModelScope.launch {
            addMember.value = addMember.value.copy(searching = true, error = null)
            try {
                val status = syncRepository.inviteByEmail(groupId, email)
                addMember.value = AddMemberState(
                    visible = true,
                    error = when (status) {
                        "sent" -> "Invite email sent ✓"
                        "logged" -> "Invite link generated — email sending isn't configured on the server yet"
                        null -> "They haven't signed up, so there's no account to invite by email"
                        else -> status
                    },
                )
            } catch (e: Exception) {
                addMember.value = addMember.value.copy(searching = false, error = e.userMessage("Couldn't send invite"))
            }
        }
    }

    fun confirmAddMember(userId: String) {
        viewModelScope.launch {
            addMember.value = addMember.value.copy(searching = true, error = null)
            try {
                ledgerApi.addMember(groupId, com.example.teramera.core.network.AddMemberRequestDto(userId))
                addMember.value = AddMemberState()
                syncRepository.refreshNow()
                refresh()
            } catch (e: Exception) {
                addMember.value = addMember.value.copy(searching = false, error = e.userMessage("Couldn't add"))
            }
        }
    }

    /** Adds someone who hasn't signed up yet; they can claim this later. */
    fun addDummyMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || addMember.value.dummyBusy) return
        viewModelScope.launch {
            addMember.value = addMember.value.copy(dummyBusy = true, dummyError = null)
            try {
                ledgerApi.addDummyMember(
                    groupId,
                    com.example.teramera.core.network.AddDummyMemberRequestDto(trimmed),
                )
                addMember.value = AddMemberState()
                syncRepository.refreshNow()
                refresh()
            } catch (e: Exception) {
                addMember.value = addMember.value.copy(
                    dummyBusy = false,
                    dummyError = e.userMessage("Couldn't add placeholder"),
                )
            }
        }
    }

    // ---- claim a placeholder after joining ----

    private val claimState = MutableStateFlow(ClaimDummyState())
    val claim: StateFlow<ClaimDummyState> = claimState

    fun claimDummy(dummyUserId: String, onDone: () -> Unit) {
        if (claimState.value.busy) return
        viewModelScope.launch {
            claimState.value = ClaimDummyState(busy = true)
            try {
                ledgerApi.claimDummy(
                    groupId,
                    com.example.teramera.core.network.ClaimDummyRequestDto(dummyUserId),
                )
                claimState.value = ClaimDummyState()
                syncRepository.refreshNow()
                refresh()
                onDone()
            } catch (e: Exception) {
                claimState.value = ClaimDummyState(error = e.userMessage("Couldn't claim that member"))
            }
        }
    }

    private var lastQuery: String? = null

    private fun memberName(detail: GroupDetailDto, userId: String): String =
        detail.members.firstOrNull { it.id == userId }?.name ?: "?"

    // ---- edit / delete expense ----

    data class ExpenseEditState(
        val visible: Boolean = false,
        val line: com.example.teramera.data.repository.ExpenseLine? = null,
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val expenseEdit = MutableStateFlow(ExpenseEditState())
    val expenseEditState: StateFlow<ExpenseEditState> = expenseEdit

    fun showEditExpense(line: com.example.teramera.data.repository.ExpenseLine) {
        expenseEdit.value = ExpenseEditState(visible = true, line = line)
    }

    fun dismissEditExpense() {
        expenseEdit.value = ExpenseEditState()
    }

    fun saveExpense(title: String, amountMinor: Long) {
        val line = expenseEdit.value.line ?: return
        if (expenseEdit.value.busy) return
        viewModelScope.launch {
            expenseEdit.value = expenseEdit.value.copy(busy = true, error = null)
            try {
                ledgerApi.updateExpense(
                    line.id,
                    com.example.teramera.core.network.UpdateExpenseRequestDto(
                        title = title.trim(),
                        amountMinor = amountMinor,
                    ),
                )
                expenseEdit.value = ExpenseEditState()
                syncRepository.refreshNow()
                refresh()
            } catch (e: Exception) {
                expenseEdit.value =
                    expenseEdit.value.copy(busy = false, error = e.userMessage("Couldn't update"))
            }
        }
    }

    fun deleteExpense() {
        val line = expenseEdit.value.line ?: return
        if (expenseEdit.value.busy) return
        viewModelScope.launch {
            expenseEdit.value = expenseEdit.value.copy(busy = true, error = null)
            try {
                ledgerApi.deleteExpense(line.id)
                expenseEdit.value = ExpenseEditState()
                syncRepository.refreshNow()
                refresh()
            } catch (e: Exception) {
                expenseEdit.value =
                    expenseEdit.value.copy(busy = false, error = e.userMessage("Couldn't delete"))
            }
        }
    }
}private fun initialsOfName(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
