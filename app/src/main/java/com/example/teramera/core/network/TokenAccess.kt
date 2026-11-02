package com.example.teramera.core.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal session surface used by the OkHttp authenticator. Kept as an
 * interface so the refresh state machine can be unit tested without Android.
 */
interface TokenAccess {
    /** True once the server has definitively rejected the refresh token. */
    val sessionExpired: StateFlow<Boolean>

    suspend fun current(): AuthTokens?

    suspend fun saveTokens(tokens: AuthTokens)

    suspend fun clear()

    fun markSessionExpired()

    fun clearSessionExpired()
}
