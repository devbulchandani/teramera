package com.example.teramera.core.auth

import com.example.teramera.core.network.AuthTokens
import com.example.teramera.core.network.GoogleLoginRequestDto
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

    suspend fun googleLogin(idToken: String): Result<Unit> =
        try {
            val tokens = authApi.googleLogin(GoogleLoginRequestDto(idToken))
            tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId))
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            // surface the backend's human-readable reason (audience mismatch etc.)
            val bodyMessage = e.response()?.errorBody()?.string()?.let { body ->
                runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()
            }
            Result.failure(Exception(bodyMessage ?: "Sign-in failed"))
        }

    suspend fun logout() {
        tokenStore.clear()
        dao.clearSyncedBalances()
    }
}