package com.example.teramera.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.auth.AuthRepository
import com.example.teramera.core.network.AuthApi
import com.example.teramera.core.network.AuthTokens
import com.example.teramera.core.network.EmailCheckRequestDto
import com.example.teramera.core.network.EmailLoginRequestDto
import com.example.teramera.core.network.EmailOtpRequestDto
import com.example.teramera.core.network.EmailRegisterRequestDto
import com.example.teramera.core.network.OtpVerifyRequestDto
import com.example.teramera.core.network.TokenStore
import com.example.teramera.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginStep { ENTRY, LOGIN_PASSWORD, CREATE_PASSWORD, CODE }

data class LoginUiState(
    val step: LoginStep = LoginStep.ENTRY,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val requestId: String? = null,
    val devCode: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value.trim(), error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    /** Email submitted → backend tells us whether it's a sign-in or a fresh account. */
    fun submitEmail() {
        val email = _state.value.email.trim().lowercase()
        if (!email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            _state.update { it.copy(error = "Enter a valid email address") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val check = authApi.emailCheck(EmailCheckRequestDto(email))
                _state.update {
                    it.copy(
                        loading = false,
                        step = if (check.exists && check.verified) LoginStep.LOGIN_PASSWORD else LoginStep.CREATE_PASSWORD,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** Existing verified account → password sign-in. Unverified falls back to email code. */
    fun submitPasswordLogin(onLoggedIn: () -> Unit) {
        val s = _state.value
        if (s.password.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tokens = authApi.emailLogin(EmailLoginRequestDto(s.email, s.password))
                tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId))
                onLoggedIn()
            } catch (e: retrofit2.HttpException) {
                val message = parseErrorBody(e)
                if (message?.contains("not verified", true) == true) useCodeInstead()
                else _state.update { it.copy(loading = false, error = message ?: "Incorrect email or password") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** New account → register; the server emails a 6-digit verification code. */
    fun submitCreatePassword() {
        val s = _state.value
        if (s.password.length < 8) {
            _state.update { it.copy(error = "Password must be at least 8 characters") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = authApi.emailRegister(EmailRegisterRequestDto(s.email, s.password))
                lastRequestId.value = resp.requestId
                _state.update {
                    it.copy(
                        loading = false,
                        step = LoginStep.CODE,
                        requestId = resp.requestId,
                        devCode = if (BuildConfig.DEBUG) resp.devCode else null,
                        code = "",
                    )
                }
            } catch (e: retrofit2.HttpException) {
                _state.update { it.copy(loading = false, error = parseErrorBody(e) ?: "Couldn't create account") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** Passwordless sign-in / re-verification via emailed code. */
    fun useCodeInstead() {
        viewModelScope.launch {
            val email = _state.value.email.trim().lowercase()
            try {
                val resp = authApi.emailOtp(EmailOtpRequestDto(email))
                lastRequestId.value = resp.requestId
                _state.update {
                    it.copy(step = LoginStep.CODE, requestId = resp.requestId, devCode = if (BuildConfig.DEBUG) resp.devCode else null, code = "")
                }
            } catch (e: retrofit2.HttpException) {
                _state.update { it.copy(error = parseErrorBody(e) ?: "Couldn't send a code") }
            } catch (e: Exception) {
                _state.update { it.copy(error = friendly(e)) }
            }
        }
    }

    fun submitCode(onLoggedIn: () -> Unit) {
        val s = _state.value
        val requestId = s.requestId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tokens = authApi.emailVerify(OtpVerifyRequestDto(requestId, s.code))
                tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken, tokens.userId))
                onLoggedIn()
            } catch (e: retrofit2.HttpException) {
                _state.update { it.copy(loading = false, error = parseErrorBody(e) ?: "Incorrect code") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun googleLogin(idToken: String, onLoggedIn: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.googleLogin(idToken).fold(
                onSuccess = { onLoggedIn() },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = friendly(failure)) }
                },
            )
        }
    }

    fun googleError(message: String) =
        _state.update { it.copy(loading = false, error = message) }

    fun back() = _state.update {
        when (it.step) {
            LoginStep.CODE -> it.copy(step = LoginStep.CREATE_PASSWORD, code = "", requestId = null, devCode = null)
            LoginStep.LOGIN_PASSWORD, LoginStep.CREATE_PASSWORD -> it.copy(step = LoginStep.ENTRY, password = "")
            else -> it
        }
    }

    private val lastRequestId = MutableStateFlow<String?>(null)

    private fun parseErrorBody(e: retrofit2.HttpException): String? =
        e.response()?.errorBody()?.string()?.let { body ->
            runCatching { org.json.JSONObject(body).optString("message").ifBlank { null } }.getOrNull()
        }

    private fun friendly(failure: Throwable): String {
        val raw = failure.message ?: ""
        return when {
            raw.contains("Invalid Google ID token", true) ->
                "That Google account was rejected by the server. " +
                    "If you're the app owner, make sure the account is an allowed test user " +
                    "or the OAuth consent screen is published."
            raw.contains("HTTP 5", true) -> "Server hiccup — try again."
            else -> raw.ifBlank { "Something went wrong — try again." }
        }
    }
}
