package com.example.teramera.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.theme.TerameraTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onAddExpense: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, onAddExpense = onAddExpense, onOpenGroup = onOpenGroup, onLogout = onLogout)
}

enum class HomeTab(val label: String) { Friends("Friends"), Groups("Groups"), All("All") }

@Composable
fun HomeContent(
    state: HomeUiState,
    onAddExpense: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onLogout = onLogout)
        HeroCard(
            netMinor = state.netMinor,
            owedToYouMinor = state.owedToYouMinor,
            youOweMinor = state.youOweMinor,
        )
        SegmentedTabs(
            tabs = HomeTab.entries.map { it.label },
            selectedIndex = selectedTab,
        ) { selectedTab = it }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        ) {
            val entries = when (HomeTab.entries[selectedTab]) {
                HomeTab.Friends -> state.friends.map { it.copy(subtitle = friendSubtitle(it)) }
                HomeTab.Groups -> state.groups
                HomeTab.All -> state.friends.map { it.copy(subtitle = friendSubtitle(it)) } + state.groups
            }
            val owedYou = entries.filter { it.amountMinor > 0 }
            if (owedYou.isNotEmpty()) {
                item { SectionLabel("Owes you") }
                items(owedYou, key = { "${it.id}-pos" }) { entry ->
                    BalanceRow(entry, onClick = {
                        if (entry.id.startsWith("g_")) onOpenGroup(entry.id)
                    })
                    HorizontalHairline()
                }
            }
            val youOwe = entries.filter { it.amountMinor < 0 }
            if (youOwe.isNotEmpty()) {
                item { SectionLabel("You owe") }
                items(youOwe, key = { "${it.id}-neg" }) { entry ->
                    BalanceRow(entry, onClick = {
                        if (entry.id.startsWith("g_")) onOpenGroup(entry.id)
                    })
                    HorizontalHairline()
                }
            }
        }
    }
}

private fun friendSubtitle(entry: BalanceEntry): String =
    when {
        entry.amountMinor > 0 -> "you're owed ₹${formatInr(entry.amountMinor)}"
        entry.amountMinor < 0 -> "you owe ₹${formatInr(-entry.amountMinor)}"
        else -> "settled up"
    }

@Composable
internal fun TopBar(onLogout: () -> Unit = {}) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Good morning",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dev",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Box {
            Box(modifier = Modifier.clickable { menuOpen = true }) {
                Avatar(initials = "D", isViolet = false, size = 44.dp)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Text("⏻", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    },
                    onClick = {
                        menuOpen = false
                        onLogout()
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(netMinor: Long, owedToYouMinor: Long, youOweMinor: Long) {
    // Inverted-ink card: dark in light theme, cream in dark theme.
    val bg = MaterialTheme.colorScheme.onBackground
    val fg = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(20.dp),
    ) {
        Text(
            text = if (netMinor >= 0) "You are owed in total" else "You owe in total",
            style = MaterialTheme.typography.bodyMedium,
            color = fg.copy(alpha = 0.7f),
        )
        Text(
            text = if (netMinor >= 0) "₹${formatInr(netMinor)}" else "−₹${formatInr(-netMinor)}",
            style = MaterialTheme.typography.displayLarge,
            color = fg,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            HeroChip(
                label = "You're owed ₹${formatInr(owedToYouMinor)}",
                dotColor = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.tertiary,
            )
            HeroChip(
                label = "You owe ₹${formatInr(youOweMinor)}",
                dotColor = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HeroChip(label: String, dotColor: Color, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(textColor.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun SegmentedTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.7.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun BalanceRow(entry: BalanceEntry, onClick: () -> Unit = {}) {
    val positive = entry.amountMinor > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
    ) {
        Avatar(initials = entry.initials, isViolet = entry.isViolet, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "₹${formatInr(kotlin.math.abs(entry.amountMinor) / 100)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (positive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = if (positive) "you're owed" else "you owe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HorizontalHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
internal fun Avatar(initials: String, isViolet: Boolean, size: androidx.compose.ui.unit.Dp) {
    val primary = if (isViolet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val soft = if (isViolet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(0f to primary, 0.5f to primary, 0.5f to soft)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

// Minor units are paise; UI shows whole rupees.
internal fun formatInr(minor: Long): String =
    NumberFormat.getIntegerInstance(Locale("en", "IN")).format((minor + 50) / 100)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun HomeContentPreview() {
    TerameraTheme {
        HomeContent(
            state = HomeUiState(
                friends = listOf(
                    BalanceEntry("u_priya", "Priya Sharma", "PS", false, "Goa Trip", 312_000L),
                    BalanceEntry("u_kabir", "Kabir Shah", "KS", true, "Direct", -92_000L),
                ),
                groups = listOf(
                    BalanceEntry("g_goa", "Goa Trip", "GT", true, "5 members · 3 expenses", 828_400L),
                ),
            ),
            onAddExpense = {},
        )
    }
}
