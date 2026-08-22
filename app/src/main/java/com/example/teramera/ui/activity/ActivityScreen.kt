package com.example.teramera.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.ui.home.formatInr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ActivityFilter(val label: String) { All("All"), Expenses("Expenses"), Settlements("Settlements") }

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filterIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Activity",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            ActivityFilter.entries.forEachIndexed { index, filter ->
                FilterChip(
                    label = filter.label,
                    selected = index == filterIndex,
                    onClick = { filterIndex = index },
                )
            }
        }

        val filters = ActivityFilter.entries
        val filtered = when (filters[filterIndex]) {
            ActivityFilter.All -> state.events
            ActivityFilter.Expenses -> state.events.filterIsInstance<ActivityEvent.ExpenseAdded>()
            ActivityFilter.Settlements -> state.events.filterIsInstance<ActivityEvent.SettlementMade>()
        }

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            // Events arrive sorted newest-first; groupBy preserves encounter order per day.
            filtered.groupBy { dayLabel(it.createdAt) }.forEach { (dayLabel, events) ->
                item(key = "day-$dayLabel") {
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 6.dp),
                    )
                }
                items(events, key = { it.id }) { event ->
                    EventRow(event)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
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
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        EventBadge(bgColor = MaterialTheme.colorScheme.primaryContainer, symbol = "+")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AnnotatedText(bold = event.payerName, rest = " added ${event.title}")
            Text(
                "₹${formatInr(event.amountMinor)} · split among ${event.participantCount} · ${timeLabel(event.createdAt)}",
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
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        EventBadge(bgColor = MaterialTheme.colorScheme.tertiaryContainer, symbol = "✓")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AnnotatedText(bold = event.payerName, rest = " settled up with ${event.payeeName}")
            Text(
                "${event.methodLabel} · ${timeLabel(event.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("₹${formatInr(event.amountMinor)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EventBadge(bgColor: androidx.compose.ui.graphics.Color, symbol: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun AnnotatedText(bold: String, rest: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            append(rest)
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
    )
}

private fun timeLabel(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun dayLabel(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    return when {
        sameDay(cal, today) -> "Today"
        sameDay(cal, (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
