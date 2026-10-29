package com.example.teramera.core.auth

import com.example.teramera.core.network.AuthTokens
import com.example.teramera.core.network.GoogleLoginRequestDto
import com.example.teramera.core.network.LogoutRequestDto
import com.example.teramera.core.network.TokenStore
import com.example.teramera.core.network.AuthApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val dao: com.example.teramera.data.local.TerameraDao,
) {

    /** null while the session state is being restored, true/false once known. */
    val isLoggedIn: Flow<Boolean?> = tokenStore.tokens.map { it != null }

    suspend fun googleLogin(idToken: String, avatarUrl: String? = null): Result<Unit> =
        try {
            val tokens = authApi.googleLogin(GoogleLoginRequestDto(idToken))
            // Signing in as someone else must not surface the previous account's
            // cached balances/groups/activity while the first sync is in flight.
            dao.clearAccountData()
            tokenStore.save(
                AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId),
                avatarUrl = avatarUrl,
                clearAvatar = true,
            )
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            // surface the backend's human-readable reason (audience mismatch etc.)
            val bodyMessage = e.response()?.errorBody()?.string()?.let { body ->
                runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()
            }
            Result.failure(Exception(bodyMessage ?: "Sign-in failed"))
        }

    suspend fun logout() {
        // best effort: revoke the refresh token server-side, then wipe local caches
        tokenStore.current()?.let { tokens ->
            runCatching { authApi.logout(LogoutRequestDto(tokens.refreshToken)) }
        }
        tokenStore.clear()
        dao.clearAccountData()
    }
}