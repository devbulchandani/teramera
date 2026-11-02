package com.example.teramera.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {

    private class FakeTokenAccess(initial: AuthTokens?) : TokenAccess {
        @Volatile var tokens: AuthTokens? = initial
        val clears = AtomicInteger()
        val saves = AtomicInteger()
        private val expired = MutableStateFlow(false)
        override val sessionExpired: StateFlow<Boolean> = expired

        override suspend fun current(): AuthTokens? = tokens

        override suspend fun saveTokens(tokens: AuthTokens) {
            this.tokens = tokens
            saves.incrementAndGet()
        }

        override suspend fun clear() {
            tokens = null
            clears.incrementAndGet()
        }

        override fun markSessionExpired() {
            expired.value = true
        }

        override fun clearSessionExpired() {
            expired.value = false
        }
    }

    /** Minimal in-process HTTP server so 401s reach OkHttp's authenticator. */
    private class TestServer(private val handler: (path: String, auth: String?) -> Pair<Int, String>) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val baseUrl = "http://127.0.0.1:${server.address.port}/"

        init {
            server.createContext("/") { exchange ->
                requests.incrementAndGet()
                val (code, body) = handler(
                    exchange.requestURI.path,
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.executor = Executors.newCachedThreadPool()
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private fun lazyOf(provider: () -> AuthApi): dagger.Lazy<AuthApi> =
        object : dagger.Lazy<AuthApi> {
            override fun get(): AuthApi = provider()
        }

    private fun authApi(client: OkHttpClient, baseUrl: String): AuthApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(
            MoshiConverterFactory.create(
                Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
            ),
        )
        .build()
        .create(AuthApi::class.java)

    @Test
    fun `rejected refresh clears the session once and never recurses`() {
        val store = FakeTokenAccess(AuthTokens("old-access", "old-refresh", "user-a"))
        val server = TestServer { _, _ -> 401 to "{\"message\":\"unauthorized\"}" }
        try {
            val api = authApi(OkHttpClient(), server.baseUrl)
            val authenticator = TokenAuthenticator(store, lazyOf { api })
            val main = OkHttpClient.Builder().authenticator(authenticator).build()

            main.newCall(Request.Builder().url(server.baseUrl + "groups").build())
                .execute()
                .use { assertEquals(401, it.code) }

            // original request + exactly one refresh attempt; the old bug looped forever
            assertEquals(2, server.requests.get())
            assertEquals(1, store.clears.get())
            assertTrue(store.sessionExpired.value)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `auth path guard stops recursion even if refresh shares the client`() {
        val store = FakeTokenAccess(AuthTokens("old-access", "old-refresh", "user-a"))
        val server = TestServer { _, _ -> 401 to "{\"message\":\"unauthorized\"}" }
        try {
            var api: AuthApi? = null
            val authenticator = TokenAuthenticator(store, lazyOf { api!! })
            val main = OkHttpClient.Builder().authenticator(authenticator).build()
            // Deliberately wire refresh onto the same client to prove the /auth guard.
            api = authApi(main, server.baseUrl)

            main.newCall(Request.Builder().url(server.baseUrl + "groups").build())
                .execute()
                .use { assertEquals(401, it.code) }

            assertEquals(2, server.requests.get())
            assertEquals(1, store.clears.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `successful refresh persists tokens and retries with the new access token`() {
        val store = FakeTokenAccess(AuthTokens("old-access", "old-refresh", "user-a"))
        val server = TestServer { path, auth ->
            when {
                path == "/auth/refresh" -> 200 to
                    "{\"accessToken\":\"new-access\",\"refreshToken\":\"new-refresh\",\"userId\":\"user-a\"}"
                auth == "Bearer new-access" -> 200 to "{}"
                else -> 401 to "{}"
            }
        }
        try {
            val api = authApi(OkHttpClient(), server.baseUrl)
            val authenticator = TokenAuthenticator(store, lazyOf { api })
            val main = OkHttpClient.Builder().authenticator(authenticator).build()

            main.newCall(
                Request.Builder()
                    .url(server.baseUrl + "groups")
                    .header("Authorization", "Bearer old-access")
                    .build(),
            ).execute().use { assertEquals(200, it.code) }

            assertEquals("new-access", store.tokens?.accessToken)
            assertEquals("new-refresh", store.tokens?.refreshToken)
            assertEquals(1, store.saves.get())
            assertEquals(0, store.clears.get())
            assertFalse(store.sessionExpired.value)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `transient refresh failure keeps the session`() {
        val store = FakeTokenAccess(AuthTokens("old-access", "old-refresh", "user-a"))
        val server = TestServer { path, _ ->
            if (path == "/auth/refresh") 503 to "{}" else 401 to "{}"
        }
        try {
            val api = authApi(OkHttpClient(), server.baseUrl)
            val authenticator = TokenAuthenticator(store, lazyOf { api })
            val main = OkHttpClient.Builder().authenticator(authenticator).build()

            main.newCall(
                Request.Builder()
                    .url(server.baseUrl + "groups")
                    .header("Authorization", "Bearer old-access")
                    .build(),
            ).execute().use { assertEquals(401, it.code) }

            assertEquals(0, store.clears.get())
            assertEquals("old-refresh", store.tokens?.refreshToken)
            assertFalse(store.sessionExpired.value)
        } finally {
            server.stop()
        }
    }
}
