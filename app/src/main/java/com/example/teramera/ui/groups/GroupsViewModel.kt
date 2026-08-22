package com.example.teramera.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GroupsUiState(val groups: List<com.example.teramera.data.repository.BalanceEntry> = emptyList())

@HiltViewModel
class GroupsViewModel @Inject constructor(
    homeRepository: HomeRepository,
) : ViewModel() {

    val uiState: StateFlow<GroupsUiState> =
        combine(homeRepository.homeData(), homeRepository.syncedHomeData()) { local, synced ->
            val chosen = synced ?: local
            GroupsUiState(groups = chosen.groups)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())
}
