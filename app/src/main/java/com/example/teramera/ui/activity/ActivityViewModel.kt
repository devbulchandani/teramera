package com.example.teramera.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.local.TerameraDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    // Plain map → only re-emits when *this* table changes (was previously also
    // observing selfUser, which fired on every sync, causing extra recompositions).
    val uiState: StateFlow<ActivityUiState> =
        dao.syncedActivity().map { events ->
            ActivityUiState(
                events.map { row ->
                    if (row.type == "settlement") {
                        ActivityEvent.SettlementMade(
                            id = "s${row.id}",
                            payerName = row.counterpartyName ?: "?",
                            payeeName = row.secondaryName ?: "?",
                            involvedSelf = row.involvedSelf,
                            amountMinor = row.amountMinor,
                            methodLabel = row.methodLabel ?: "",
                            createdAt = row.createdAt,
                        )
                    } else {
                        ActivityEvent.ExpenseAdded(
                            id = "e${row.id}",
                            title = row.title ?: "Expense",
                            payerName = row.counterpartyName ?: "?",
                            paidBySelf = row.paidBySelf,
                            amountMinor = row.amountMinor,
                            myShareMinor = row.myShareMinor,
                            groupName = row.groupName,
                            participantCount = row.participantCount,
                            createdAt = row.createdAt,
                        )
                    }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())
}