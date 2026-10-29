package com.example.teramera.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.core.time.TimeFormat
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.CheckIcon
import com.example.teramera.ui.components.EmptyState
import com.example.teramera.ui.components.InkHeader
import com.example.teramera.ui.components.PlusIcon
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Ink900

private enum class ActivityFilter(val label: String) { All("All"), Expenses("Expenses"), Settlements("Settlements") }

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filterIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        InkHeader {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f),
                )
                Baanto(state = BaantoState.Thinking, size = 40.dp, animate = true)
            }
        }

        SegmentedTabs(
            tabs = ActivityFilter.entries.map { it.label },
            selectedIndex = filterIndex,
            onSelect = { filterIndex = it },
        )

        val filters = ActivityFilter.entries
        val filtered = when (filters[filterIndex]) {
            ActivityFilter.All -> state.events
            ActivityFilter.Expenses -> state.events.filterIsInstance<ActivityEvent.ExpenseAdded>()
            ActivityFilter.Settlements -> state.events.filterIsInstance<ActivityEvent.SettlementMade>()
        }

        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)) {
            // Events arrive sorted newest-first; groupBy preserves encounter order per day.
            filtered.groupBy { TimeFormat.dayStart(it.createdAt) }.forEach { (day, events) ->
                item(key = "day-$day") {
                    Text(
                        TimeFormat.dayLabel(day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp),
                    ) {
                        events.forEachIndexed { index, event ->
                            EventRow(event)
                            if (index != events.lastIndex) Hairline()
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "Expenses and settlements you're part of will show up here.",
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: ActivityEvent) {
    when (event) {
        is ActivityEvent.ExpenseAdded -> ExpenseRow(event)
        is ActivityEvent.SettlementMade -> SettlementRow(event)
    }
}

@Composable
private fun ExpenseRow(event: ActivityEvent.ExpenseAdded) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
    ) {
        EventBadge(
            bgColor = MaterialTheme.colorScheme.primaryContainer,
            content = { PlusIcon(tint = Ink900, size = 18.dp) },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AnnotatedText(bold = event.payerName, rest = " added ${event.title}")
            Text(
                "₹${formatInr(event.amountMinor)} · ${event.participantCount} people · ${TimeFormat.time(event.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (event.groupName != null) {
                Text(event.groupName!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "your share ₹${formatInr(event.myShareMinor)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettlementRow(event: ActivityEvent.SettlementMade) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
    ) {
        EventBadge(
            bgColor = MaterialTheme.colorScheme.secondaryContainer,
            content = { CheckIcon(tint = Ink900, size = 18.dp) },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AnnotatedText(bold = event.payerName, rest = " settled up with ${event.payeeName}")
            Text(
                "${event.methodLabel} · ${TimeFormat.time(event.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("₹${formatInr(event.amountMinor)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EventBadge(
    bgColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AnnotatedText(bold: String, rest: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            append(rest)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
