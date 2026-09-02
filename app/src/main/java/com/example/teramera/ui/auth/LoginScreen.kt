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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.BuildConfig
import com.example.teramera.ui.theme.TerameraTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Auth v4 — Google Sign-In is the only way in. v0.3.4 dropped the email
 * password / code flow because Google gives us a verified email + avatar.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("tere", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.secondary)
            Text("mera", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "Money between friends, split fairly and settled simply.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 10.dp, bottom = 36.dp)
                .fillMaxWidth(),
        )

        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Button(
            onClick = {
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                    viewModel.googleError("Google sign-in isn't configured — add google.webClientId to local.properties")
                    return@Button
                }
                scope.launch {
                    try {
                        val manager = CredentialManager.create(context)
                        val option = GetGoogleIdOption.Builder()
                            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                            .setFilterByAuthorizedAccounts(false)
                            .build()
                        val result = manager.getCredential(
                            context,
                            GetCredentialRequest.Builder().addCredentialOption(option).build(),
                        )
                        // CredentialManager usually wraps the token in a CustomCredential —
                        // unwrap via createFrom() instead of a direct cast.
                        val credential = result.credential
                        val idToken = when {
                            credential is GoogleIdTokenCredential -> credential.idToken
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                                GoogleIdTokenCredential.createFrom(credential.data).idToken
                            else -> throw IllegalStateException("Unexpected credential type: ${credential.type}")
                        }
                        viewModel.googleLogin(idToken, onLoggedIn)
                    } catch (_: GetCredentialCancellationException) {
                        // account picker dismissed — nothing to do
                    } catch (e: GetCredentialException) {
                        viewModel.googleError(
                            if (e.type.endsWith("NO_CREDENTIAL"))
                                "No Google account available on this device."
                            else "Google sign-in failed — try again."
                        )
                    } catch (e: Exception) {
                        viewModel.googleError(e.message ?: "Google sign-in failed")
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

        Spacer(Modifier.height(8.dp))

        ErrorBanner(state.error)
    }
}

@Composable
private fun ErrorBanner(message: String?) {
    message?.let {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun LoginScreenPreview() {
    TerameraTheme {
        LoginScreen(onLoggedIn = {})
    }
}