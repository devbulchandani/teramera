package com.example.teramera.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On 401, rotates the refresh token against the backend and retries once.
 * Synchronized so concurrent 401s share one rotation.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApiProvider: dagger.Lazy<AuthApi>,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
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

            val refreshed = runBlocking {
                runCatching { authApiProvider.get().refresh(AuthRefreshRequestDto(tokens.refreshToken)) }
            }.getOrNull() ?: return null.also { runBlocking { tokenStore.clear() } }

            runBlocking { tokenStore.save(AuthTokens(refreshed.accessToken, refreshed.refreshToken, refreshed.userId)) }

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
