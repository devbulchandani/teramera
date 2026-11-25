package com.example.teramera.ui.addexpense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.teramera.data.repository.SplitType
import com.example.teramera.ui.home.formatInr

/**
 * Step 2 — the decisions, in the order people make them: what it was for, who
 * fronted it, who shares it, how it splits, and what that works out to. Each
 * block gets its own label and card instead of one long undifferentiated form.
 */
@Composable
internal fun DetailsStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val shares = remember(state) {
        if (state.serverMode) viewModel.serverShares(state) else viewModel.previewShares(state)
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
                FormSection("What was it for") {
                    TitleField(
                        title = state.draft.title,
                        onTitle = viewModel::setTitle,
                    )
                }
            }

            item {
                FormSection("Who paid?") {
                    PaidByBlock(state = state, viewModel = viewModel)
                }
            }

            if (state.serverMode) {
                item {
                    FormSection(
                        title = "Split between · ${state.draft.includedServer.size} " +
                            (if (state.draft.includedServer.size == 1) "person" else "people"),
                    ) {
                        ParticipantBlock(state = state, viewModel = viewModel)
                    }
                }
            }

            item {
                FormSection("How to split?") {
                    SplitTypeSelector(
                        selected = state.draft.splitType,
                        onSelect = viewModel::setSplitType,
                    )
                }
            }

            if (state.draft.splitType != SplitType.EQUAL) {
                item {
                    FormSection("Per person") {
                        val people = if (state.serverMode) {
                            state.draft.includedServer.toList()
                        } else {
                            viewModel.participants(state)
                        }
                        SheetCard {
                            people.forEach { userId ->
                                RawValueRow(
                                    state = state,
                                    userId = userId,
                                    type = state.draft.splitType,
                                    onValue = viewModel::setRawValue,
                                )
                            }
                        }
                    }
                }
            }

            item {
                FormSection("Split preview") {
                    PreviewBlock(
                        state = state,
                        viewModel = viewModel,
                        shares = shares,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }

        ActionBar(
            label = "Review split",
            enabled = state.draft.title.isNotBlank() && valid,
            onClick = viewModel::nextStep,
        )
    }
}

// ---------- title ----------

@Composable
private fun TitleField(title: String, onTitle: (String) -> Unit) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitle,
        placeholder = { Text("Dinner, cab, tickets…") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- who paid ----------

@Composable
private fun PaidByBlock(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val people = if (state.serverMode) {
        state.serverPeople.map { it.id to memberLabel(it) }
    } else {
        viewModel.participantPool(state).map { it to nameOf(state, it) }
    }
    val selected = state.payerIds

    if (people.isEmpty()) {
        Text(
            text = if (state.membersLoading) "Loading members…" else "No people to split with yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(people, key = { it.first }) { (id, label) ->
            PayerAvatar(
                name = label,
                selected = id in selected,
                onClick = { viewModel.togglePayer(id) },
            )
        }
    }

    // Per-payer amounts once more than one person is chipping in.
    if (state.draft.payers.size > 1) {
        Spacer(Modifier.height(10.dp))
        SheetCard {
            state.draft.payers.forEach { (uid, minor) ->
                PayerAmountRow(
                    name = nameOf(state, uid),
                    amountMinor = minor,
                    onAmount = { viewModel.setPayerAmount(uid, it) },
                )
            }
        }
        val sum = state.draft.payers.values.sum()
        FieldNote(
            text = if (sum == state.draft.amountMinor) {
                "Adds up to ₹${formatInr(sum)} ✓"
            } else {
                "Adds up to ₹${formatInr(sum)} of ₹${formatInr(state.draft.amountMinor)}"
            },
            isError = sum != state.draft.amountMinor,
        )
    }
}

// ---------- who is included ----------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ParticipantBlock(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val members = state.serverPeople
    if (members.isEmpty()) {
        Text(
            text = when {
                state.membersLoading -> "Loading members…"
                state.membersError != null -> state.membersError
                state.draft.groupId != null -> "No members found in this group"
                else -> "No friends yet — create a group and invite someone first"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.membersLoading && state.draft.groupId != null) {
            TextButton(onClick = viewModel::retryMembers) { Text("Try again") }
        }
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        members.forEach { member ->
            MemberChip(
                label = memberLabel(member),
                selected = member.id in state.draft.includedServer,
                onClick = { viewModel.toggleParticipantServer(member.id) },
            )
        }
    }

    debtLabel(state, state.draft.includedServer.size)?.let {
        FieldNote(text = it)
    }

    // Outside a group the only people you can split with are your friends — let
    // the user reach one they're currently square with by email.
    if (state.draft.groupId == null) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.friendSearch.query,
                onValueChange = viewModel::onFriendQueryChange,
                placeholder = { Text("Add a friend's email") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = viewModel::addFriendByEmail,
                enabled = !state.friendSearch.searching && state.friendSearch.query.isNotBlank(),
            ) {
                Text(if (state.friendSearch.searching) "Adding…" else "Add")
            }
        }
        state.friendSearch.error?.let { FieldNote(text = it, isError = true) }
    }
}

// ---------- preview ----------

@Composable
private fun PreviewBlock(
    state: AddExpenseUiState,
    viewModel: AddExpenseViewModel,
    shares: List<Pair<String, Long>>?,
    modifier: Modifier = Modifier,
) {
    if (shares == null) {
        val error = if (state.serverMode) {
            viewModel.serverValidationError(state)
        } else {
            viewModel.validationError(state)
        }
        Text(
            text = error ?: "Enter per-person values above",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        return
    }

    SheetCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "₹${formatInr(state.draft.amountMinor)}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        Hairline()
        shares.forEachIndexed { index, (userId, minor) ->
            ShareRow(
                state = state,
                userId = userId,
                amountMinor = minor,
                isPayer = userId in state.payerIds,
            )
            if (index != shares.lastIndex) Hairline()
        }
    }
}
