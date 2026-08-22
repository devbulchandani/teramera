package com.example.teramera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.data.repository.HomeData
import com.example.teramera.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val friends: List<BalanceEntry> = emptyList(),
    val groups: List<BalanceEntry> = emptyList(),
) {
    val owedToYouMinor: Long = friends.sumOf { it.amountMinor }.coerceAtLeast(0)
    val youOweMinor: Long = -friends.sumOf { it.amountMinor }.coerceAtMost(0)
    val netMinor: Long = friends.sumOf { it.amountMinor }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: HomeRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.homeData(), repository.syncedHomeData()) { local, synced ->
            from(synced ?: local)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun from(data: com.example.teramera.data.repository.HomeData) =
        HomeUiState(friends = data.friends, groups = data.groups)
}
