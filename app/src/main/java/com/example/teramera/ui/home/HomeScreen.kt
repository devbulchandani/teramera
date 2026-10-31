package com.example.teramera.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.EmptyState
import com.example.teramera.ui.components.GroupMascot
import com.example.teramera.ui.components.InkHeader
import com.example.teramera.ui.components.PowerIcon
import com.example.teramera.ui.theme.Ink900
import com.example.teramera.ui.theme.Mint
import com.example.teramera.ui.theme.Peach
import com.example.teramera.ui.theme.Paper100
import com.example.teramera.ui.theme.TerameraTheme
import coil.compose.SubcomposeAsyncImage
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onAddExpense: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        updateCheck = updateCheck,
        versionName = viewModel.versionName,
        versionCode = viewModel.versionCode,
        onAddExpense = onAddExpense,
        onOpenGroup = onOpenGroup,
        onLogout = onLogout,
        darkTheme = darkTheme,
        onToggleTheme = onToggleTheme,
        onSaveProfile = viewModel::saveProfile,
        onCheckForUpdates = viewModel::checkForUpdates,
        onInstallUpdate = viewModel::installUpdate,
        onDismissUpdateCheck = viewModel::resetUpdateCheck,
    )
}

enum class HomeTab(val label: String) { Friends("Friends"), Groups("Groups"), All("All") }

