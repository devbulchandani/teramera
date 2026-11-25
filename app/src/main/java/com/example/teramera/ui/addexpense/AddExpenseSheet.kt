package com.example.teramera.ui.addexpense

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Add expense is a three-step form, so the sheet is deliberately not
 * drag-dismissible, does not close on an outside tap, and only closes through
 * the X or Back once the draft has been confirmed for discard. A scroll or a
 * stray swipe must never throw a half-filled expense away.
 *
 * Layout is modular: the host owns the sheet, [AddExpenseHeader] the ink header,
 * and each step owns its own scroll container and pinned action bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    fixedGroupId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Config changes recompose the sheet; only a genuinely new open resets the draft.
    var prepared by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!prepared) {
            prepared = true
            viewModel.prepare()
        }
    }

    // Fresh draft every time the sheet opens; then lock the group (if any) and
    // load its members. Re-fires when server mode flips on — the first pass can
    // land before the signed-in state + synced groups arrive.
    LaunchedEffect(fixedGroupId, state.serverMode, state.selfServerId) {
        if (fixedGroupId != null) {
            viewModel.setGroup(fixedGroupId)
        } else {
            viewModel.ensureServerDefaults()
        }
    }

    val hasDraft = draft.amountMinor > 0 ||
        draft.title.isNotBlank() ||
        draft.included.isNotEmpty() ||
        draft.includedServer.isNotEmpty()
    val requestClose: () -> Unit = {
        if (hasDraft) confirmDiscard = true else onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
    ) {
        // Back must live inside the sheet's dialog composition: Material3 hosts
        // the sheet in its own window with its own back dispatcher, so a
        // handler registered outside it never fires. Composed here it is added
        // after Material3's own callback and wins while it is enabled.
        BackHandler {
            when {
                draft.step > 1 -> viewModel.previousStep()
                hasDraft -> confirmDiscard = true
                else -> onDismiss()
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            // Landscape, short screens and large font scales get a slimmer
            // header so the step below keeps usable height.
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val compact = maxHeight < 520.dp || fontScale >= 1.5f
            Column(modifier = Modifier.fillMaxSize()) {
                AddExpenseHeader(
                    step = draft.step,
                    draft = draft,
                    state = state,
                    fixedGroupId = fixedGroupId,
                    compact = compact,
                    onClose = requestClose,
                    onBack = if (draft.step > 1) viewModel::previousStep else null,
                    onSelectGroup = viewModel::setGroup,
                )
                AnimatedContent(
                    targetState = draft.step,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { direction * it / 5 } +
                            fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(220)) { -direction * it / 5 } +
                                fadeOut(tween(140)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "expenseStep",
                ) { step ->
                    when (step) {
                        1 -> AmountStep(state = state, viewModel = viewModel)
                        2 -> DetailsStep(state = state, viewModel = viewModel)
                        else -> ReviewStep(
                            state = state,
                            viewModel = viewModel,
                            onSaved = onDismiss,
                        )
                    }
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                shape = MaterialTheme.shapes.large,
                title = { Text("Discard this expense?") },
                text = { Text("Your unfinished expense will be lost. You can keep editing instead.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiscard = false
                            onDismiss()
                        },
                    ) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
                },
            )
        }
    }
}
