package com.example.teramera.core.auth

import com.example.teramera.core.network.AuthTokens
import com.example.teramera.core.network.OtpRequestDto
import com.example.teramera.core.network.OtpVerifyRequestDto
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

    data class OtpChallenge(val requestId: String, val devCode: String?)

    suspend fun requestOtp(phoneE164: String): Result<OtpChallenge> = runCatching {
        val response = authApi.requestOtp(OtpRequestDto(phoneE164))
        OtpChallenge(response.requestId, response.devCode)
    }

    suspend fun verifyOtp(requestId: String, code: String): Result<Unit> = runCatching {
        val tokens = authApi.verifyOtp(OtpVerifyRequestDto(requestId, code))
        tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId))
    }

    suspend fun logout() {
        tokenStore.clear()
        dao.clearSyncedBalances()
    }
}
