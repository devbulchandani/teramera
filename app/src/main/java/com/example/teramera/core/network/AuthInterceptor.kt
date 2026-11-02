package com.example.teramera.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Attaches the stored access token to every request that isn't an auth call. */
@Singleton
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null || request.url.encodedPath.startsWith("/auth/")) {
            return chain.proceed(request)
        }
        // Hot path is the in-memory mirror. Only the very first request of a
        // process can fall through to a blocking DataStore read.
        val tokens = tokenStore.peek() ?: runBlocking { tokenStore.current() }
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .build()
        )
    }
}
