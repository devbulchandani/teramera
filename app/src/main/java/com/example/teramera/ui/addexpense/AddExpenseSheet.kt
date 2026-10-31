package com.example.teramera.ui.addexpense

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.core.time.TimeFormat
import com.example.teramera.data.repository.SplitType
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.BackspaceIcon
import com.example.teramera.ui.components.CloseIcon
import com.example.teramera.ui.components.ArrowBackIcon
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Ink900
import com.example.teramera.ui.theme.Paper100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    fixedGroupId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Fresh draft every time the sheet opens; then lock the group (if any) and load
    // its members. Re-fires when server mode flips on — the first pass can land
    // before the signed-in state + synced groups arrive.
    LaunchedEffect(Unit) { viewModel.prepare() }
    LaunchedEffect(fixedGroupId, state.serverMode, state.selfServerId) {
        if (fixedGroupId != null) viewModel.setGroup(fixedGroupId) else viewModel.ensureServerDefaults()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            SheetHeader(
                step = draft.step,
                draft = draft,
                state = state,
                fixedGroupId = fixedGroupId,
                onClose = onDismiss,
                onBack = if (draft.step > 1) viewModel::previousStep else null,
                onSelectGroup = viewModel::setGroup,
            )

            AnimatedContent(
                targetState = draft.step,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { direction * it / 5 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(220)) { -direction * it / 5 } + fadeOut(tween(140)))
                },
                modifier = Modifier.weight(1f),
                label = "expenseStep",
            ) { step ->
                Column(Modifier.fillMaxSize()) {
                    when (step) {
                        1 -> AmountStep(state, viewModel)
                        2 -> DetailsStep(state, viewModel)
                        else -> ReviewStep(state, viewModel, onSaved = onDismiss)
                    }
                }
            }
        }
    }
}

// ---------- ink header ----------

@Composable
private fun SheetHeader(
    step: Int,
    draft: AddDraft,
    state: AddExpenseUiState,
    fixedGroupId: String?,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSelectGroup: (String?) -> Unit,
) {
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (step == 1) 18.dp else 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HeaderButton(
                enabled = onBack != null,
                contentDescription = "Back",
                onClick = { onBack?.invoke() },
            ) {
                ArrowBackIcon(tint = Paper100, size = 20.dp)
            }
            Text(
                "Add expense",
                style = MaterialTheme.typography.titleMedium,
                color = cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Baanto(state = BaantoState.Idle, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            HeaderButton(
                enabled = true,
                contentDescription = "Close",
                onClick = onClose,
            ) {
                CloseIcon(tint = Paper100, size = 20.dp)
            }
        }

        ProgressPills(step = step, modifier = Modifier.padding(top = 12.dp, start = 4.dp))

        if (step == 1) {
            Spacer(Modifier.height(14.dp))
            GroupPicker(
                state = state,
                fixedGroupId = fixedGroupId,
                onSelectGroup = onSelectGroup,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    draft.amountText.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                    color = cream,
                )
            }
            val people = if (state.serverMode) state.draft.includedServer.size else state.draft.included.size
            Text(
                "Equal split by default · ${people.coerceAtLeast(1)} " +
                    "${if (people.coerceAtLeast(1) == 1) "person" else "people"}",
                style = MaterialTheme.typography.bodySmall,
                color = cream.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun HeaderButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Paper100.copy(alpha = if (enabled) 0.12f else 0f))
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) content()
    }
}

