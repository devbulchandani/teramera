package com.example.teramera.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.teramera.core.auth.SessionFacts
import com.example.teramera.core.auth.SessionState
import com.example.teramera.core.auth.SignedOutReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

data class AuthTokens(val accessToken: String, val refreshToken: String, val userId: String)

data class SpendStats(val totalSpentMinor: Long, val expenseCount: Int)

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) : TokenAccess {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val avatarKey = stringPreferencesKey("avatar_url")
    private val totalSpentKey = longPreferencesKey("total_spent_minor")
    private val expenseCountKey = intPreferencesKey("expense_count")
    private val cacheEpochKey = intPreferencesKey("cache_epoch")
    private val signedOutReasonKey = stringPreferencesKey("signed_out_reason")

    // Interceptors run on OkHttp threads and cannot suspend. A small in-memory
    // mirror keeps them off DataStore (and off runBlocking) on the hot path.
    @Volatile private var cachedTokens: AuthTokens? = null
    @Volatile private var cachePrimed = false
    private val refreshDelayed = MutableStateFlow(false)

    /** Single reader for persisted tokens; also keeps the interceptor cache warm. */
    private val cachedPrefs: Flow<Preferences> = context.tokenDataStore.data.onEach { prefs ->
        cachedTokens = tokensOf(prefs)
        cachePrimed = true
    }

    val tokens: Flow<AuthTokens?> = cachedPrefs.map(::tokensOf)

    override val session: Flow<SessionState> =
        combine(cachedPrefs, refreshDelayed) { prefs, delayed ->
            SessionFacts(
                restoring = false,
                hasTokens = tokensOf(prefs) != null,
                userId = prefs[userIdKey] ?: "",
                signedOutReason = prefs[signedOutReasonKey]?.toSignedOutReason(),
                refreshDelayed = delayed,
            ).toState()
        }.onStart { emit(SessionState.Restoring) }

    /** Profile picture URL from the Google account, when the provider returns one. */
    val avatarUrl: Flow<String?> = context.tokenDataStore.data.map { it[avatarKey] }

    /** Lifetime spend summary from `/me`, used on the home hero. */
    val stats: Flow<SpendStats?> = context.tokenDataStore.data.map { prefs ->
        val total = prefs[totalSpentKey] ?: return@map null
        SpendStats(total, prefs[expenseCountKey] ?: 0)
    }

    suspend fun saveStats(totalSpentMinor: Long, expenseCount: Int) {
        context.tokenDataStore.edit { prefs ->
            prefs[totalSpentKey] = totalSpentMinor
            prefs[expenseCountKey] = expenseCount
        }
    }

    /** Local cache schema marker — bumping it wipes stale synced rows once. */
    suspend fun cacheEpoch(): Int = context.tokenDataStore.data.first()[cacheEpochKey] ?: 0

    suspend fun setCacheEpoch(epoch: Int) {
        context.tokenDataStore.edit { it[cacheEpochKey] = epoch }
    }

    /**
     * [clearAvatar] is only true on a fresh sign-in: a token refresh must not
     * erase the profile picture that is already stored.
     */
    suspend fun save(
        tokens: AuthTokens,
        avatarUrl: String? = null,
        clearAvatar: Boolean = false,
    ) {
        context.tokenDataStore.edit { prefs ->
            prefs[accessKey] = tokens.accessToken
            prefs[refreshKey] = tokens.refreshToken
            prefs[userIdKey] = tokens.userId
            when {
                !avatarUrl.isNullOrBlank() -> prefs[avatarKey] = avatarUrl
                clearAvatar -> prefs.remove(avatarKey)
            }
            // Stats belong to the account, never leak across a sign-in switch.
            if (clearAvatar) {
                prefs.remove(totalSpentKey)
                prefs.remove(expenseCountKey)
            }
            prefs.remove(signedOutReasonKey)
        }
        // Publish immediately so the next request cannot race the DataStore
        // collector and go out with the previous (now rotated) access token.
        cachedTokens = tokens
        cachePrimed = true
        refreshDelayed.value = false
    }

    override suspend fun saveTokens(tokens: AuthTokens) = save(tokens)

    /** Explicit sign-out: remember why so the next launch can explain it. */
    override suspend fun clear() = clearWithReason(SignedOutReason.Manual)

    /** The server rejected the refresh token: remember the reason across restarts. */
    override suspend fun reject() = clearWithReason(SignedOutReason.Expired)

    private suspend fun clearWithReason(reason: SignedOutReason) {
        context.tokenDataStore.edit { prefs ->
            prefs.clear()
            prefs[signedOutReasonKey] = reason.name
        }
        cachedTokens = null
        cachePrimed = true
        refreshDelayed.value = false
    }

    override fun markRefreshDelayed() {
        refreshDelayed.value = true
    }

    override fun clearRefreshDelayed() {
        refreshDelayed.value = false
    }

    /** Non-suspending snapshot for OkHttp interceptors. */
    fun peek(): AuthTokens? = cachedTokens

    /** One-shot read for interceptors. */
    override suspend fun current(): AuthTokens? {
        cachedTokens?.let { return it }
        if (cachePrimed) return null
        return tokens.first()
    }

    private fun tokensOf(prefs: Preferences): AuthTokens? {
        val access = prefs[accessKey] ?: return null
        val refresh = prefs[refreshKey] ?: return null
        return AuthTokens(access, refresh, prefs[userIdKey] ?: "")
    }
}

private fun String.toSignedOutReason(): SignedOutReason? =
    runCatching { SignedOutReason.valueOf(this) }.getOrNull()
