package com.example.teramera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.data.repository.HomeData
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

data class HomeUiState(
    val friends: List<BalanceEntry> = emptyList(),
    val groups: List<BalanceEntry> = emptyList(),
    val selfName: String = "",
    val selfUpiId: String = "",
    val profileSaved: Boolean = false,
) {
    val owedToYouMinor: Long = friends.sumOf { it.amountMinor }.coerceAtLeast(0)
    val youOweMinor: Long = -friends.sumOf { it.amountMinor }.coerceAtMost(0)
    val netMinor: Long = friends.sumOf { it.amountMinor }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val profileStatus = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.homeData(),
            repository.syncedHomeData(),
            repository.selfUser(),
            profileStatus,
        ) { local, synced, self, _ ->
            from(synced ?: local).copy(
                selfName = self?.name?.ifEmpty { "You" } ?: "You",
                selfUpiId = self?.upiId.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun saveProfile(name: String, upiId: String) {
        viewModelScope.launch {
            when (syncRepository.updateProfile(name = name.trim().ifEmpty { null }, upiId = upiId.trim().ifEmpty { null })) {
                is SyncRepository.Result.Success -> {
                    profileStatus.value = !profileStatus.value
                }
                else -> Unit
            }
        }
    }

    private fun from(data: HomeData) = HomeUiState(friends = data.friends, groups = data.groups)
}
