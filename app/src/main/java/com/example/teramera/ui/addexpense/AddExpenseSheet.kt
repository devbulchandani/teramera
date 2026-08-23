package com.example.teramera.ui.addexpense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.repository.SplitType
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    fixedGroupId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft

    // When opened from a group, lock the group once its membership data arrives.
    if (fixedGroupId != null) {
        LaunchedEffect(fixedGroupId, state.membersByGroup[fixedGroupId]) {
            viewModel.setGroup(fixedGroupId)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            SheetHeader(
                step = draft.step,
                onClose = onDismiss,
                onBack = if (draft.step > 1) viewModel::previousStep else null,
            )
            if (fixedGroupId == null) {
                GroupSelector(state = state, onSelect = viewModel::setGroup)
            } else {
                val groupName = state.groups.firstOrNull { it.id == fixedGroupId }?.name
                if (groupName != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        SelectorChip(label = groupName, selected = true, onClick = {})
                    }
                }
            }

            when (draft.step) {
                1 -> AmountStep(state, viewModel)
                2 -> DetailsStep(state, viewModel)
                else ->
                    if (state.serverMode) ServerParticipantsStep(state, viewModel, onSaved = onDismiss)
                    else ParticipantsStep(state, viewModel, onSaved = onDismiss)
            }
        }
    }
}

