package com.example.teramera.ui.addexpense

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.teramera.ui.components.ArrowBackIcon
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.CloseIcon
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Paper100

/**
 * The ink header carries the one number that matters on step 1 and shrinks to a
 * compact summary on steps 2 and 3 so the form below has room to breathe. The
 * sheet itself pads for the status bar (and any cutout), so this block starts
 * below them and never fights the system bar icons.
 */
@Composable
internal fun AddExpenseHeader(
    step: Int,
    draft: AddDraft,
    state: AddExpenseUiState,
    fixedGroupId: String?,
    compact: Boolean,
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
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = if (step == 1) 18.dp else 16.dp,
            ),
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
                text = "Add expense",
                style = MaterialTheme.typography.titleMedium,
                color = cream,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Baanto(state = BaantoState.Idle, size = 34.dp)
            Spacer(Modifier.width(8.dp))
            HeaderButton(
                enabled = true,
                contentDescription = "Close",
                onClick = onClose,
            ) {
                CloseIcon(tint = Paper100, size = 20.dp)
            }
        }

        ProgressPills(step = step, modifier = Modifier.padding(top = 10.dp, start = 4.dp))

        if (step == 1) {
            Spacer(Modifier.height(12.dp))
            GroupPicker(
                state = state,
                fixedGroupId = fixedGroupId,
                onSelectGroup = onSelectGroup,
            )
            AmountHero(
                amountText = draft.amountText,
                maxFontSize = if (compact) 40.sp else 56.sp,
            )
            if (!compact) {
                val people = if (state.serverMode) {
                    draft.includedServer.size
                } else {
                    draft.included.size
                }
                Text(
                    text = "Equal split by default · ${people.coerceAtLeast(1)} " +
                        if (people.coerceAtLeast(1) == 1) "person" else "people",
                    style = MaterialTheme.typography.bodySmall,
                    color = cream.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            DraftSummaryLine(draft = draft, state = state, compact = compact)
        }
    }
}

/**
 * 48 dp targets. The disabled state keeps its slot so the title stays centred
 * whether or not there is a step to go back to, and it is not announced.
 */
@Composable
private fun HeaderButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Paper100.copy(alpha = if (enabled) 0.12f else 0f))
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) content()
    }
}

@Composable
private fun ProgressPills(step: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Step $step of 3"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
            InkChip(label = name, selected = true, onClick = null)
        }
        return
    }
    LazyRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
private fun InkChip(label: String, selected: Boolean, onClick: (() -> Unit)?) {
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    val base = Modifier
        .heightIn(min = 48.dp)
        .clip(CircleShape)
        .background(
            if (selected) MaterialTheme.colorScheme.primary
            else Paper100.copy(alpha = 0.12f)
        )
    Box(
        modifier = if (onClick != null) {
            base.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        } else {
            base
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The amount is the only hero on the screen, so it shrinks to fit rather than
 * clipping. The maximum is capped by the system font scale as well, so a 2×
 * accessibility setting cannot hand the hero half of a small phone's height.
 */
@Composable
private fun AmountHero(
    amountText: String,
    maxFontSize: TextUnit,
) {
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    val primary = MaterialTheme.colorScheme.primary
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cappedMax = (maxFontSize.value / fontScale).coerceAtLeast(24f).sp
    val styled = remember(amountText, cream, primary) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontSize = 0.62.em)) { append("₹") }
            withStyle(SpanStyle(color = cream)) { append(amountText.ifEmpty { "0" }) }
        }
    }
    Text(
        text = styled,
        style = MaterialTheme.typography.displayLarge,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 22.sp,
            maxFontSize = cappedMax,
            stepSize = 2.sp,
        ),
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
}

/** "₹500 · Goa Trip · 3 people" — context without re-showing the hero. */
@Composable
private fun DraftSummaryLine(
    draft: AddDraft,
    state: AddExpenseUiState,
    compact: Boolean,
) {
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    val groupName = state.groups.firstOrNull { it.id == draft.groupId }?.name
    val people = if (state.serverMode) draft.includedServer.size else draft.included.size
    val summary = buildString {
        append("₹")
        append(formatInr(draft.amountMinor))
        append(" · ")
        append(groupName ?: "No group")
        if (!compact) {
            append(" · ")
            append(people.coerceAtLeast(1))
            append(if (people <= 1) " person" else " people")
        }
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = cream.copy(alpha = 0.78f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}
