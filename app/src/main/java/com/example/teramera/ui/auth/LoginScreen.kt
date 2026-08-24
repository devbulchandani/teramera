package com.example.teramera.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.BuildConfig
import com.example.teramera.ui.theme.TerameraTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Google Sign-In is the only way in.
 * Phone OTP endpoints remain on the backend but are no longer surfaced in the UI.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 36.dp)
                .fillMaxWidth(),
        )

        Button(
            onClick = {
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                    viewModel.googleError(
                        "Google sign-in isn't configured yet — add google.webClientId to local.properties"
                    )
                    return@Button
                }
                scope.launch {
                    try {
                        val manager = CredentialManager.create(context)
                        val option = GetGoogleIdOption.Builder()
                            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                            .setFilterByAuthorizedAccounts(false) // show ALL google accounts on the device
                            .build()
                        val result = manager.getCredential(
                            context,
                            GetCredentialRequest.Builder().addCredentialOption(option).build(),
                        )
                        val idToken = (result.credential as? GoogleIdTokenCredential)?.idToken
                            ?: throw IllegalStateException("Unexpected credential type")
                        viewModel.googleLogin(idToken, onLoggedIn)
                    } catch (_: GetCredentialCancellationException) {
                        // user closed the account picker — nothing to do
                    } catch (e: Exception) {
                        viewModel.googleError(googleErrorMessage(e))
                    }
                }
            },
            enabled = !state.loading,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(if (state.loading) "Signing in…" else "Continue with Google", style = MaterialTheme.typography.labelLarge)
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun googleErrorMessage(e: Exception): String = when (e) {
    is androidx.credentials.exceptions.NoCredentialException ->
        "No Google account available on this device. Add one in Settings → Accounts."
    is androidx.credentials.exceptions.GetCredentialException ->
        if (e.type.endsWith("NO_CREDENTIAL"))
            "No Google account available on this device. Add one in Settings → Accounts."
        else
            "Google sign-in failed (${e.type.substringAfterLast('.').take(40)})." 
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed — try again."
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    TerameraTheme {
        LoginScreen(onLoggedIn = {})
    }
}
