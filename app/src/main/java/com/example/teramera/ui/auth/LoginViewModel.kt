package com.example.teramera.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, error = null) }

    fun onCodeChange(value: String) = _state.update {
        it.copy(code = value.filter(Char::isDigit).take(6), error = null)
    }

    fun requestOtp() {
        val phone = normalize(_state.value.phone)
        if (phone == null) {
            _state.update { it.copy(error = "Use international format, e.g. +919876543210") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, phone = phone) }
            authRepository.requestOtp(phone).fold(
                onSuccess = { challenge ->
                    _state.update {
                        it.copy(step = LoginStep.Verify, requestId = challenge.requestId, devCode = challenge.devCode, loading = false)
                    }
                },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = friendly(failure)) }
                },
            )
        }
    }

    fun verifyOtp(onLoggedIn: () -> Unit) {
        val state = _state.value
        val requestId = state.requestId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            authRepository.verifyOtp(requestId, state.code).fold(
                onSuccess = { onLoggedIn() },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = friendly(failure)) }
                },
            )
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

    private fun normalize(raw: String): String? {
        var cleaned = raw.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (!cleaned.startsWith("+")) return null
        cleaned = "+" + cleaned.drop(1).filter(Char::isDigit)
        return if (cleaned.matches(Regex("\\+\\d{8,15}"))) cleaned else null
    }

    private fun friendly(failure: Throwable): String =
        failure.message?.takeIf { it.isNotBlank() && !it.contains("HTTP", ignoreCase = true) }
            ?: "Couldn't reach the server. Check the backend is running."
}
