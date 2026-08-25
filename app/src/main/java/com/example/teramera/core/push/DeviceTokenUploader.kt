package com.example.teramera.core.push

import com.example.teramera.core.network.TokenStore
import com.example.teramera.data.sync.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Uploads the FCM device token to the backend once signed in. */
@Singleton
class DeviceTokenUploader @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
) {
    /** Token seen before login is retried after the next successful sync. */
    @Volatile
    var pendingToken: String? = null

    suspend fun upload(token: String): Boolean {
        if (tokenStore.current() == null) {
            pendingToken = token
            return false
        }
        return if (syncRepository.registerDeviceToken(token)) {
            pendingToken = null
            true
        } else {
            pendingToken = token
            false
        }
    }

    suspend fun uploadPending() {
        pendingToken?.let { upload(it) }
    }
}