@Composable
private fun SheetHeader(step: Int, onClose: () -> Unit, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderButton(
            label = if (onBack != null) "←" else "",
            onClick = { onBack?.invoke() },
            enabled = onBack != null,
        )
        Text(
            "Add expense",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        HeaderButton(label = "✕", onClick = onClose, enabled = true)
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(if (enabled) 1f else 0f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GroupSelector(state: AddExpenseUiState, onSelect: (String?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        SelectorChip(label = "No group", selected = state.draft.groupId == null) { onSelect(null) }
        state.groups.forEach { group ->
            SelectorChip(
                label = group.name,
                selected = state.draft.groupId == group.id,
            ) { onSelect(group.id) }
        }
    }
}

@Composable
private fun SelectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val border =
        if (selected) MaterialTheme.colorScheme.secondary
        else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------- Step 1: amount keypad ----------

@Composable
private fun AmountStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val amountText = state.draft.amountText.ifEmpty { "0" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text("₹ INR", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("₹", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(amountText, style = MaterialTheme.typography.displayLarge)
        }
        Text(
            text = "Split equally by default — change next",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }

    Keypad(
        onKey = viewModel::onKey,
        onBackspace = viewModel::onBackspace,
        modifier = Modifier.padding(horizontal = 12.dp),
    )

    PrimaryButton(
        label = "Next",
        enabled = state.draft.amountMinor > 0,
        onClick = viewModel::nextStep,
    )
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { if (key == "⌫") onBackspace() else onKey(key.first()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ---------- Step 2: details & split type ----------

@Composable
private fun DetailsStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.draft.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text("What was it for?") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.serverMode) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Paid by",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.participantPool(state).take(4).forEach { userId ->
                        PayerChip(
                            label = nameOf(state, userId),
                            selected = state.draft.paidByUserId == userId,
                            onClick = { viewModel.setPayer(userId) },
                        )
                    }
                }
                if (viewModel.participantPool(state).size > 4) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.participantPool(state).drop(4).forEach { userId ->
                            PayerChip(
                                label = nameOf(state, userId),
                                selected = state.draft.paidByUserId == userId,
                                onClick = { viewModel.setPayer(userId) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            if (state.serverMode) {
                ServerSplitSection(state = state, viewModel = viewModel)
            } else {
                SectionLabel("Split by")
                SplitTypeSelector(selected = state.draft.splitType, onSelect = viewModel::setSplitType)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!state.serverMode && state.draft.splitType != SplitType.EQUAL) {
            items(viewModel.participants(state), key = { "$it-edit" }) { userId ->
                RawValueRow(state = state, userId = userId, type = state.draft.splitType, onValue = viewModel::setRawValue)
            }
        }

        item { SectionLabelPreview(state, viewModel) }

        val shares = if (state.serverMode) null else viewModel.previewShares(state)
        if (shares != null) {
            items(shares, key = { "${it.first}-share" }) { (userId, minor) ->
                ShareRow(state = state, userId = userId, amountMinor = minor)
                HorizontalHairline()
            }
        } else if (!state.serverMode) {
            item {
                Text(
                    viewModel.validationError(state) ?: "Enter per-person values above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
    PrimaryButton(
        label = "Next",
        enabled = state.draft.title.isNotBlank() && state.draft.amountMinor > 0 &&
            (state.serverMode || sharesValid(state, viewModel)),
        onClick = viewModel::nextStep,
    )
}

@Composable
private fun PayerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val border =
        if (selected) MaterialTheme.colorScheme.primary
        else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionLabelPreview(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    Text(
        "₹${formatInr(state.draft.amountMinor)} total · ${viewModel.participants(state).size} people",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SplitTypeSelector(selected: SplitType, onSelect: (SplitType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(SplitType.EQUAL, SplitType.EXACT, SplitType.PERCENT, SplitType.SHARES).forEach { type ->
            val isSelected = type == selected
            val bg =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            val borderColor =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                    .clickable { onSelect(type) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (type) {
                        SplitType.EQUAL -> "Equal"
                        SplitType.EXACT -> "Exact"
                        SplitType.PERCENT -> "%"
                        SplitType.SHARES -> "Shares"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareRow(state: AddExpenseUiState, userId: String, amountMinor: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
    ) {
        Avatar(
            initials = initialsOf(nameOf(state, userId)),
            isViolet = userId.hashCode() % 2 == 0,
            size = 32.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            nameOf(state, userId),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        Text("₹${formatInr(amountMinor)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RawValueRow(state: AddExpenseUiState, userId: String, type: SplitType, onValue: (String, Long) -> Unit) {
    val current = state.draft.rawValues[userId] ?: 0L
    val prefix = when (type) {
        SplitType.EXACT -> "₹"
        SplitType.PERCENT -> "%"
        else -> "×"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Avatar(
            initials = initialsOf(nameOf(state, userId)),
            isViolet = userId.hashCode() % 2 == 0,
            size = 32.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(nameOf(state, userId), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        Text(prefix, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = if (current == 0L) "" else current.toString(),
            onValueChange = { text -> onValue(userId, text.toLongOrNull() ?: 0L) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(120.dp),
        )
    }
}

// ---------- Step 3: participants ----------

@Composable
private fun ParticipantsStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel, onSaved: () -> Unit) {
    if (state.serverMode) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Everyone in ${state.groups.firstOrNull { it.id == state.draft.groupId }?.name ?: "the group"} pays equally.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "₹${formatInr(state.draft.amountMinor)} · saved to your teramera account",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        PrimaryButton(
            label = "Save expense · ₹${formatInr(state.draft.amountMinor)}",
            enabled = !state.saving && state.draft.title.isNotBlank() && state.draft.amountMinor > 0,
            onClick = { viewModel.save(onDone = onSaved) },
        )
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        return
    }

    val pool = viewModel.participantPool(state)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item { SectionLabel("Who's in") }
        items(pool, key = { it }) { userId ->
            ParticipantToggle(
                state = state,
                userId = userId,
                included = userId in state.draft.included,
                onToggle = { viewModel.toggleParticipant(userId) },
            )
            HorizontalHairline()
        }
        item {
            val shares = viewModel.previewShares(state)
            Text(
                text = shares?.joinToString(" · ") { (u, m) -> "${nameOf(state, u)} ₹${formatInr(m)}" }
                    ?: "Pick at least two people",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
    PrimaryButton(
        label = "Save expense · ₹${formatInr(state.draft.amountMinor)}",
        enabled = !state.saving && sharesValid(state, viewModel),
        onClick = { viewModel.save(onDone = onSaved) },
    )
    state.error?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ParticipantToggle(state: AddExpenseUiState, userId: String, included: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onToggle),
    ) {
        Column(Modifier.alpha(if (included) 1f else 0.42f)) {
            Avatar(initials = initialsOf(nameOf(state, userId)), isViolet = userId.hashCode() % 2 == 0, size = 44.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            nameOf(state, userId),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            modifier = Modifier
                .weight(1f)
                .alpha(if (included) 1f else 0.42f),
        )
        TickMark(included = included)
    }
}

@Composable
private fun TickMark(included: Boolean) {
    val bg =
        if (included) MaterialTheme.colorScheme.primary
        else Color.Transparent
    val borderColor =
        if (included) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (included) {
            Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------- shared bits ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HorizontalHairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
}

private fun sharesValid(state: AddExpenseUiState, viewModel: AddExpenseViewModel): Boolean =
    state.draft.included.isNotEmpty() &&
        state.draft.amountMinor > 0 &&
        viewModel.previewShares(state) != null

private fun userById(state: AddExpenseUiState, userId: String) =
    if (userId == AddExpenseViewModel.SELF) state.self else state.friends.firstOrNull { it.id == userId }

private fun nameOf(state: AddExpenseUiState, userId: String) =
    if (userId == AddExpenseViewModel.SELF) "You" else (userById(state, userId)?.name ?: userId)

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")


// ---------- server-mode sections ----------

@Composable
private fun ServerSplitSection(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val members = state.serverMembers
    if (members.isEmpty()) {
        Text(
            "Loading members…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }


    val activePayers = state.draft.payers.keys.ifEmpty {
        setOfNotNull(state.selfServerId)
    }
    SectionLabel("Paid by · ${activePayers.size} selected")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        members.chunked(3).forEach { rowMembers ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMembers.forEach { member ->
                    val selected = member.id in activePayers
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.togglePayer(member.id) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(memberLabel(member), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    // per-payer amounts when more than one payer is selected
    val editedPayers = state.draft.payers.filterValues { it > 0 }
    if (activePayers.size > 1) {
        Spacer(Modifier.height(8.dp))
        (if (editedPayers.isNotEmpty()) editedPayers else state.draft.payers).forEach { (uid, minor) ->
            val member = members.firstOrNull { it.id == uid }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("₹ from ${member?.let { memberLabel(it) } ?: uid}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = if (minor == 0L) "" else (minor / 100).toString(),
                    onValueChange = { text -> viewModel.setPayerAmount(uid, (text.toLongOrNull() ?: 0L) * 100) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        val sum = state.draft.payers.values.sum()
        Text(
            text = if (sum == state.draft.amountMinor) "✓ adds up to ₹${formatInr(sum)}"
            else "Adds up to ₹${formatInr(sum)} of ₹${formatInr(state.draft.amountMinor)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (sum == state.draft.amountMinor) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("Split between · ${state.draft.includedServer.size} people")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        members.chunked(3).forEach { rowMembers ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMembers.forEach { member ->
                    val included = member.id in state.draft.includedServer
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (included) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.toggleParticipantServer(member.id) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(memberLabel(member), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    Text(
        "${state.draft.includedServer.size} people · equal shares",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ServerParticipantsStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel, onSaved: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "Review",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        val payers = state.draft.payers
        val payerText = if (payers.isEmpty() || payers.size == 1 && payers.values.first() == state.draft.amountMinor) {
            "Paid by you"
        } else {
            "Split payment: " + payers.entries.joinToString(" + ") { (uid, m) -> "₹${formatInr(m)}" } + " paid"
        }
        Text(payerText, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${state.draft.includedServer.size} people split equally · saved to your teramera account",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        val payersSumOk = state.draft.payers.isEmpty() ||
            state.draft.payers.values.sum() == state.draft.amountMinor
        Button(
            onClick = { viewModel.save(onDone = onSaved) },
            enabled = !state.saving && state.draft.title.isNotBlank() &&
                state.draft.amountMinor > 0 && state.draft.includedServer.size >= 2 && payersSumOk,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(56.dp),
        ) {
            Text("Save expense · ₹${formatInr(state.draft.amountMinor)}", style = MaterialTheme.typography.labelLarge)
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun memberLabel(member: com.example.teramera.core.network.MemberDto): String =
    if (member.isSelf) "You" else member.name.ifBlank { "Member" }
