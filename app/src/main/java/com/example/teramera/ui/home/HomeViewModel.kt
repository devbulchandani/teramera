package com.example.teramera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.BuildConfig
import com.example.teramera.core.update.AppUpdateManager
import com.example.teramera.core.update.UpdateInfo
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.data.repository.HomeData
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
import javax.inject.Inject

data class HomeUiState(
    val friends: List<BalanceEntry> = emptyList(),
    val groups: List<BalanceEntry> = emptyList(),
    val selfName: String = "",
    val selfUpiId: String = "",
    val profileSaved: Boolean = false,
    val lastSyncAt: Long? = null,
) {
    val owedToYouMinor: Long = friends.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    val youOweMinor: Long = -friends.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }
    val netMinor: Long = friends.sumOf { it.amountMinor }
}

/** Result of the "check for updates" button in the version dialog. */
data class UpdateCheck(
    val status: Status = Status.Idle,
    val available: UpdateInfo? = null,
) {
    enum class Status { Idle, Checking, UpToDate, Available }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val syncRepository: SyncRepository,
    private val updateManager: AppUpdateManager,
) : ViewModel() {

    private val profileStatus = MutableStateFlow(false)

    private val _updateCheck = MutableStateFlow(UpdateCheck())
    val updateCheck: StateFlow<UpdateCheck> = _updateCheck

    /** Version shown in the dialog; the slug comes from the build itself, not the server. */
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    /** Manual check — the app also checks silently on launch (see SessionViewModel). */
    fun checkForUpdates() {
        if (_updateCheck.value.status == UpdateCheck.Status.Checking) return
        viewModelScope.launch {
            _updateCheck.value = UpdateCheck(UpdateCheck.Status.Checking)
            val info = updateManager.checkForUpdate(BuildConfig.VERSION_CODE)
            _updateCheck.value = if (info == null) {
                UpdateCheck(UpdateCheck.Status.UpToDate)
            } else {
                UpdateCheck(UpdateCheck.Status.Available, info)
            }
        }
    }

    fun installUpdate(info: UpdateInfo) = updateManager.downloadAndInstall(info.apkUrl)

    fun resetUpdateCheck() {
        _updateCheck.value = UpdateCheck()
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.homeData(),
            repository.syncedHomeData(),
            repository.selfUser(),
            profileStatus,
            syncRepository.lastSyncAt,
        ) { local, synced, self, _, syncedAt ->
            from(synced ?: local).copy(
                selfName = self?.name?.ifEmpty { "You" } ?: "You",
                selfUpiId = self?.upiId.orEmpty(),
                lastSyncAt = syncedAt,
            )
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
