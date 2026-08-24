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

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

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

    /**
     * Maps raw failures to something a human can act on.
     * - backend messages (audience mismatch etc.) pass through
     * - Credential-Manager codes become setup hints
     */
    private fun friendly(failure: Throwable): String {
        val raw = failure.message ?: ""
        return when {
            raw.contains("No teramera", true) -> raw
            raw.contains("Invalid Google ID token", true) ->
                "That Google account was rejected by the server. " +
                    "If you're the app owner, make sure the account is an allowed test user " +
                    "or the OAuth consent screen is published."
            raw.contains("HTTP 5", true) -> "Server hiccup — try again."
            else -> raw.ifBlank { "Sign-in failed — try again." }
        }
    }
}
