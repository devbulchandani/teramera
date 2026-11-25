package com.example.teramera.ui.addexpense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.teramera.data.repository.SplitType
import com.example.teramera.ui.components.BackspaceIcon
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr

/**
 * The reusable controls the steps are assembled from. Nothing in here knows
 * about a step, a draft or a save flow — that keeps the step files readable.
 */

// ---------- keypad ----------

@Composable
internal fun Keypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    KeypadKey(
                        onClick = { if (key == "⌫") onBackspace() else onKey(key.first()) },
                        contentDescription = if (key == "⌫") "Backspace" else key,
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight),
                    ) {
                        if (key == "⌫") {
                            BackspaceIcon(
                                tint = MaterialTheme.colorScheme.onSurface,
                                size = 22.dp,
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.displayMedium,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 16.sp,
                                    maxFontSize = 30.sp,
                                    stepSize = 2.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---------- split type ----------

@Composable
internal fun SplitTypeSelector(selected: SplitType, onSelect: (SplitType) -> Unit) {
    val types = listOf(
        SplitType.EQUAL,
        SplitType.EXACT,
        SplitType.PERCENT,
        SplitType.SHARES,
    )
    Column {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            types.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(
            text = splitHint(selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}

@Composable
private fun SplitTile(
    type: SplitType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.medium,
                    )
                } else Modifier
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        SplitGlyph(type = type, selected = selected)
        Spacer(Modifier.width(10.dp))
        Text(
            text = splitLabel(type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SplitGlyph(type: SplitType, selected: Boolean) {
    val widths = when (type) {
        SplitType.EQUAL -> listOf(4f, 4f, 4f, 4f)
        SplitType.EXACT -> listOf(9f, 5f, 12f)
        SplitType.PERCENT -> listOf(10f, 7f, 4f, 8f)
        SplitType.SHARES -> listOf(4f, 8f, 12f, 6f)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        widths.forEach { width ->
            Box(
                Modifier
                    .width(width.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
            )
        }
    }
}

// ---------- people ----------

@Composable
internal fun PayerAvatar(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 56.dp, max = 84.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
internal fun MemberChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- rows ----------

@Composable
internal fun ShareRow(
    state: AddExpenseUiState,
    userId: String,
    amountMinor: Long,
    isPayer: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Avatar(
            initials = initialsOf(nameOf(state, userId)),
            isViolet = userId.hashCode() % 2 == 0,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = nameOf(state, userId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isPayer) "paid" else "included",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "₹${formatInr(amountMinor)}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RawValueRow(
    state: AddExpenseUiState,
    userId: String,
    type: SplitType,
    onValue: (String, Long) -> Unit,
) {
    val current = state.draft.rawValues[userId] ?: 0L
    val prefix = when (type) {
        SplitType.EXACT -> "₹"
        SplitType.PERCENT -> "%"
        else -> "×"
    }
    // Exact amounts are typed in rupees, everything else in its own unit.
    val shown = when {
        type == SplitType.EXACT && current > 0 -> (current / 100).toString()
        current == 0L -> ""
        else -> current.toString()
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
        Text(
            text = nameOf(state, userId),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = prefix,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = shown,
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L
                onValue(userId, if (type == SplitType.EXACT) parsed * 100 else parsed)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.widthIn(min = 92.dp, max = 124.dp),
        )
    }
}

@Composable
internal fun PayerAmountRow(
    name: String,
    amountMinor: Long,
    onAmount: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "₹ from $name",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = if (amountMinor == 0L) "" else (amountMinor / 100).toString(),
            onValueChange = { text ->
                onAmount((text.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 100)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.widthIn(min = 96.dp, max = 128.dp),
        )
    }
}
