package com.example.teramera.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.home.formatInr

@Composable
fun GroupsScreen(
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                "Groups",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { showCreate = true }, enabled = !state.creating) {
                Text("+ New group", style = MaterialTheme.typography.labelMedium)
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            items(state.groups, key = { it.id }) { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(64.dp)
                        .clickable { onOpenGroup(group.id) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondary),
                    ) {
                        androidx.compose.material3.Text(
                            group.initials,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                        Text(
                            group.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val positive = group.amountMinor >= 0
                    Text(
                        text = if (group.amountMinor == 0L) "settled" else "₹${formatInr(kotlin.math.abs(group.amountMinor))}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (positive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.groups.isEmpty()) {
                item {
                    Text(
                        "No groups yet. Create one to start splitting.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
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
private fun CreateGroupDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank() && !creating,
            ) { Text("Create") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
