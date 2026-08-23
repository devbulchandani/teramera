package com.example.teramera.ui.groups

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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.ui.home.formatInr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onAddExpense: (String) -> Unit = {},
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val addMemberState by viewModel.addMemberState.collectAsStateWithLifecycle()
    val d = detail ?: return

    if (addMemberState.visible) {
        AddMemberDialog(
            state = addMemberState,
            onFind = viewModel::findFriend,
            onConfirm = viewModel::confirmAddMember,
            onInviteByEmail = viewModel::inviteByEmail,
            onDismiss = viewModel::dismissAddMember,
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
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
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 120.dp,
                    ),
                ) {
                    if (d.simplifiedDebts.isNotEmpty()) {
                        item { SimplifiedDebtsCard(d) }
                    }
                    items(d.expenses, key = { it.id }) { line ->
                        ExpenseRow(line)
                        Hairline()
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

            // Dual action bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { /* settle flow scoped to group comes later */ },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text("Settle up", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { onAddExpense(d.groupId) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
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
) {
    val violet = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.radialGradient(
                    listOf(violet, violet.copy(alpha = 0.75f), Color.Transparent),
                    radius = 900f,
                )
            )
            .background(violet)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onAddMember),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(groupName.take(2).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(groupName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${memberInitials.size} members · ₹${formatInr(totalSpentMinor)} spent",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            memberInitials.forEach { (initials, isSelf) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initials, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (isSelf) "You" else initials,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimplifiedDebtsCard(d: com.example.teramera.data.repository.GroupDetail) {
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "SIMPLIFY DEBTS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${d.simplifiedDebts.size} payments instead of ${d.worstCasePayments}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
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
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(transfer.fromName),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${shortName(transfer.fromName)} pays ${shortName(transfer.toName)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    modifier = Modifier.weight(1f),
                )
                Text("₹${formatInr(transfer.amountMinor)}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ExpenseRow(line: com.example.teramera.data.repository.ExpenseLine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
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
            Text(line.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            Text(
                "paid by ${line.payerName} · ${line.participantCount} people · ${dateLabel(line.createdAt)}",
                style = MaterialTheme.typography.bodyMedium,
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

private fun dateLabel(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = java.util.Calendar.getInstance()
    return when {
        sameDay(cal, today) -> "Today"
        sameDay(cal, java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
    }
}

private fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
    a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

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
    onDismiss: () -> Unit,
) {
    var phone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add member") },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (+91…) or email") },
                    singleLine = true,
                    enabled = state.found == null,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.found != null -> Text(
                        "Found: ${state.found!!.name.ifBlank { state.found!!.phone ?: state.found!!.email ?: "user" }}",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
            }
        },
        confirmButton = {
            if (state.found == null) {
                androidx.compose.material3.TextButton(
                    onClick = { onFind(phone.trim()) },
                    enabled = !state.searching && phone.trim().length >= 5,
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
