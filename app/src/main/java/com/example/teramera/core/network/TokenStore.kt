package com.example.teramera.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

data class AuthTokens(val accessToken: String, val refreshToken: String, val userId: String)

data class SpendStats(val totalSpentMinor: Long, val expenseCount: Int)

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val avatarKey = stringPreferencesKey("avatar_url")
    private val totalSpentKey = longPreferencesKey("total_spent_minor")
    private val expenseCountKey = intPreferencesKey("expense_count")
    private val cacheEpochKey = intPreferencesKey("cache_epoch")

    val tokens: Flow<AuthTokens?> = context.tokenDataStore.data.map { prefs ->
        val access = prefs[accessKey] ?: return@map null
        val refresh = prefs[refreshKey] ?: return@map null
        AuthTokens(access, refresh, prefs[userIdKey] ?: "")
    }

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
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }

    /** One-shot read for interceptors. */
    suspend fun current(): AuthTokens? = tokens.first()
}
