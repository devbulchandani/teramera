package com.example.teramera.ui.auth

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.teramera.core.network.userMessage
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
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

    val cream = MaterialTheme.colorScheme.inverseOnSurface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Baanto(
                state = BaantoState.Idle,
                size = 112.dp,
                animate = true,
            )
            androidx.compose.foundation.layout.Row(modifier = Modifier.padding(top = 10.dp)) {
                Text("tere", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text("mera", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                "Money between friends, split fairly and settled simply.",
                style = MaterialTheme.typography.bodyLarge,
                color = cream.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 30.dp)
                    .fillMaxWidth(),
            )

            val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val addGoogleAccount: () -> Unit = {
            val addAccount = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
            runCatching { context.startActivity(addAccount) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
            Unit
        }

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
                        val google = when {
                            credential is GoogleIdTokenCredential -> credential
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                                GoogleIdTokenCredential.createFrom(credential.data)
                            else -> throw IllegalStateException("Unexpected credential type: ${credential.type}")
                        }
                        viewModel.googleLogin(
                            idToken = google.idToken,
                            avatarUrl = google.profilePictureUri?.toString(),
                            onLoggedIn = onLoggedIn,
                        )
                    } catch (_: GetCredentialCancellationException) {
                        // account picker dismissed — nothing to do
                    } catch (e: GetCredentialException) {
                        when {
                            e.type.endsWith("NO_CREDENTIAL") -> viewModel.googleError(
                                "No Google account on this device. Add one, then try again.",
                                offerAddAccount = true,
                            )
                            e.type.contains("NO_PROVIDER") || e.type.contains("PROVIDER_CONFIGURATION") ->
                                viewModel.googleError(
                                    "Google Play services is missing or out of date. Update it, then try again.",
                                    offerAddAccount = true,
                                )
                            else -> viewModel.googleError("Google sign-in failed — try again.")
                        }
                    } catch (e: Exception) {
                        viewModel.googleError(e.userMessage("Google sign-in failed"))
                    }
                }
            },
            enabled = !state.loading,
            shape = CircleShape,
            modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
        ) {
            Text(if (state.loading) "Signing in…" else "Continue with Google", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(8.dp))

        ErrorBanner(state.error, state.offerAddAccount, addGoogleAccount)
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String?,
    offerAddAccount: Boolean,
    onAddAccount: () -> Unit,
) {
    message?.let {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (offerAddAccount) {
                    TextButton(onClick = onAddAccount, contentPadding = PaddingValues(0.dp)) {
                        Text("Add Google account", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
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