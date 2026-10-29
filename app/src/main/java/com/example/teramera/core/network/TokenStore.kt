package com.example.teramera.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val avatarKey = stringPreferencesKey("avatar_url")

    val tokens: Flow<AuthTokens?> = context.tokenDataStore.data.map { prefs ->
        val access = prefs[accessKey] ?: return@map null
        val refresh = prefs[refreshKey] ?: return@map null
        AuthTokens(access, refresh, prefs[userIdKey] ?: "")
    }

    /** Profile picture URL from the Google account, when the provider returns one. */
    val avatarUrl: Flow<String?> = context.tokenDataStore.data.map { it[avatarKey] }

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
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }

    /** One-shot read for interceptors. */
    suspend fun current(): AuthTokens? = tokens.first()
}
