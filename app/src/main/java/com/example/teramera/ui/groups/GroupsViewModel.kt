package com.example.teramera.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.network.CreateGroupRequestDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.data.repository.HomeRepository
import com.example.teramera.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<com.example.teramera.data.repository.BalanceEntry> = emptyList(),
    val creating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    homeRepository: HomeRepository,
    private val ledgerApi: LedgerApi,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val creation = MutableStateFlow(Pair(false, null as String?))

    val uiState: StateFlow<GroupsUiState> =
        combine(homeRepository.homeData(), homeRepository.syncedHomeData(), creation) { local, synced, (busy, error) ->
            val chosen = synced ?: local
            GroupsUiState(groups = chosen.groups, creating = busy, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

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
                creation.value = false to (e.message ?: "Couldn't create group")
            }
        }
    }
}
