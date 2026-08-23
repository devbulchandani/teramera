package com.example.teramera.ui.auth

import com.example.teramera.BuildConfig
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.core.auth.AuthRepository

data class LoginUiState(
    val step: LoginStep = LoginStep.Phone,
    val phone: String = "",
    val code: String = "",
    val requestId: String? = null,
    val devCode: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

enum class LoginStep { Phone, Verify }

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // tere | mera wordmark
        androidx.compose.foundation.layout.Row {
            Text("tere", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.secondary)
            Text("mera", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "Money between friends, split fairly and settled simply.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        // Google first — the primary way in
        GoogleSignInButton(
            enabled = !state.loading,
            webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            onIdToken = { idToken -> viewModel.googleLogin(idToken, onLoggedIn) },
            onError = viewModel::googleError,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "or use your phone number",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when (state.step) {
            LoginStep.Phone -> {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("+91 98765 43210") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Continue with phone", enabled = state.phone.length >= 8 && !state.loading) {
                    viewModel.requestOtp()
                }
            }
            LoginStep.Verify -> {
                Text(
                    "Enter the 6-digit code sent to ${state.phone}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.devCode != null) {
                    Text(
                        "Dev code: ${state.devCode}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("Code") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Verify & continue", enabled = state.code.length == 6 && !state.loading) {
                    viewModel.verifyOtp(onLoggedIn)
                }
            }
        }



        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    webClientId: String,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.OutlinedButton(
        onClick = {
            if (webClientId.isBlank()) {
                onError("Google sign-in isn't configured yet — add google.webClientId to local.properties")
                return@OutlinedButton
            }
            scope.launch {
                try {
                    val manager = androidx.credentials.CredentialManager.create(context)
                    val option = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                    val result = manager.getCredential(
                        context,
                        androidx.credentials.GetCredentialRequest.Builder()
                            .addCredentialOption(option)
                            .build(),
                    )
                    val idToken = (result.credential as? com.google.android.libraries.identity.googleid.GoogleIdTokenCredential)
                        ?.idToken
                        ?: throw IllegalStateException("Unexpected credential type")
                    onIdToken(idToken)
                } catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) {
                    // user closed the account picker — nothing to do
                } catch (e: Exception) {
                    onError(e.message ?: "Google sign-in failed")
                }
            }
        },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text("Continue with Google", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
