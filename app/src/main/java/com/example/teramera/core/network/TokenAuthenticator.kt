package com.example.teramera.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On 401, rotates the refresh token against the backend and retries once.
 * Synchronized so concurrent 401s share one rotation.
 *
 * The refresh call itself is made through [AuthApi], which is built on a
 * separate OkHttp client with no authenticator. Wiring refresh onto the same
 * client recursed forever whenever the refresh token was rejected.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenAccess,
    private val authApiProvider: dagger.Lazy<AuthApi>,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never try to refresh an auth call: a 401 from /auth/refresh means the
        // refresh token itself is dead, not that an access token expired.
        if (response.request.url.encodedPath.startsWith("/auth/")) return null
        if (responseCount(response) >= 2) return null // already retried once

        synchronized(lock) {
            val tokens = runBlocking { tokenStore.current() } ?: return null
            // another thread may have refreshed while we waited on the lock
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (requestToken != null && requestToken != tokens.accessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokens.accessToken}")
                    .build()
            }

            val outcome = runBlocking {
                runCatching { authApiProvider.get().refresh(AuthRefreshRequestDto(tokens.refreshToken)) }
            }
            val refreshed = outcome.getOrNull()
            if (refreshed == null) {
                // Only an explicit rejection means the session is dead. A timeout,
                // DNS failure or 5xx must not sign the user out — they may be offline.
                val error = outcome.exceptionOrNull()
                if (error is HttpException && (error.code() == 401 || error.code() == 400)) {
                    // Definitive rejection: persist Expired so the UI explains it.
                    runBlocking { tokenStore.reject() }
                } else {
                    // Transient: keep the session and retry on the next request.
                    tokenStore.markRefreshDelayed()
                }
                return null
            }

            val saved = runBlocking {
                runCatching {
                    tokenStore.saveTokens(
                        AuthTokens(refreshed.accessToken, refreshed.refreshToken, refreshed.userId),
                    )
                }.isSuccess
            }
            // Without a durable save the retry would use a token the next request
            // cannot see. Fail closed; the server grace window protects the session.
            if (!saved) {
                tokenStore.markRefreshDelayed()
                return null
            }
            tokenStore.clearRefreshDelayed()

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
