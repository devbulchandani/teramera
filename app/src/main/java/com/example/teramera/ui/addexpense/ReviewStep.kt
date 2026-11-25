package com.example.teramera.ui.addexpense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.teramera.core.time.TimeFormat
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.home.formatInr

/**
 * Step 3 — confirmation. Same section rhythm as step 2 so the transition reads
 * as "one step further", not a different screen.
 */
@Composable
internal fun ReviewStep(
    state: AddExpenseUiState,
    viewModel: AddExpenseViewModel,
    onSaved: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val shares = remember(state) {
        if (state.serverMode) viewModel.serverShares(state) else viewModel.previewShares(state)
    }
    val shareList = shares.orEmpty()
    val total = state.draft.amountMinor
    val groupName = state.groups.firstOrNull { it.id == state.draft.groupId }?.name
    val debtText = debtLabel(state, shareList.size)
    val payerText = when {
        state.draft.payers.isEmpty() -> "You"
        state.draft.payers.size == 1 -> nameOf(state, state.draft.payers.keys.first())
        else -> state.draft.payers.entries.joinToString(" + ") { (uid, minor) ->
            "${nameOf(state, uid)} ₹${formatInr(minor)}"
        }
    }
    val valid = remember(state) {
        if (state.serverMode) serverSharesValid(state, viewModel) else sharesValid(state, viewModel)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
        ) {
            item {
                FormSection("Summary") {
                    SheetCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            Baanto(state = BaantoState.Pleased, size = 56.dp, withCoin = false)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        debtText != null ->
                                            "$debtText${groupName?.let { " · $it" } ?: ""}"
                                        shareList.size == 1 ->
                                            "Personal expense${groupName?.let { " · $it" } ?: ""}"
                                        else ->
                                            "Splitting ${groupName ?: "no group"} · " +
                                                splitLabel(state.draft.splitType).lowercase()
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AmountSummary(amountMinor = total)
                                Text(
                                    text = "Paid by $payerText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item {
                FormSection("Who owes what") {
                    SheetCard {
                        shareList.forEachIndexed { index, (userId, minor) ->
                            ShareRow(
                                state = state,
                                userId = userId,
                                amountMinor = minor,
                                isPayer = userId in state.payerIds,
                            )
                            if (index != shareList.lastIndex) Hairline()
                        }
                    }
                }
            }

            item {
                FormSection("When") {
                    SheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                        ) {
                            Text(
                                text = TimeFormat.dayTime(now),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    )
                }
            }
        }

        ActionBar(
            label = if (shareList.size == 1) {
                "Save ₹${formatInr(total)}"
            } else {
                "Split ₹${formatInr(total)}"
            },
            enabled = state.draft.title.isNotBlank() && total > 0 && valid,
            loading = state.saving,
            onClick = { viewModel.save(onDone = onSaved) },
        )
    }
}

@Composable
private fun AmountSummary(amountMinor: Long) {
    val primary = MaterialTheme.colorScheme.primary
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        .coerceAtLeast(1f)
    val maxSp = (44f / fontScale).coerceAtLeast(22f).sp
    val text = remember(amountMinor, primary) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontSize = 0.62.em)) { append("₹") }
            append(formatInr(amountMinor))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 22.sp,
            maxFontSize = maxSp,
            stepSize = 2.sp,
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
