package com.example.teramera.ui.settle

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.teramera.core.time.TimeFormat
import com.example.teramera.data.local.PaymentMethod
import com.example.teramera.data.repository.BalanceEntry
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
import com.example.teramera.ui.components.CheckIcon
import com.example.teramera.ui.components.EmptyState
import com.example.teramera.ui.components.InkHeader
import com.example.teramera.ui.components.MailIcon
import com.example.teramera.ui.home.Avatar
import com.example.teramera.ui.home.formatInr
import com.example.teramera.ui.theme.Ink900

@Composable
fun SettleScreen(
    viewModel: SettleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestState by viewModel.request.collectAsStateWithLifecycle()
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
            android.widget.Toast.makeText(
                context,
                "No UPI app found on this device — recording without launching payment",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            viewModel.save {}
        }
    }

    pendingUpiConfirm?.let { confirmDraft ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingUpiConfirm = null },
            shape = MaterialTheme.shapes.large,
            icon = {
                // Small visual hint that the money has already moved in the UPI app.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckIcon(tint = MaterialTheme.colorScheme.tertiary, size = 22.dp)
                }
            },
            title = { Text("Did the payment go through?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "You opened ${confirmDraft.person.name}'s UPI app for ₹${formatInr(confirmDraft.amountMinor)}, " +
                            "but most UPI apps don't tell teramera whether the payment succeeded.",
                    )
                    Text(
                        "Only tap 'Yes, record it' if money actually moved. If the app crashed or you backed out, tap 'No, cancel'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        val youOweTotal = state.balances.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }
        InkHeader {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Settle up",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f),
                )
                Baanto(state = BaantoState.Pleased, size = 40.dp, animate = true)
            }
            Text(
                "₹${formatInr(youOweTotal)} across ${state.balances.count { it.amountMinor < 0 }} " +
                    "${if (state.balances.count { it.amountMinor < 0 } == 1) "person" else "people"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp,
            ),
        ) {
            val owedYou = state.balances.filter { it.amountMinor > 0 }
            if (owedYou.isNotEmpty()) {
                item {
                    SectionLabel("Owes you")
                    BalanceCard(owedYou) { viewModel.start(it) }
                }
            }
            val youOwe = state.balances.filter { it.amountMinor < 0 }
            if (youOwe.isNotEmpty()) {
                item {
                    SectionLabel("You owe")
                    BalanceCard(youOwe) { viewModel.start(it) }
                }
            }
            if (state.balances.isEmpty()) {
                item {
                    EmptyState(
                        title = "Everyone's square",
                        message = "Nothing to settle right now. Add an expense to start something.",
                    )
                }
            }
        }
    }

    state.draft?.let { draft ->
        val upiWillLaunch = draft.method == PaymentMethod.UPI &&
            !draft.person.upiId.isNullOrBlank() &&
            draft.person.amountMinor < 0
        SettleSheet(
            draft = draft,
            saving = state.saving,
            saved = state.saved,
            error = state.error,
            upiWillLaunch = upiWillLaunch,
            onFull = viewModel::setFull,
            onHalf = viewModel::setHalf,
            onStartCustom = viewModel::startCustom,
            onCustomKey = viewModel::onKey,
            onCustomBackspace = viewModel::onBackspace,
            onMethod = viewModel::setMethod,
            onSave = { state.draft?.let { onSave(it) } },
            request = requestState,
            onRequest = viewModel::requestSettle,
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
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun BalanceCard(entries: List<BalanceEntry>, onOpen: (BalanceEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            SettleRow(entry, onClick = { onOpen(entry) })
            if (index != entries.lastIndex) Hairline()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    draft: SettleDraft,
    saving: Boolean,
    saved: Boolean,
    error: String?,
    upiWillLaunch: Boolean,
    onFull: () -> Unit,
    onHalf: () -> Unit,
    onStartCustom: () -> Unit,
    onCustomKey: (Char) -> Unit,
    onCustomBackspace: () -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onSave: () -> Unit,
    request: SettleRequestState,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
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

                if (draft.person.amountMinor > 0) {
                    if (!draft.person.id.startsWith("dummy:")) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onRequest,
                            enabled = !request.sending && draft.amountMinor > 0,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            MailIcon(tint = MaterialTheme.colorScheme.onSurface, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (request.sending) "Sending request…" else "Send settle request",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            "They'll get an email with a UPI link for ₹${formatInr(draft.amountMinor)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        request.message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        request.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            "Placeholder member — they can claim this balance after they join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Button(
                    onClick = onSave,
                    enabled = !saving && draft.amountMinor > 0,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(56.dp),
                ) {
                    Text(
                        if (upiWillLaunch) "Open UPI app & pay · ₹${formatInr(draft.amountMinor)}"
                        else "Record payment · ₹${formatInr(draft.amountMinor)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
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
    val border =
        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeypadRow(onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(onClick = { onKey(key.first()) }, contentDescription = key) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            KeypadKey(onClick = { onKey('0') }, contentDescription = "0") {
                Text("0", style = MaterialTheme.typography.titleMedium)
            }
            KeypadKey(onClick = onBackspace, contentDescription = "Backspace") {
                com.example.teramera.ui.components.BackspaceIcon(
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = 22.dp,
                )
            }
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
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
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.5.dp, border, MaterialTheme.shapes.medium)
            .clickable { onSelect(method) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(method.label(), style = MaterialTheme.typography.titleMedium)
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
    val recordedAt = remember(draft) { System.currentTimeMillis() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.65f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "settleSuccess",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale),
        ) {
            Baanto(state = BaantoState.Pleased, size = 72.dp, withCoin = false)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                CheckIcon(tint = MaterialTheme.colorScheme.tertiary, size = 38.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Settled with ${draft.person.name}",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "₹${formatInr(draft.amountMinor)}",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "Recorded ${TimeFormat.dayTime(recordedAt)} via ${draft.method.name.lowercase().replaceFirstChar { it.uppercase() }}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "${draft.person.name} will see this in their activity, and both balances update everywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Button(
            onClick = onDone,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(54.dp),
        ) {
            Text("Back to settle", style = MaterialTheme.typography.labelLarge)
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
    )
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
