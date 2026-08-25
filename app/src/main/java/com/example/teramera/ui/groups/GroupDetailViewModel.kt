package com.example.teramera.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.repository.GroupDetail
import com.example.teramera.data.repository.GroupDetailRepository
import com.example.teramera.data.sync.SyncRepository
import com.example.teramera.core.network.GroupDetailDto
import com.example.teramera.core.network.LedgerApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: GroupDetailRepository,
    private val ledgerApi: LedgerApi,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val remote = MutableStateFlow<GroupDetail?>(null)
    private val remoteError = MutableStateFlow<String?>(null)
    private val loadingRemote = MutableStateFlow(true)
    private val addMember = MutableStateFlow(AddMemberState())

    val detail: StateFlow<GroupDetailUiState> =
        combine(remote, repository.groupDetail(groupId), remoteError, loadingRemote) { r, local, error, loading ->
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState(loading = true))

    val addMemberState: StateFlow<AddMemberState> = addMember

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadingRemote.value = true
            remoteError.value = null
            if (!syncRepository.isLoggedIn()) {
                // not signed in — local Room data (if any) is the only source
                loadingRemote.value = false
                return@launch
            }
            try {
                val d = ledgerApi.groupDetail(groupId)
                remote.value = GroupDetail(
                    groupId = d.id,
                    groupName = d.name,
                    memberInitials = d.members.map { initialsOfName(it.name) to it.isSelf },
                    memberIds = d.members.map { it.id },
                    totalSpentMinor = d.totalSpentMinor,
                    expenses = d.expenses.map {
                        com.example.teramera.data.repository.ExpenseLine(
                            id = it.id,
                            title = it.title,
                            payerName = memberName(d, it.paidByUserId),
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
                    worstCasePayments = 0,
                )
            } catch (e: retrofit2.HttpException) {
                remoteError.value = when (e.code()) {
                    403 -> "You're not a member of this group."
                    404 -> "This group no longer exists."
                    else -> "Server error (${e.code()})"
                }
            } catch (e: Exception) {
                remoteError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Couldn't reach the server."
            } finally {
                loadingRemote.value = false
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
            lastQuery = query.trim()
            try {
                val found = if (query.contains("@")) ledgerApi.findUserByEmail(query.trim())
                else ledgerApi.findUser(query.trim())
                addMember.value = AddMemberState(visible = true, found = found)
            } catch (e: retrofit2.HttpException) {
                val message = when {
                    e.code() == 404 && query.contains("@") -> "No teramera user with that email yet"
                    e.code() == 404 -> "No teramera user with that number yet"
                    else -> e.message()
                }
                addMember.value = AddMemberState(visible = true, error = message)
            } catch (e: Exception) {
                addMember.value = AddMemberState(visible = true, error = e.message ?: "Search failed")
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
                addMember.value = addMember.value.copy(searching = false, error = e.message ?: "Couldn't send invite")
            }
        }
    }

    fun confirmAddMember(userId: String) {
        viewModelScope.launch {
            addMember.value = addMember.value.copy(searching = true, error = null)
            try {
                ledgerApi.addMember(groupId, com.example.teramera.core.network.AddMemberRequestDto(userId))
                addMember.value = AddMemberState()
                refresh()
            } catch (e: Exception) {
                addMember.value = addMember.value.copy(searching = false, error = e.message ?: "Couldn't add")
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

    fun saveExpense(title: String, rupees: Long) {
        val line = expenseEdit.value.line ?: return
        if (expenseEdit.value.busy) return
        viewModelScope.launch {
            expenseEdit.value = expenseEdit.value.copy(busy = true, error = null)
            try {
                ledgerApi.updateExpense(
                    line.id,
                    com.example.teramera.core.network.UpdateExpenseRequestDto(
                        title = title.trim(),
                        amountMinor = rupees * 100,
                    ),
                )
                expenseEdit.value = ExpenseEditState()
                refresh()
            } catch (e: Exception) {
                expenseEdit.value =
                    expenseEdit.value.copy(busy = false, error = e.message ?: "Couldn't update")
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
                refresh()
            } catch (e: Exception) {
                expenseEdit.value =
                    expenseEdit.value.copy(busy = false, error = e.message ?: "Couldn't delete")
            }
        }
    }
}private fun initialsOfName(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
