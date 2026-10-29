package com.example.teramera.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.data.repository.HomeRepository
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

data class SettleDraft(
    val person: BalanceEntry,
    val amountMinor: Long,
    val method: PaymentMethod = PaymentMethod.UPI,
    val customMode: Boolean = false,
) {
    val fullAmount: Long get() = kotlin.math.abs(person.amountMinor)
    val isFull: Boolean get() = !customMode && amountMinor == fullAmount
}

data class SettleUiState(
    val balances: List<BalanceEntry> = emptyList(),
    val draft: SettleDraft? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettleViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    homeRepository: HomeRepository,
) : ViewModel() {

    private val draft = MutableStateFlow<SettleDraft?>(null)
    private val status = MutableStateFlow(SaveStatus())

    val uiState: StateFlow<SettleUiState> =
        combine(homeRepository.homeData(), homeRepository.syncedHomeData(), draft, status) { local, synced, d, st ->
            val source = synced ?: local
            val balances = source.friends.filter { it.amountMinor != 0L }
            // keep the open sheet pointed at the live balance, not the snapshot
            // taken when it opened
            val liveDraft = d?.let { current ->
                balances.firstOrNull { it.id == current.person.id }
                    ?.let { current.copy(person = it) }
                    ?: current
            }
            SettleUiState(
                // groups are aggregates of friend debts — settling one would be meaningless
                balances = source.friends.filter { it.amountMinor != 0L },
                draft = liveDraft,
                saving = st.saving,
                saved = st.saved,
                error = st.error,
            )
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleUiState())

    fun start(entry: BalanceEntry) {
        status.value = SaveStatus()
        draft.value = SettleDraft(
            person = entry,
            amountMinor = kotlin.math.abs(entry.amountMinor),
        )
    }

    /** Dismisses the sheet from any stage, including the "settled" confirmation. */
    fun clear() {
        draft.value = null
        status.value = SaveStatus()
    }

    fun setFull() = draft.update { it?.copy(customMode = false, amountMinor = it.fullAmount) }

    fun setHalf() = draft.update { it?.copy(customMode = false, amountMinor = it.fullAmount / 2) }

    fun startCustom() = draft.update { it?.copy(customMode = true, amountMinor = 0L) }

    fun onKey(ch: Char) = draft.update { d ->
        d?.takeIf { it.customMode } ?: return@update d
        val current = (d.amountMinor / 100).toString() + ch
        d.copy(amountMinor = (current.toLongOrNull() ?: 0L).coerceAtMost(d.fullAmount) * 100)
    }

    fun onBackspace() = draft.update { d ->
        d?.takeIf { it.customMode } ?: return@update d
        val current = (d.amountMinor / 100).toString().dropLast(1)
        d.copy(amountMinor = (current.toLongOrNull() ?: 0L) * 100)
    }

    fun setMethod(method: PaymentMethod) = draft.update { it?.copy(method = method) }

    fun save(onDone: () -> Unit) {
        val d = uiState.value.draft ?: return
        if (d.amountMinor <= 0 || status.value.saving) return
        // Re-read the live balance: the other side may have settled, or another
        // device may have added an expense while this sheet was open.
        val live = uiState.value.balances.firstOrNull { it.id == d.person.id }
        if (live == null || live.amountMinor == 0L) {
            status.value = SaveStatus(error = "This balance just changed. Close and check again.")
            return
        }
        val outstanding = kotlin.math.abs(live.amountMinor)
        if (d.amountMinor > outstanding) {
            status.value = SaveStatus(
                error = "Only ₹${outstanding / 100} is still outstanding. Close and check again.",
            )
            return
        }
        viewModelScope.launch {
            status.value = SaveStatus(saving = true)
            when (val result = syncRepository.pushSettlement(
                personUserId = live.id,
                personNetMinor = live.amountMinor,
                amountMinor = d.amountMinor,
                method = d.method.name,
            )) {
                is SyncRepository.Result.Success -> {
                    status.value = SaveStatus(saved = true)
                    onDone()
                }
                is SyncRepository.Result.Failure -> {
                    // Nothing is written locally: a settlement that never reached the
                    // server would show as "paid" while everyone else still owes it.
                    status.value = SaveStatus(error = result.message)
                }
            }
        }
    }

    private data class SaveStatus(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)
}
