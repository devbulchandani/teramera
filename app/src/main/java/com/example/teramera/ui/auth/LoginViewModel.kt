package com.example.teramera.ui.auth

import androidx.lifecycle.ViewModel
import com.example.teramera.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Google Sign-In is the only login method (v0.3.4). The ViewModel just
 * orchestrates the Google Credential Manager flow + surfaces friendly
 * error messages.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

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