@Composable
private fun ProgressPills(step: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val active = index + 1 == step
            val width by animateDpAsState(
                targetValue = if (active) 38.dp else 26.dp,
                animationSpec = tween(220),
                label = "progressWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary
                else Paper100.copy(alpha = 0.18f),
                animationSpec = tween(220),
                label = "progressColor",
            )
            Box(
                Modifier
                    .width(width)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun GroupPicker(
    state: AddExpenseUiState,
    fixedGroupId: String?,
    onSelectGroup: (String?) -> Unit,
) {
    val selectedId = state.draft.groupId
    if (fixedGroupId != null) {
        val name = state.groups.firstOrNull { it.id == fixedGroupId }?.name
        if (name != null) {
            InkChip(label = name, selected = true, onClick = {})
        }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InkChip(
                label = "No group",
                selected = selectedId == null,
                onClick = { onSelectGroup(null) },
            )
        }
        items(state.groups, key = { it.id }) { group ->
            InkChip(
                label = group.name,
                selected = selectedId == group.id,
                onClick = { onSelectGroup(group.id) },
            )
        }
    }
}

@Composable
private fun InkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Paper100.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else cream,
        )
    }
}

// ---------- Step 1: amount keypad ----------

@Composable
private fun ColumnScope.AmountStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Spacer(Modifier.weight(1f))
        Keypad(
            onKey = viewModel::onKey,
            onBackspace = viewModel::onBackspace,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            label = "Continue",
            enabled = state.draft.amountMinor > 0,
            onClick = viewModel::nextStep,
        )
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        onClick = { if (key == "⌫") onBackspace() else onKey(key.first()) },
                        contentDescription = if (key == "⌫") "Backspace" else key,
                    ) {
                        if (key == "⌫") {
                            BackspaceIcon(tint = MaterialTheme.colorScheme.onSurface, size = 24.dp)
                        } else {
                            Text(key, style = MaterialTheme.typography.displayMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---------- Step 2: details & split type ----------

@Composable
private fun ColumnScope.DetailsStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.draft.title,
                    onValueChange = viewModel::setTitle,
                    placeholder = { Text("What was it for?") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!state.serverMode) {
                    Spacer(Modifier.height(6.dp))
                    PaidBySection(state = state, viewModel = viewModel)
                }

                Spacer(Modifier.height(10.dp))
                if (state.serverMode) {
                    ServerSplitSection(state = state, viewModel = viewModel)
                } else {
                    SectionLabel("How to split?")
                    SplitTypeSelector(selected = state.draft.splitType, onSelect = viewModel::setSplitType)
                }

                if (!state.serverMode && state.draft.splitType != SplitType.EQUAL) {
                    Spacer(Modifier.height(8.dp))
                    viewModel.participants(state).forEach { userId ->
                        RawValueRow(
                            state = state,
                            userId = userId,
                            type = state.draft.splitType,
                            onValue = viewModel::setRawValue,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                val shares = if (state.serverMode) viewModel.serverShares(state) else viewModel.previewShares(state)
                SectionLabel("Included")
                if (shares != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp),
                    ) {
                        shares.forEachIndexed { index, (userId, minor) ->
                            ShareRow(state = state, userId = userId, amountMinor = minor, isPayer = userId in state.payerIds)
                            if (index != shares.lastIndex) HorizontalHairline()
                        }
                    }
                } else {
                    Text(
                        (if (state.serverMode) viewModel.serverValidationError(state) else viewModel.validationError(state))
                            ?: "Enter per-person values above",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        PrimaryButton(
            label = "Review split",
            enabled = state.draft.title.isNotBlank() && state.draft.amountMinor > 0 &&
                (if (state.serverMode) serverSharesValid(state, viewModel) else sharesValid(state, viewModel)),
            onClick = viewModel::nextStep,
        )
    }
}

/**
 * Who fronted the money. Works in both modes: offline ids come from the device
 * profile, signed-in ids from the group (or your friends). Several payers get
 * one amount field each, and the total must match the expense.
 */
@Composable
private fun PaidBySection(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val people = if (state.serverMode) {
        state.serverPeople.map { MemberRow(it.id, memberLabel(it)) }
    } else {
        viewModel.participantPool(state).map { MemberRow(it, nameOf(state, it)) }
    }
    val selected = state.payerIds
    SectionLabel("Who paid?")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(people, key = { it.id }) { person ->
            PayerAvatar(
                label = person.label,
                selected = person.id in selected,
                onClick = { viewModel.togglePayer(person.id) },
            )
        }
    }

    // per-payer amounts once more than one person is chipping in
    if (state.draft.payers.size > 1) {
        Spacer(Modifier.height(8.dp))
        state.draft.payers.forEach { (uid, minor) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "₹ from ${nameOf(state, uid)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = if (minor == 0L) "" else (minor / 100).toString(),
                    onValueChange = { text ->
                        viewModel.setPayerAmount(uid, (text.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 100)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
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
}

private data class MemberRow(val id: String, val label: String)

@Composable
private fun PayerAvatar(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SplitTypeSelector(selected: SplitType, onSelect: (SplitType) -> Unit) {
    val types = listOf(SplitType.EQUAL, SplitType.EXACT, SplitType.PERCENT, SplitType.SHARES)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { type ->
                    SplitTile(
                        type = type,
                        selected = type == selected,
                        onClick = { onSelect(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTile(
    type: SplitType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                else Modifier
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(14.dp),
    ) {
        SplitGlyph(type = type, selected = selected)
        Text(
            when (type) {
                SplitType.EQUAL -> "Equal"
                SplitType.EXACT -> "Exact"
                SplitType.PERCENT -> "Percent"
                SplitType.SHARES -> "Shares"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            when (type) {
                SplitType.EQUAL -> "Everyone pays the same"
                SplitType.EXACT -> "Set each amount"
                SplitType.PERCENT -> "Split by percentage"
                SplitType.SHARES -> "Weighted portions"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SplitGlyph(type: SplitType, selected: Boolean) {
    val widths = when (type) {
        SplitType.EQUAL -> listOf(6f, 6f, 6f, 6f)
        SplitType.EXACT -> listOf(14f, 8f, 18f)
        SplitType.PERCENT -> listOf(16f, 10f, 6f, 12f)
        SplitType.SHARES -> listOf(6f, 12f, 18f, 9f)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        widths.forEach { width ->
            Box(
                Modifier
                    .width(width.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
            )
        }
    }
}

@Composable
private fun ShareRow(state: AddExpenseUiState, userId: String, amountMinor: Long, isPayer: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
    ) {
        Avatar(
            initials = initialsOf(nameOf(state, userId)),
            isViolet = userId.hashCode() % 2 == 0,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nameOf(state, userId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (isPayer) "paid" else "included",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    // Exact amounts are typed in rupees, everything else in its own unit.
    val shown = if (type == SplitType.EXACT && current > 0) (current / 100).toString() else if (current == 0L) "" else current.toString()
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
        Text(nameOf(state, userId), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(prefix, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = shown,
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L
                onValue(userId, if (type == SplitType.EXACT) parsed * 100 else parsed)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.width(120.dp),
        )
    }
}

// ---------- Step 3: review ----------

@Composable
private fun ColumnScope.ReviewStep(state: AddExpenseUiState, viewModel: AddExpenseViewModel, onSaved: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val shares = if (state.serverMode) viewModel.serverShares(state) else viewModel.previewShares(state)
    val total = state.draft.amountMinor
    val payerText = when {
        state.draft.payers.isEmpty() -> "You"
        state.draft.payers.size == 1 -> nameOf(state, state.draft.payers.keys.first())
        else -> state.draft.payers.entries.joinToString(" + ") { (uid, minor) ->
            "${nameOf(state, uid)} ₹${formatInr(minor)}"
        }
    }
    val groupName = state.groups.firstOrNull { it.id == state.draft.groupId }?.name

    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(18.dp),
                ) {
                    Baanto(state = BaantoState.Pleased, size = 64.dp, withCoin = false)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (shares.orEmpty().size == 1) {
                                "Personal expense${groupName?.let { " · $it" } ?: ""}"
                            } else {
                                "Splitting ${groupName ?: "no group"} · ${splitLabel(state.draft.splitType)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "₹",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                formatInr(total),
                                style = MaterialTheme.typography.displayLarge,
                            )
                        }
                        Text(
                            "Paid by $payerText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionLabel("Who owes what")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                ) {
                    shares.orEmpty().forEachIndexed { index, (userId, minor) ->
                        ShareRow(
                            state = state,
                            userId = userId,
                            amountMinor = minor,
                            isPayer = userId in state.payerIds,
                        )
                        if (index != shares.orEmpty().lastIndex) HorizontalHairline()
                    }
                }

                SectionLabel("When")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(18.dp),
                ) {
                    Text(
                        TimeFormat.dayTime(now),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                state.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        PrimaryButton(
            label = if (state.saving) {
                "Saving…"
            } else if (shares.orEmpty().size == 1) {
                "Save ₹${formatInr(total)}"
            } else {
                "Split ₹${formatInr(total)}"
            },
            enabled = !state.saving && state.draft.title.isNotBlank() && total > 0 &&
                (if (state.serverMode) serverSharesValid(state, viewModel) else sharesValid(state, viewModel)),
            onClick = { viewModel.save(onDone = onSaved) },
        )
    }
}

// ---------- shared bits ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
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

private fun serverSharesValid(state: AddExpenseUiState, viewModel: AddExpenseViewModel): Boolean =
    state.draft.includedServer.isNotEmpty() &&
        (state.draft.groupId == null || state.draft.includedServer.size >= 2) &&
        state.draft.amountMinor > 0 &&
        viewModel.serverShares(state) != null

private fun nameOf(state: AddExpenseUiState, userId: String): String = when {
    userId == AddExpenseViewModel.SELF || userId == state.selfServerId -> "You"
    else -> state.serverPeople.firstOrNull { it.id == userId }?.name?.ifBlank { "Member" }
        ?: state.friends.firstOrNull { it.id == userId }?.name
        ?: state.self?.takeIf { it.id == userId }?.name
        ?: userId
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")


// ---------- server-mode sections ----------

@Composable
private fun ServerSplitSection(state: AddExpenseUiState, viewModel: AddExpenseViewModel) {
    val members = state.serverPeople
    if (members.isEmpty()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                when {
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
        }
        return
    }

    PaidBySection(state = state, viewModel = viewModel)

    Spacer(Modifier.height(8.dp))
    val includedCount = state.draft.includedServer.size
    SectionLabel("Split between · $includedCount ${if (includedCount == 1) "person" else "people"}")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.chunked(3).forEach { rowMembers ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMembers.forEach { member ->
                    MemberChip(
                        label = memberLabel(member),
                        selected = member.id in state.draft.includedServer,
                        onClick = { viewModel.toggleParticipantServer(member.id) },
                    )
                }
            }
        }
    }

    // Outside a group the only people you can split with are your friends — let the
    // user reach one they're currently square with by email.
    if (state.draft.groupId == null) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.friendSearch.query,
                onValueChange = viewModel::onFriendQueryChange,
                placeholder = { Text("Add a friend's email") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = viewModel::addFriendByEmail,
                enabled = !state.friendSearch.searching && state.friendSearch.query.isNotBlank(),
            ) {
                Text(if (state.friendSearch.searching) "Adding…" else "Add")
            }
        }
        state.friendSearch.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    SectionLabel("How to split?")
    SplitTypeSelector(selected = state.draft.splitType, onSelect = viewModel::setSplitType)

    if (state.draft.splitType != SplitType.EQUAL) {
        Spacer(Modifier.height(8.dp))
        state.draft.includedServer.forEach { uid ->
            RawValueRow(
                state = state,
                userId = uid,
                type = state.draft.splitType,
                onValue = viewModel::setRawValue,
            )
        }
    }
}

@Composable
private fun MemberChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun splitLabel(type: SplitType): String = when (type) {
    SplitType.EQUAL -> "equal"
    SplitType.EXACT -> "exact amounts"
    SplitType.PERCENT -> "percentages"
    SplitType.SHARES -> "shares"
}

private fun memberLabel(member: com.example.teramera.core.network.MemberDto): String =
    if (member.isSelf) "You" else member.name.ifBlank { "Member" }
