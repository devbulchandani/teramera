package com.example.teramera.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.local.TerameraDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class ActivityEvent {
    abstract val id: String
    abstract val createdAt: Long

    data class ExpenseAdded(
        override val id: String,
        val title: String,
        val payerName: String,
        val paidBySelf: Boolean,
        val amountMinor: Long,
        val myShareMinor: Long,
        val groupName: String?,
        val participantCount: Int,
        override val createdAt: Long,
    ) : ActivityEvent()

    data class SettlementMade(
        override val id: String,
        val payerName: String,
        val payeeName: String,
        val involvedSelf: Boolean,
        val amountMinor: Long,
        val methodLabel: String,
        override val createdAt: Long,
    ) : ActivityEvent()
}

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList(),
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    dao: TerameraDao,
) : ViewModel() {

    private val SELF_ID = "u_dev"

    val uiState: StateFlow<ActivityUiState> =
        combine(dao.users(), dao.groups(), dao.expenses(), dao.shares(), dao.settlements()) {
                users, groups, expenses, shares, settlements ->
            val usersById = users.associateBy { it.id }
            val groupsById = groups.associateBy { it.id }
            val sharesByExpense = shares.groupBy { it.expenseId }

            val expenseEvents = expenses.map { expense ->
                val expenseShares = sharesByExpense[expense.id].orEmpty()
                ActivityEvent.ExpenseAdded(
                    id = "e${expense.id}",
                    title = expense.title,
                    payerName = usersById[expense.paidByUserId]?.name ?: "?",
                    paidBySelf = expense.paidByUserId == SELF_ID,
                    amountMinor = expense.amountMinor,
                    myShareMinor = expenseShares.firstOrNull { it.userId == SELF_ID }?.amountMinor ?: 0L,
                    groupName = expense.groupId?.let { groupsById[it]?.name },
                    participantCount = expenseShares.size,
                    createdAt = expense.createdAt,
                )
            }

            val settlementEvents = settlements.map { s ->
                ActivityEvent.SettlementMade(
                    id = "s${s.id}",
                    payerName = usersById[s.payerUserId]?.name ?: "?",
                    payeeName = usersById[s.paidToUserId]?.name ?: "?",
                    involvedSelf = s.payerUserId == SELF_ID || s.paidToUserId == SELF_ID,
                    amountMinor = s.amountMinor,
                    methodLabel = s.method.lowercase().replaceFirstChar { it.uppercase() },
                    createdAt = s.createdAt,
                )
            }

            ActivityUiState((expenseEvents + settlementEvents).sortedByDescending { it.createdAt })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())
}