@Composable
fun HomeContent(
    state: HomeUiState,
    updateCheck: UpdateCheck = UpdateCheck(),
    versionName: String = com.example.teramera.BuildConfig.VERSION_NAME,
    versionCode: Int = com.example.teramera.BuildConfig.VERSION_CODE,
    onAddExpense: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onSaveProfile: (name: String, upiId: String) -> Unit = { _, _ -> },
    onCheckForUpdates: () -> Unit = {},
    onInstallUpdate: (com.example.teramera.core.update.UpdateInfo) -> Unit = {},
    onDismissUpdateCheck: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var profileOpen by remember { mutableStateOf(false) }
    var versionOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            state = state,
            onLogout = onLogout,
            onOpenProfile = { profileOpen = true },
            versionName = versionName,
            onOpenVersion = { versionOpen = true },
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme,
        )
        if (profileOpen) {
            ProfileDialog(
                state = state,
                onSave = { name, upi ->
                    onSaveProfile(name, upi)
                    profileOpen = false
                },
                onDismiss = { profileOpen = false },
            )
        }
        if (versionOpen) {
            VersionDialog(
                versionName = versionName,
                versionCode = versionCode,
                check = updateCheck,
                onCheck = onCheckForUpdates,
                onInstall = { info ->
                    onInstallUpdate(info)
                    versionOpen = false
                },
                onDismiss = {
                    versionOpen = false
                    onDismissUpdateCheck()
                },
            )
        }
        HeroCard(
            netMinor = state.netMinor,
            owedToYouMinor = state.owedToYouMinor,
            youOweMinor = state.youOweMinor,
            lastSyncAt = state.lastSyncAt,
        )
        SpendSummaryCard(
            thisMonthMinor = state.thisMonthSpentMinor,
            thisMonthCount = state.thisMonthExpenseCount,
            lastMonthMinor = state.lastMonthSpentMinor,
            totalMinor = state.selfSpentMinor,
        )
        SegmentedTabs(
            tabs = HomeTab.entries.map { it.label },
            selectedIndex = selectedTab,
        ) { selectedTab = it }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            val entries = when (HomeTab.entries[selectedTab]) {
                HomeTab.Friends -> state.friends.map { it.copy(subtitle = friendSubtitle(it)) }
                HomeTab.Groups -> state.groups
                HomeTab.All -> state.friends.map { it.copy(subtitle = friendSubtitle(it)) } + state.groups
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No balances yet",
                        message = "Add an expense or create a group to start splitting.",
                        actionLabel = "Add expense",
                        onAction = onAddExpense,
                    )
                }
            }
            val owedYou = entries.filter { it.amountMinor > 0 }
            if (owedYou.isNotEmpty()) {
                item(key = "sec-pos") {
                    SectionLabel("Owes you")
                    BalanceCard(owedYou) { entry ->
                        if (entry.isGroup) onOpenGroup(entry.id)
                    }
                }
            }
            val youOwe = entries.filter { it.amountMinor < 0 }
            if (youOwe.isNotEmpty()) {
                item(key = "sec-neg") {
                    SectionLabel("You owe")
                    BalanceCard(youOwe) { entry ->
                        if (entry.isGroup) onOpenGroup(entry.id)
                    }
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
internal fun TopBar(
    state: HomeUiState = HomeUiState(),
    onLogout: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    versionName: String = com.example.teramera.BuildConfig.VERSION_NAME,
    onOpenVersion: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    InkHeader {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = greetingFor(java.util.Calendar.getInstance()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cream.copy(alpha = 0.78f),
                )
                Text(
                    text = state.selfName.ifEmpty { "You" },
                    style = MaterialTheme.typography.titleLarge,
                    color = cream,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Baanto(state = BaantoState.Idle, size = 40.dp, animate = true)
                Box {
                    Box(
                        modifier = Modifier
                            .clickable { menuOpen = true }
                            .semantics {
                                contentDescription = "Profile and settings"
                                role = Role.Button
                            },
                    ) {
                        ProfileAvatar(
                            initials = initialsOfName(state.selfName.ifEmpty { "You" }),
                            photoUrl = state.selfPhotoUrl,
                            size = 44.dp,
                            borderColor = cream.copy(alpha = 0.28f),
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Profile & UPI") },
                            onClick = {
                                menuOpen = false
                                onOpenProfile()
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Dark mode") },
                            trailingIcon = {
                                Switch(
                                    checked = darkTheme,
                                    onCheckedChange = { onToggleTheme() },
                                )
                            },
                            onClick = { onToggleTheme() },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("App version") },
                            trailingIcon = {
                                Text(
                                    versionName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onOpenVersion()
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                PowerIcon(
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 20.dp,
                                )
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
    }
}

private fun greetingFor(calendar: java.util.Calendar): String = when (calendar.get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

private fun initialsOfName(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

@Composable
private fun HeroCard(
    netMinor: Long,
    owedToYouMinor: Long,
    youOweMinor: Long,
    lastSyncAt: Long?,
) {
    val ink = MaterialTheme.colorScheme.inverseSurface
    val cream = MaterialTheme.colorScheme.inverseOnSurface
    val shownNet = animatedMinor(kotlin.math.abs(netMinor))

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(ink),
    ) {
        // Brand shapes: peach and mint never blend into a gradient, they sit as
        // separate low-alpha forms behind the numbers.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Peach.copy(alpha = 0.20f),
                radius = size.width * 0.34f,
                center = Offset(size.width * 1.02f, -size.height * 0.08f),
            )
            drawCircle(
                color = Mint.copy(alpha = 0.14f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.02f, size.height * 1.02f),
            )
            drawCircle(
                color = Peach.copy(alpha = 0.16f),
                radius = size.width * 0.16f,
                center = Offset(size.width * 0.82f, size.height * 0.98f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
            )
            drawCircle(
                color = cream.copy(alpha = 0.10f),
                radius = size.width * 0.05f,
                center = Offset(size.width * 0.62f, size.height * 0.18f),
            )
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (netMinor >= 0) "You are owed in total" else "You owe in total",
                style = MaterialTheme.typography.bodyMedium,
                color = cream.copy(alpha = 0.82f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = formatInr(shownNet),
                    style = MaterialTheme.typography.displayLarge,
                    color = cream,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                HeroChip(
                    label = "Owed ₹${formatInr(owedToYouMinor)}",
                    tint = MaterialTheme.colorScheme.secondary,
                )
                HeroChip(
                    label = "You owe ₹${formatInr(youOweMinor)}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (lastSyncAt != null) {
                Text(
                    "Updated ${com.example.teramera.core.time.TimeFormat.relative(lastSyncAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cream.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SpendSummaryCard(
    thisMonthMinor: Long,
    thisMonthCount: Int,
    lastMonthMinor: Long,
    totalMinor: Long,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .animateContentSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        val shownThisMonth = animatedMinor(thisMonthMinor)
        val shownTotal = animatedMinor(totalMinor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "This month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "₹",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        formatInr(shownThisMonth),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "$thisMonthCount ${if (thisMonthCount == 1) "expense" else "expenses"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "All time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "₹${formatInr(shownTotal)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (lastMonthMinor > 0) {
                    Text(
                        "vs ₹${formatInr(lastMonthMinor)} last month",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun SegmentedTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
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
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
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
private fun BalanceCard(entries: List<BalanceEntry>, onOpen: (BalanceEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            BalanceRow(entry, index = entry.id.hashCode(), onClick = { onOpen(entry) })
            if (index != entries.lastIndex) HorizontalHairline()
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
private fun BalanceRow(entry: BalanceEntry, index: Int = 0, onClick: () -> Unit = {}) {
    val positive = entry.amountMinor > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
    ) {
        if (entry.isGroup) {
            GroupMascot(index = index, size = 44.dp)
        } else {
            Avatar(initials = entry.initials, isViolet = entry.isViolet, size = 44.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "₹${formatInr(kotlin.math.abs(entry.amountMinor))}",
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
    // Peach/Mint alternate; butter appears when the hash asks for a third tone.
    val tone = when {
        isViolet -> MaterialTheme.colorScheme.secondary
        initials.hashCode() % 3 == 0 -> com.example.teramera.ui.theme.Butter
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tone),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelMedium,
            color = Ink900,
        )
    }
}

@Composable
private fun animatedMinor(target: Long): Long {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(650, easing = FastOutSlowInEasing),
        )
    }
    return value.value.toLong()
}

@Composable
private fun ProfileAvatar(
    initials: String,
    photoUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color,
) {
    if (photoUrl.isNullOrBlank()) {
        Avatar(initials = initials, isViolet = false, size = size)
        return
    }
    SubcomposeAsyncImage(
        model = photoUrl,
        contentDescription = "Profile picture",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape),
        loading = { Avatar(initials = initials, isViolet = false, size = size) },
        error = { Avatar(initials = initials, isViolet = false, size = size) },
    )
}

// Minor units are paise. Whole rupees when exact, otherwise shown to the paisa
// (brand rule: no decimals when .00).
private val inrFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))

internal fun formatInr(minor: Long): String = synchronized(inrFormat) {
    val abs = kotlin.math.abs(minor)
    val sign = if (minor < 0) "-" else ""
    if (abs % 100 == 0L) {
        sign + inrFormat.format(abs / 100)
    } else {
        sign + inrFormat.format(abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }
}

@Composable
private fun ProfileDialog(
    state: HomeUiState,
    onSave: (name: String, upiId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(state.selfName) }
    var upi by remember { mutableStateOf(state.selfUpiId) }
    val upiValid = upi.isBlank() || Regex("^[\r\n\t\\w.\\-]{2,64}@[a-zA-Z]{2,32}$").matches(upi.trim())

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Profile & UPI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = upi,
                    onValueChange = { upi = it },
                    label = { Text("UPI ID (e.g. you@okhdfcbank)") },
                    supportingText = {
                        Text(
                            if (upiValid) "Friends use this to pay you via UPI"
                            else "UPI IDs look like name@bank",
                            color = if (upiValid) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    singleLine = true,
                    isError = !upiValid,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank() && upiValid,
                onClick = { onSave(name, upi) },
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Version + manual update check, opened from the avatar menu. */
@Composable
private fun VersionDialog(
    versionName: String,
    versionCode: Int,
    check: UpdateCheck,
    onCheck: () -> Unit,
    onInstall: (com.example.teramera.core.update.UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = check.available
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("teramera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Version $versionName", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Build $versionCode · Android 8.0+",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "teramera installs its own updates, so there's no app store to wait on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (check.status) {
                    UpdateCheck.Status.Checking -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Checking for updates…", style = MaterialTheme.typography.bodySmall)
                    }
                    UpdateCheck.Status.UpToDate -> Text(
                        "You're on the latest version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    UpdateCheck.Status.Available -> Text(
                        "Version ${available?.versionName} is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    UpdateCheck.Status.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (available != null) {
                androidx.compose.material3.TextButton(onClick = { onInstall(available) }) {
                    Text("Update now", fontWeight = FontWeight.Bold)
                }
            } else {
                androidx.compose.material3.TextButton(
                    enabled = check.status != UpdateCheck.Status.Checking,
                    onClick = onCheck,
                ) { Text("Check for updates", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

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
