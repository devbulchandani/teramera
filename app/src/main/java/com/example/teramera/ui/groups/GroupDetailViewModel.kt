package com.example.teramera.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.network.FoundUserDto
import com.example.teramera.core.network.GroupDetailDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.data.repository.GroupDetail
import com.example.teramera.data.repository.GroupDetailRepository
import com.example.teramera.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddMemberState(
    val visible: Boolean = false,
    val searching: Boolean = false,
    val found: FoundUserDto? = null,
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
    private val addMember = MutableStateFlow(AddMemberState())

    val detail: StateFlow<GroupDetail?> =
        combine(remote, repository.groupDetail(groupId)) { r, local -> r ?: local }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val addMemberState: StateFlow<AddMemberState> = addMember

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!syncRepository.isLoggedIn()) return@launch
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
            } catch (_: Exception) {
                // offline or not on server yet — the local fallback flow covers it
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

    fun findFriend(phone: String) {
        viewModelScope.launch {
            addMember.value = addMember.value.copy(searching = true, error = null, found = null)
            try {
                val found = ledgerApi.findUser(phone)
                addMember.value = AddMemberState(visible = true, found = found)
            } catch (e: retrofit2.HttpException) {
                val message = if (e.code() == 404) "No teramera user with that number yet" else e.message()
                addMember.value = AddMemberState(visible = true, error = message)
            } catch (e: Exception) {
                addMember.value = AddMemberState(visible = true, error = e.message ?: "Search failed")
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

    private fun memberName(detail: GroupDetailDto, userId: String): String =
        detail.members.firstOrNull { it.id == userId }?.name ?: "?"
}

private fun initialsOfName(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
