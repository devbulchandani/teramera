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

    val tokens: Flow<AuthTokens?> = context.tokenDataStore.data.map { prefs ->
        val access = prefs[accessKey] ?: return@map null
        val refresh = prefs[refreshKey] ?: return@map null
        AuthTokens(access, refresh, prefs[userIdKey] ?: "")
    }

    suspend fun save(tokens: AuthTokens) {
        context.tokenDataStore.edit { prefs ->
            prefs[accessKey] = tokens.accessToken
            prefs[refreshKey] = tokens.refreshToken
            prefs[userIdKey] = tokens.userId
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }

    /** One-shot read for interceptors. */
    suspend fun current(): AuthTokens? = tokens.first()
}
