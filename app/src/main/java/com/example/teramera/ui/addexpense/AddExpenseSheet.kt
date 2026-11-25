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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Add expense is a three-step form, so the sheet is deliberately *not*
 * drag-dismissible: a scroll or a stray swipe must never throw a half-filled
 * draft away. The close button, the scrim and system Back (which steps
 * backwards first) are the only ways out.
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

    // Fresh draft every time the sheet opens; then lock the group (if any) and
    // load its members. Re-fires when server mode flips on — the first pass can
    // land before the signed-in state + synced groups arrive.
    LaunchedEffect(Unit) { viewModel.prepare() }
    LaunchedEffect(fixedGroupId, state.serverMode, state.selfServerId) {
        if (fixedGroupId != null) {
            viewModel.setGroup(fixedGroupId)
        } else {
            viewModel.ensureServerDefaults()
        }
    }

    // Back walks the form backwards instead of discarding it; on step 1 the
    // sheet's own handler closes it as usual.
    BackHandler(enabled = draft.step > 1) { viewModel.previousStep() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        // The header draws behind the status bar itself, so only the bottom
        // inset (navigation bar or keyboard) belongs to the sheet. Applying
        // both there and here used to double-pad the bottom on phones with a
        // keyboard open.
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            // Landscape and very short screens get a slimmer header so the step
            // below keeps usable height.
            val compact = maxHeight < 520.dp
            Column(modifier = Modifier.fillMaxSize()) {
                AddExpenseHeader(
                    step = draft.step,
                    draft = draft,
                    state = state,
                    fixedGroupId = fixedGroupId,
                    compact = compact,
                    onClose = onDismiss,
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
    }
}
