package com.example.teramera.ui.addexpense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Step 1 — the amount. The keypad shrinks to the space actually available
 * (48–64 dp keys) and the whole block scrolls as a safety net, so Continue is
 * reachable on a 320×568 phone, in landscape, and at a large font scale.
 */
@Composable
internal fun AmountStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val keyHeight = ((maxHeight - 40.dp) * 0.25f).coerceIn(48.dp, 64.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Keypad(
                    onKey = viewModel::onKey,
                    onBackspace = viewModel::onBackspace,
                    keyHeight = keyHeight,
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        ActionBar(
            label = "Continue",
            enabled = state.draft.amountMinor > 0,
            onClick = viewModel::nextStep,
        )
    }
}
