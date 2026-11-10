package com.example.teramera.core.network

import com.example.teramera.core.auth.SessionState
import kotlinx.coroutines.flow.Flow

/**
 * Minimal session surface used by the OkHttp authenticator. Kept as an
 * interface so the refresh state machine can be unit tested without Android.
 */
interface TokenAccess {
    /** The explicit session state machine, derived from tokens + refresh health. */
    val session: Flow<SessionState>

    suspend fun current(): AuthTokens?

    suspend fun saveTokens(tokens: AuthTokens)

    /** The user signed out explicitly. */
    suspend fun clear()

    /** The server rejected the refresh token: sign out as [SessionState.SignedOut] Expired. */
    suspend fun reject()

    /** A refresh failed transiently; the session stays valid and may retry. */
    fun markRefreshDelayed()

    fun clearRefreshDelayed()
}
