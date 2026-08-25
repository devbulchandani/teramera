package com.example.teramera.ui.settle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr

@Composable
fun SettleScreen(
    viewModel: SettleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // UPI-first settle: open the UPI app, confirm the payment, only then record it
    var launchedDraft by remember { mutableStateOf<SettleDraft?>(null) }
    var pendingUpiConfirm by remember { mutableStateOf<SettleDraft?>(null) }

    val upiLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val draft = launchedDraft
        launchedDraft = null
        if (draft == null) return@rememberLauncherForActivityResult
        val status = result.data?.getStringExtra("status") ?: result.data?.data?.getQueryParameter("Status")
        val responseCode = result.data?.getStringExtra("responseCode") ?: result.data?.data?.getQueryParameter("responseCode")
        val paidInUpiApp = status.equals("SUCCESS", ignoreCase = true) || responseCode == "00" || responseCode == "0"
        if (paidInUpiApp) {
            viewModel.save {}
        } else {
            // most UPI apps return an empty result even on success — ask
            pendingUpiConfirm = draft
        }
    }

    fun launchUpi(draft: SettleDraft) {
        val upi = draft.person.upiId ?: return
        val rupees = java.math.BigDecimal(draft.amountMinor).movePointLeft(2).toPlainString()
        val uri = android.net.Uri.parse("upi://pay").buildUpon()
            .appendQueryParameter("pa", upi)
            .appendQueryParameter("pn", draft.person.name)
            .appendQueryParameter("am", rupees)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "teramera")
            .build()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        launchedDraft = draft
        try {
            upiLauncher.launch(intent)
        } catch (_: Exception) {
            launchedDraft = null
            android.widget.Toast.makeText(context, "No UPI app found — recording without payment", android.widget.Toast.LENGTH_LONG).show()
            viewModel.save {}
        }
    }

    pendingUpiConfirm?.let { confirmDraft ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingUpiConfirm = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            title = { Text("Payment done?") },
            text = {
                Text("Did you complete ₹${formatInr(confirmDraft.amountMinor)} to ${confirmDraft.person.name} in your UPI app?")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingUpiConfirm = null
                    viewModel.save {}
                }) { Text("Yes, record it", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingUpiConfirm = null
                    viewModel.clear()
                }) { Text("No, cancel") }
            },
        )
    }

    fun onSave(draft: SettleDraft) {
        val upiApplies = draft.method == PaymentMethod.UPI &&
            !draft.person.upiId.isNullOrBlank() &&
            draft.person.amountMinor < 0 // I owe them → the intent pays from this phone
        if (upiApplies) launchUpi(draft) else viewModel.save {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Settle up",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            val owedYou = state.balances.filter { it.amountMinor > 0 }
            if (owedYou.isNotEmpty()) {
                item { SectionLabel("Owes you") }
                items(owedYou, key = { "${it.id}-pos" }) { entry ->
                    SettleRow(entry, onClick = { viewModel.start(entry) })
                    Hairline()
                }
            }
            val youOwe = state.balances.filter { it.amountMinor < 0 }
            if (youOwe.isNotEmpty()) {
                item { SectionLabel("You owe") }
                items(youOwe, key = { "${it.id}-neg" }) { entry ->
                    SettleRow(entry, onClick = { viewModel.start(entry) })
                    Hairline()
                }
            }
            if (state.balances.isEmpty()) {
                item {
                    Text(
                        "Everyone's square. Nothing to settle.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }

    state.draft?.let { draft ->
        SettleSheet(
            draft = draft,
            saving = state.saving,
            saved = state.saved,
            onFull = viewModel::setFull,
            onHalf = viewModel::setHalf,
            onStartCustom = viewModel::startCustom,
            onCustomKey = viewModel::onKey,
            onCustomBackspace = viewModel::onBackspace,
            onMethod = viewModel::setMethod,
            onSave = { state.draft?.let { onSave(it) } },
            onDismiss = viewModel::clear,
        )
    }
}

@Composable
private fun SettleRow(entry: BalanceEntry, onClick: () -> Unit) {
    val positive = entry.amountMinor > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clickable(onClick = onClick),
    ) {
        Avatar(initials = entry.initials, isViolet = entry.isViolet, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            Text(
                text = if (positive) "owes you" else "you owe",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "₹${formatInr(kotlin.math.abs(entry.amountMinor))}",
            style = MaterialTheme.typography.titleMedium,
            color = if (positive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    draft: SettleDraft,
    saving: Boolean,
    saved: Boolean,
    onFull: () -> Unit,
    onHalf: () -> Unit,
    onStartCustom: () -> Unit,
    onCustomKey: (Char) -> Unit,
    onCustomBackspace: () -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            if (!saved) {
                SheetHeader(name = draft.person.name)

                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Text("₹", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatInr(draft.amountMinor),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "of ₹${formatInr(draft.fullAmount)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                    QuickChip("Full", !draft.customMode && draft.isFull, onFull)
                    QuickChip("Half", !draft.customMode && draft.amountMinor == draft.fullAmount / 2, onHalf)
                    QuickChip("Custom", draft.customMode, onStartCustom)
                }

                if (draft.customMode) {
                    KeypadRow(onKey = onCustomKey, onBackspace = onCustomBackspace)
                }

                SectionLabel("How did you pay?")
                PaymentMethod.entries.forEach { method ->
                    MethodRow(method = method, selected = draft.method == method, onSelect = onMethod)
                }

                Button(
                    onClick = onSave,
                    enabled = !saving && draft.amountMinor > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(56.dp),
                ) {
                    Text("Record payment · ₹${formatInr(draft.amountMinor)}", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                DoneStage(draft = draft, onDone = onDismiss)
            }
        }
    }
}

@Composable
private fun SheetHeader(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Avatar(initials = initialsOf(name), isViolet = name.hashCode() % 2 == 0, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Text("Settling with $name", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun KeypadRow(onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onKey(key.first()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onKey('0') },
                contentAlignment = Alignment.Center,
            ) { Text("0", style = MaterialTheme.typography.titleMedium) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) { Text("⌫", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun MethodRow(method: PaymentMethod, selected: Boolean, onSelect: (PaymentMethod) -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val border =
        if (selected) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable { onSelect(method) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(method.label(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            Text(
                when (method) {
                    PaymentMethod.UPI -> "GPay, PhonePe, Paytm or any UPI app"
                    PaymentMethod.CASH -> "Handed over in person"
                    PaymentMethod.BANK -> "NEFT, IMPS or account transfer"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
    }
}

private fun PaymentMethod.label() = when (this) {
    PaymentMethod.UPI -> "UPI"
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.BANK -> "Bank transfer"
}

@Composable
private fun DoneStage(draft: SettleDraft, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Settled with ${draft.person.name}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Recorded ₹${formatInr(draft.amountMinor)} via ${draft.method.name.lowercase().replaceFirstChar { it.uppercase() }}.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp),
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Hairline() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
