package com.example.teramera.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.repository.initials
import com.example.teramera.core.time.TimeFormat
import com.example.teramera.ui.components.ArrowBackIcon
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.HeaderIconButton
import com.example.teramera.ui.components.InkHeader
import com.example.teramera.ui.components.GroupMascot
import com.example.teramera.ui.components.MoreIcon
import com.example.teramera.ui.components.PlusIcon
import com.example.teramera.ui.components.ShareIcon
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Ink900
import com.example.teramera.ui.theme.Paper000
import com.example.teramera.ui.theme.Paper100

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onAddExpense: (String) -> Unit = {},
    onShareGroup: (String, String) -> Unit = { _, _ -> },
    showClaimPrompt: Boolean = false,
    onClaimHandled: () -> Unit = {},
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.detail.collectAsStateWithLifecycle()
    val addMemberState by viewModel.addMemberState.collectAsStateWithLifecycle()
    val expenseEditState by viewModel.expenseEditState.collectAsStateWithLifecycle()
    val settleState by viewModel.settle.collectAsStateWithLifecycle()
    val manageState by viewModel.manage.collectAsStateWithLifecycle()
    val claimState by viewModel.claim.collectAsStateWithLifecycle()
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var claimDismissed by remember { mutableStateOf(false) }
    var slowLoading by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.loading) {
        if (uiState.loading) {
            kotlinx.coroutines.delay(8_000)
            slowLoading = true
        } else {
            slowLoading = false
        }
    }

    // loading / error / empty states instead of a blank screen
    if (uiState.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Baanto(state = BaantoState.Thinking, size = 88.dp, animate = true)
            Spacer(Modifier.height(12.dp))
            Text("Loading group…", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (slowLoading) {
                Text(
                    "Still loading. Your connection or local cache may be stuck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    TextButton(onClick = onBack) { Text("Back to groups") }
                }
            }
        }
        return
    }
    if (uiState.error != null && uiState.detail == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Baanto(state = BaantoState.Sorry, size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                uiState.error ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::refresh,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.height(52.dp),
            ) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onBack) { Text("Back to groups") }
        }
        return
    }
    val d = uiState.detail ?: return

    LaunchedEffect(showClaimPrompt, d.dummyMembers) {
        if (showClaimPrompt && d.dummyMembers.isEmpty()) onClaimHandled()
    }

    if (showClaimPrompt && !claimDismissed && d.dummyMembers.isNotEmpty()) {
        ClaimDummyDialog(
            dummies = d.dummyMembers,
            busy = claimState.busy,
            error = claimState.error,
            onClaim = { id ->
                viewModel.claimDummy(id) {
                    claimDismissed = true
                    onClaimHandled()
                }
            },
            onSkip = {
                claimDismissed = true
                onClaimHandled()
            },
        )
    }

    if (addMemberState.visible) {
        AddMemberDialog(
            state = addMemberState,
            onFind = viewModel::findFriend,
            onConfirm = viewModel::confirmAddMember,
            onInviteByEmail = viewModel::inviteByEmail,
            onAddDummy = viewModel::addDummyMember,
            onDismiss = viewModel::dismissAddMember,
        )
    }

    if (expenseEditState.visible) {
        EditExpenseDialog(
            state = expenseEditState,
            onSave = viewModel::saveExpense,
            onDelete = viewModel::deleteExpense,
            onDismiss = viewModel::dismissEditExpense,
        )
    }

    if (settleState.visible) {
        GroupSettleDialog(
            state = settleState,
            detail = d,
            onPick = viewModel::startSettle,
            onMethod = viewModel::setSettleMethod,
            onConfirm = viewModel::confirmSettle,
            onDismiss = viewModel::dismissSettle,
        )
    }

    if (renameOpen) {
        RenameGroupDialog(
            currentName = d.groupName,
            busy = manageState.busy,
            error = manageState.error,
            onDismiss = {
                renameOpen = false
                viewModel.clearManageError()
            },
            onRename = { name -> viewModel.renameGroup(name) { renameOpen = false } },
        )
    }

    if (deleteOpen) {
        DeleteGroupDialog(
            groupName = d.groupName,
            busy = manageState.busy,
            error = manageState.error,
            onDismiss = {
                deleteOpen = false
                viewModel.clearManageError()
            },
            onDelete = {
                viewModel.deleteGroup {
                    deleteOpen = false
                    onBack()
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GroupHeader(
                    groupName = d.groupName,
                    memberInitials = d.memberInitials,
                    totalSpentMinor = d.totalSpentMinor,
                    onBack = onBack,
                    onAddMember = viewModel::showAddMember,
                    onShare = { onShareGroup(d.groupName, d.groupId) },
                    canManage = d.isOwner,
                    onRename = { renameOpen = true },
                    onDelete = { deleteOpen = true },
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 18.dp, bottom = 130.dp,
                    ),
                ) {
                    item { SimplifiedDebtsCard(d) }
                    if (d.members.isNotEmpty()) {
                        item { SectionLabel("Members") }
                        item { MembersCard(d.members) }
                    }
                    if (d.expenses.isNotEmpty()) {
                        item { SectionLabel("Expenses") }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp),
                            ) {
                                d.expenses.forEachIndexed { index, line ->
                                    ExpenseRow(line, onClick = { viewModel.showEditExpense(line) })
                                    if (index != d.expenses.lastIndex) Hairline()
                                }
                            }
                        }
                    }
                    if (d.expenses.isEmpty()) {
                        item {
                            Text(
                                "No expenses yet. Add the first one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                }
            }

            // Dual action bar: ghost + peach, pills, no global nav here.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = viewModel::openSettlePicker,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                ) { Text("Settle up", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { onAddExpense(d.groupId) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                ) { Text("Add expense", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    groupName: String,
    memberInitials: List<Pair<String, Boolean>>,
    totalSpentMinor: Long,
    onBack: () -> Unit,
    onAddMember: () -> Unit,
    onShare: () -> Unit,
    canManage: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkHeader {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(contentDescription = "Back", onClick = onBack) {
                ArrowBackIcon(tint = Paper100, size = 20.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(contentDescription = "Share group invite", onClick = onShare) {
                    ShareIcon(tint = Paper100, size = 20.dp)
                }
                HeaderIconButton(contentDescription = "Add member", onClick = onAddMember) {
                    PlusIcon(tint = Paper100, size = 20.dp)
                }
                if (canManage) {
                    Box {
                        HeaderIconButton(
                            contentDescription = "Manage group",
                            onClick = { menuOpen = true },
                        ) {
                            MoreIcon(tint = Paper100, size = 20.dp)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename group") },
                                onClick = {
                                    menuOpen = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete group", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    groupName,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${memberInitials.size} members · ₹${formatInr(totalSpentMinor)} spent",
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Baanto(state = BaantoState.Idle, size = 56.dp)
        }
    }
}

@Composable
private fun SimplifiedDebtsCard(d: com.example.teramera.data.repository.GroupDetail) {
    val owedToYou = d.simplifiedDebts.filter { it.toUserId == d.selfUserId }.sumOf { it.amountMinor }
    val youOwe = d.simplifiedDebts.filter { it.fromUserId == d.selfUserId }.sumOf { it.amountMinor }
    val net = owedToYou - youOwe
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primary)
            .padding(18.dp),
    ) {
        Text(
            when {
                net > 0 -> "You are owed"
                net < 0 -> "You owe"
                else -> "All settled up"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
        )
        Text(
            "₹${formatInr(kotlin.math.abs(net))}",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            "₹${formatInr(d.totalSpentMinor)} spent · ${d.expenses.size} expenses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            modifier = Modifier.padding(top = 4.dp, bottom = if (d.simplifiedDebts.isEmpty()) 0.dp else 6.dp),
        )
        if (d.simplifiedDebts.isNotEmpty()) {
            Text(
                if (d.worstCasePayments > d.simplifiedDebts.size) {
                    "${d.simplifiedDebts.size} payments instead of ${d.worstCasePayments}"
                } else {
                    "${d.simplifiedDebts.size} payments"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        d.simplifiedDebts.forEach { transfer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(transfer.fromName),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${shortName(transfer.fromName)} pays ${shortName(transfer.toName)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "₹${formatInr(transfer.amountMinor)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun MembersCard(members: List<com.example.teramera.data.repository.GroupMember>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        members.forEachIndexed { index, member ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                if (member.isDummy) {
                    GroupMascot(index = member.id.hashCode(), size = 40.dp)
                } else {
                    Avatar(
                        initials = initialsOf(member.name),
                        isViolet = member.id.hashCode() % 2 == 0,
                        size = 40.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (member.isSelf) "${member.name} (you)" else member.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        (if (member.isDummy) "Placeholder" else "Member") +
                            " · ₹${formatInr(member.paidMinor)} spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (member.isDummy) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "PLACEHOLDER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (index != members.lastIndex) Hairline()
        }
    }
}

@Composable
private fun ExpenseRow(
    line: com.example.teramera.data.repository.ExpenseLine,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(line.title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "paid by ${line.payerName}${if (line.payerCount > 1) " +${line.payerCount - 1}" else ""} · " +
                    "${line.participantCount} ${if (line.participantCount == 1) "person" else "people"} · ${TimeFormat.dayTime(line.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("₹${formatInr(line.amountMinor)}", style = MaterialTheme.typography.titleMedium)
            Text(
                "your share ₹${formatInr(line.myShareMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shortName(fullName: String): String =
    fullName.split(" ").firstOrNull() ?: fullName

private fun initialsOf(text: String): String =
    text.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
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

@Composable
private fun AddMemberDialog(
    state: com.example.teramera.ui.groups.AddMemberState,
    onFind: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onInviteByEmail: (String) -> Unit,
    onAddDummy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var dummyName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Add member", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Friend's email") },
                    singleLine = true,
                    enabled = state.found == null,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.found != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initials(state.found!!.name.ifBlank { "?" }),
                                color = Ink900,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Found: " + state.found!!.name.ifBlank { state.found!!.phone ?: state.found!!.email ?: "user" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Ready to join this group",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.error != null -> Column {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // not on teramera yet + we have their email → send an invite email
                        if (state.error!!.startsWith("No teramera user with that email") &&
                            phone.contains("@")
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = { onInviteByEmail(phone.trim()) },
                                enabled = !state.searching,
                            ) { Text("Email them an invite link") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Not on teramera yet?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add them as a placeholder. When they join, they can claim this member and all past expenses move with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = dummyName,
                    onValueChange = { dummyName = it },
                    label = { Text("Their name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.dummyError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(
                    onClick = { onAddDummy(dummyName) },
                    enabled = dummyName.isNotBlank() && !state.dummyBusy && !state.searching,
                ) {
                    Text(if (state.dummyBusy) "Adding…" else "Add placeholder")
                }
            }
        },
        confirmButton = {
            if (state.found == null) {
                androidx.compose.material3.TextButton(
                    onClick = { onFind(phone.trim()) },
                    enabled = !state.searching && phone.trim().contains("@"),
                ) {
                    Text(if (state.searching) "Searching…" else "Find")
                }
            } else {
                androidx.compose.material3.TextButton(
                    onClick = { onConfirm(state.found!!.id) },
                    enabled = !state.searching,
                ) { Text("Add to group") }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GroupSettleDialog(
    state: GroupSettleState,
    detail: com.example.teramera.data.repository.GroupDetail,
    onPick: (com.example.teramera.data.repository.DebtTransfer) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transfer = state.transfer
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (transfer == null) "Settle up" else "Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (transfer == null) {
                    if (detail.simplifiedDebts.isEmpty()) {
                        Text(
                            "Everyone's square in ${detail.groupName}.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        detail.simplifiedDebts.forEach { debt ->
                            val involved = detail.selfUserId == debt.fromUserId ||
                                detail.selfUserId == debt.toUserId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${shortName(debt.fromName)} → ${shortName(debt.toName)}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "₹${formatInr(debt.amountMinor)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.TextButton(
                                    enabled = involved && !state.busy,
                                    onClick = { onPick(debt) },
                                ) { Text(if (involved) "Settle" else "Not you") }
                            }
                        }
                    }
                } else {
                    Text(
                        "${shortName(transfer.fromName)} pays ${shortName(transfer.toName)} ₹${formatInr(transfer.amountMinor)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "How was it paid?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.entries.forEach { method ->
                            val selected = state.method == method
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onMethod(method) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                Text(methodLabel(method), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (transfer != null) {
                androidx.compose.material3.TextButton(enabled = !state.busy, onClick = onConfirm) {
                    Text(
                        if (state.busy) "Recording…" else "Record payment",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun methodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.UPI -> "UPI"
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.BANK -> "Bank"
}

@Composable
private fun ClaimDummyDialog(
    dummies: List<com.example.teramera.data.repository.DummyMember>,
    busy: Boolean,
    error: String?,
    onClaim: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onSkip() },
        shape = MaterialTheme.shapes.large,
        title = { Text("Who are you in this group?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "This group was set up before you joined. Pick your placeholder so past expenses count for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                dummies.forEach { dummy ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (selected == dummy.id) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selected = dummy.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = selected == dummy.id,
                            onClick = { selected = dummy.id },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(dummy.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && !busy,
                onClick = { selected?.let(onClaim) },
            ) { Text(if (busy) "Claiming…" else "That's me", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onSkip) { Text("I'm new here") }
        },
    )
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Rename group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = { onRename(name) },
            ) { Text(if (busy) "Saving…" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteGroupDialog(
    groupName: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Delete group?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "“$groupName” and all of its expenses will be removed for everyone. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onDelete) {
                Text(
                    if (busy) "Deleting…" else "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditExpenseDialog(
    state: GroupDetailViewModel.ExpenseEditState,
    onSave: (title: String, amountMinor: Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val line = state.line
    var title by androidx.compose.runtime.remember(line) {
        androidx.compose.runtime.mutableStateOf(line?.title.orEmpty())
    }
    var amountText by androidx.compose.runtime.remember(line) {
        androidx.compose.runtime.mutableStateOf(
            line?.amountMinor
                ?.let { java.math.BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }
                .orEmpty()
        )
    }
    var confirmDelete by androidx.compose.runtime.remember(line) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val amountMinor = parseRupeesToMinor(amountText)
    val valid = title.isNotBlank() && amountMinor != null && amountMinor > 0

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (confirmDelete) "Delete expense?" else "Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (confirmDelete) {
                    Text(
                        "“${line?.title}” will be removed and everyone's balances updated.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    androidx.compose.material3.OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Description") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = amountText,
                        onValueChange = { new ->
                            // digits plus at most one dot, two decimals
                            val filtered = new.filter { it.isDigit() || it == '.' }
                            val dot = filtered.indexOf('.')
                            amountText = if (dot >= 0 && filtered.length > dot + 3) {
                                filtered.take(dot + 3)
                            } else filtered.take(10)
                        },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!state.error.isNullOrBlank()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                androidx.compose.material3.TextButton(
                    enabled = !state.busy,
                    onClick = onDelete,
                ) { Text(if (state.busy) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            } else {
                androidx.compose.material3.TextButton(
                    enabled = valid && !state.busy,
                    onClick = { amountMinor?.let { onSave(title, it) } },
                ) { Text("Save", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (confirmDelete) {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (line?.canDelete == true) {
                        androidx.compose.material3.TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        },
    )
}

/** "12.34" -> 1234 minor units. Returns null for anything that isn't valid money. */
private fun parseRupeesToMinor(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }
    val fraction = parts.getOrNull(1).orEmpty()
    if (fraction.length > 2) return null
    if (whole.any { !it.isDigit() } || fraction.any { !it.isDigit() }) return null
    val rupees = whole.toLongOrNull() ?: return null
    val paise = fraction.padEnd(2, '0').toLongOrNull() ?: return null
    return rupees * 100 + paise
}
