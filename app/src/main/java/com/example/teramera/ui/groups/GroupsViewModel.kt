package com.example.teramera.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.network.CreateGroupRequestDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.core.network.userMessage
import com.example.teramera.data.repository.HomeRepository
import com.example.teramera.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<com.example.teramera.data.repository.BalanceEntry> = emptyList(),
    val creating: Boolean = false,
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    homeRepository: HomeRepository,
    private val ledgerApi: LedgerApi,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val creation = MutableStateFlow(Pair(false, null as String?))
    private data class RefreshState(
        val refreshing: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null,
    )
    private val refreshState = MutableStateFlow(RefreshState())

    val uiState: StateFlow<GroupsUiState> =
        combine(
            homeRepository.homeData(),
            homeRepository.syncedHomeData(),
            creation,
            refreshState,
        ) { local, synced, (busy, createError), refresh ->
            val chosen = synced ?: local
            GroupsUiState(
                groups = chosen.groups,
                creating = busy,
                refreshing = refresh.refreshing,
                loaded = refresh.loaded || chosen.groups.isNotEmpty(),
                error = createError ?: refresh.error,
            )
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    init { refresh() }

    /** Explicit recovery path when the first sync never landed. */
    fun refresh() {
        if (refreshState.value.refreshing) return
        viewModelScope.launch {
            refreshState.value = RefreshState(refreshing = true, loaded = refreshState.value.loaded)
            val result = withTimeoutOrNull(15_000) { syncRepository.refreshNow() }
            refreshState.value = RefreshState(
                refreshing = false,
                loaded = true,
                error = when (result) {
                    null -> "Loading timed out. Check your connection and try again."
                    is SyncRepository.Result.Failure -> result.message
                    is SyncRepository.Result.Success -> null
                },
            )
        }
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            creation.value = true to null
            try {
                val created = ledgerApi.createGroup(CreateGroupRequestDto(name = trimmed, currency = null, memberUserIds = null))
                syncRepository.refreshNow()
                creation.value = false to null
                onCreated(created.id)
            } catch (e: Exception) {
                creation.value = false to e.userMessage("Couldn't create group")
            }
        }
    }
}
