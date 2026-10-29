package com.example.teramera.ui.groups

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.EmptyState
import com.example.teramera.ui.components.HeaderIconButton
import com.example.teramera.ui.components.InkHeader
import com.example.teramera.ui.components.PlusIcon
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Butter
import com.example.teramera.ui.theme.Ink900
import com.example.teramera.ui.theme.Mint
import com.example.teramera.ui.theme.Paper100
import com.example.teramera.ui.theme.Peach

@Composable
fun GroupsScreen(
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val net = state.groups.sumOf { it.amountMinor }

    Column(modifier = Modifier.fillMaxSize()) {
        InkHeader {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Groups",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f),
                )
                Baanto(state = BaantoState.Pleased, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(
                    contentDescription = "New group",
                    onClick = { showCreate = true },
                    enabled = !state.creating,
                ) {
                    PlusIcon(tint = Paper100, size = 22.dp)
                }
            }
            Text(
                "${state.groups.size} active · ₹${formatInr(kotlin.math.abs(net))} net",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.groups, key = { _, group -> group.id }) { index, group ->
                GroupCard(group = group, index = index, onClick = { onOpenGroup(group.id) })
            }
            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        title = "No groups yet",
                        message = "Create a group and invite the people you split with.",
                        actionLabel = "New group",
                        onAction = { showCreate = true },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            creating = state.creating,
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createGroup(name) { groupId ->
                    showCreate = false
                    onOpenGroup(groupId)
                }
            },
        )
    }
}

@Composable
private fun GroupCard(group: BalanceEntry, index: Int, onClick: () -> Unit) {
    val tone = when (index % 3) {
        0 -> Peach
        1 -> Mint
        else -> Butter
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(tone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                group.initials,
                color = Ink900,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                group.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = if (group.amountMinor == 0L) "settled" else "₹${formatInr(kotlin.math.abs(group.amountMinor))}",
            style = MaterialTheme.typography.titleMedium,
            color = if (group.amountMinor >= 0) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CreateGroupDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("New group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank() && !creating,
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
