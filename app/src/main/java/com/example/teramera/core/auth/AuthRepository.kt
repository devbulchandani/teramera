package com.example.teramera.core.auth

import com.example.teramera.core.network.AuthApi
import com.example.teramera.core.network.AuthTokens
import com.example.teramera.core.network.GoogleLoginRequestDto
import com.example.teramera.core.network.LogoutRequestDto
import com.example.teramera.core.network.TokenStore
import com.example.teramera.core.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val spendStore: com.example.teramera.core.stats.SpendStore,
    private val dao: com.example.teramera.data.local.TerameraDao,
) {

    /** The explicit session state machine; screens render this, not booleans. */
    val session: Flow<SessionState> = tokenStore.session

    suspend fun googleLogin(idToken: String, avatarUrl: String? = null): Result<Unit> =
        try {
            val tokens = authApi.googleLogin(GoogleLoginRequestDto(idToken))
            // Signing in as someone else must not surface the previous account's
            // cached balances/groups/activity while the first sync is in flight.
            dao.clearAccountData()
            spendStore.clear()
            tokenStore.save(
                AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId),
                avatarUrl = avatarUrl,
                clearAvatar = true,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(loginFailure(e))
        }

    /**
     * Every failure mode becomes a user-readable [Result.failure] — offline,
     * timeout and TLS errors used to escape and crash the sign-in coroutine.
     */
    private fun loginFailure(error: Throwable): Exception {
        val message = when (error) {
            is HttpException -> runCatching {
                error.response()?.errorBody()?.string()?.let { body ->
                    org.json.JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                }
            }.getOrNull() ?: "Sign-in failed"
            else -> error.userMessage("Sign-in failed")
        }
        return Exception(message)
    }

    suspend fun logout() {
        // best effort: revoke the refresh token server-side, then wipe local caches
        tokenStore.current()?.let { tokens ->
            runCatching { authApi.logout(LogoutRequestDto(tokens.refreshToken)) }
        }
        tokenStore.clear()
        spendStore.clear()
        dao.clearAccountData()
    }
}
