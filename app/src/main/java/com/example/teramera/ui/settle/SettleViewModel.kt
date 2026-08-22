package com.example.teramera.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.local.SettlementEntity
import com.example.teramera.data.local.TerameraDao
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.data.repository.HomeRepository
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
    private val dao: TerameraDao,
    private val syncRepository: SyncRepository,
    homeRepository: HomeRepository,
) : ViewModel() {

    private val LOCAL_SELF = "u_dev"
    private val draft = MutableStateFlow<SettleDraft?>(null)
    private val status = MutableStateFlow(SaveStatus())

    val uiState: StateFlow<SettleUiState> =
        combine(homeRepository.homeData(), homeRepository.syncedHomeData(), draft, status) { local, synced, d, st ->
            val source = synced ?: local
            SettleUiState(
                balances = (source.friends + source.groups).filter { it.amountMinor != 0L },
                draft = d,
                saving = st.saving,
                saved = st.saved,
                error = st.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleUiState())

    fun start(entry: BalanceEntry) {
        status.value = SaveStatus()
        draft.value = SettleDraft(
            person = entry,
            amountMinor = kotlin.math.abs(entry.amountMinor),
        )
    }

    fun clear() {
        if (status.value.saved) return // stay on the done screen
        draft.value = null
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
        val state = uiState.value
        val d = state.draft ?: return
        if (d.amountMinor <= 0 || d.amountMinor > d.fullAmount || status.value.saving) return
        viewModelScope.launch {
            if (syncRepository.isLoggedIn()) {
                status.value = SaveStatus(saving = true)
                when (val result = syncRepository.pushSettlement(
                    personUserId = d.person.id,
                    personNetMinor = d.person.amountMinor,
                    amountMinor = d.amountMinor,
                    method = d.method.name,
                )) {
                    is SyncRepository.Result.Success -> {
                        status.value = SaveStatus(saved = true)
                        onDone()
                    }
                    is SyncRepository.Result.Failure -> {
                        // offline: keep the record locally so nothing is lost
                        insertLocally(d)
                        status.value = SaveStatus(saved = true)
                        onDone()
                    }
                }
                return@launch
            }

            status.value = SaveStatus(saving = true)
            insertLocally(d)
            status.value = SaveStatus(saved = true)
        }
    }

    private suspend fun insertLocally(d: SettleDraft) {
        // positive balance = they owe me → they pay me
        val (payer, payee) = if (d.person.amountMinor > 0) d.person.id to LOCAL_SELF else LOCAL_SELF to d.person.id
        dao.insertSettlement(
            SettlementEntity(
                groupId = null,
                payerUserId = payer,
                paidToUserId = payee,
                amountMinor = d.amountMinor,
                method = d.method.name,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private data class SaveStatus(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)
}
