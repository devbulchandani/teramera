package com.example.teramera.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.example.teramera.BuildConfig
import com.example.teramera.ui.theme.TerameraTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Auth v3 — Google Sign-In is the primary method; email+password (with emailed
 * verification / sign-in codes) as the private alternative.
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

        when (state.step) {
            LoginStep.ENTRY -> EntryStep(state, viewModel, onLoggedIn)
            LoginStep.LOGIN_PASSWORD -> PasswordStep(state, viewModel, isCreate = false, onLoggedIn = onLoggedIn)
            LoginStep.CREATE_PASSWORD -> PasswordStep(state, viewModel, isCreate = true, onLoggedIn)
            LoginStep.CODE -> CodeStep(state, viewModel, onLoggedIn = onLoggedIn)
        }
    }
}

// ---------- step 1: Google primary + email entry ----------

@Composable
private fun EntryStep(state: LoginUiState, viewModel: LoginViewModel, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var emailVisible by remember { mutableStateOf(false) }

    androidx.compose.material3.Button(
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
                    val idToken = (result.credential as? GoogleIdTokenCredential)?.idToken
                        ?: throw IllegalStateException("Unexpected credential type")
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
    ) {
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "or continue with email",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
    }

    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        placeholder = { Text("you@example.com") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = viewModel::submitEmail,
        enabled = !state.loading && state.email.isNotBlank(),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Continue with email", style = MaterialTheme.typography.labelLarge)
    }

    ErrorBanner(state.error)
}

// ---------- steps 2/3: password ----------

@Composable
private fun PasswordStep(
    state: LoginUiState,
    viewModel: LoginViewModel,
    isCreate: Boolean,
    onLoggedIn: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackCircle { viewModel.back() }
        Spacer(Modifier.width(12.dp))
        Text(
            if (isCreate) "Create your account" else "Sign in with email",
            style = MaterialTheme.typography.titleLarge,
        )
    }

    FieldLabel("Email")
    OutlinedTextField(
        value = state.email,
        onValueChange = {},
        enabled = false,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )

    FieldLabel(if (isCreate) "Create a password" else "Password")
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { if (isCreate) Text("min 8 characters") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = {
            if (isCreate) viewModel.submitCreatePassword() else viewModel.submitPasswordLogin(onLoggedIn)
        },
        enabled = !state.loading && state.password.length >= (if (isCreate) 8 else 1),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(56.dp),
    ) {
        Text(if (isCreate) "Create account" else "Sign in", style = MaterialTheme.typography.labelLarge)
    }

    if (!isCreate) {
        androidx.compose.material3.TextButton(
            onClick = viewModel::useCodeInstead,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Email me a sign-in code instead", style = MaterialTheme.typography.labelMedium)
        }
    }

    ErrorBanner(state.error)
}

// ---------- step 4: email code ----------

@Composable
private fun CodeStep(state: LoginUiState, viewModel: LoginViewModel, onLoggedIn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackCircle { viewModel.back() }
        Spacer(Modifier.width(12.dp))
        Text("Enter the code", style = MaterialTheme.typography.titleLarge)
    }
    Text(
        "We emailed a 6-digit code to ${state.email}. It expires in 5 minutes.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        placeholder = { Text("••••••") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            textAlign = TextAlign.Center,
            letterSpacing = 8.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    )

    Button(
        onClick = { viewModel.submitCode(onLoggedIn) },
        enabled = !state.loading && state.code.length == 6,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(56.dp),
    ) {
        Text("Verify & finish", style = MaterialTheme.typography.labelLarge)
    }

    androidx.compose.material3.TextButton(
        onClick = viewModel::useCodeInstead,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Resend code", style = MaterialTheme.typography.labelMedium)
    }

    ErrorBanner(state.error)
}

// ---------- shared bits ----------

@Composable
private fun BackCircle(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Text(
            "←",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
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
