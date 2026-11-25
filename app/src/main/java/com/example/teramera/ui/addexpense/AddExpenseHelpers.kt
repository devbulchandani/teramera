package com.example.teramera.ui.addexpense

import com.example.teramera.core.network.MemberDto
import com.example.teramera.data.repository.SplitType

/**
 * Small, pure helpers shared by the add-expense steps. Kept out of the
 * composables so the layout files stay about layout only.
 */

internal fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2)
        .map { it.first().uppercaseChar() }.joinToString("")

internal fun nameOf(state: AddExpenseUiState, userId: String): String = when {
    userId == AddExpenseViewModel.SELF || userId == state.selfServerId -> "You"
    else -> state.serverPeople.firstOrNull { it.id == userId }?.name?.ifBlank { "Member" }
        ?: state.friends.firstOrNull { it.id == userId }?.name
        ?: state.self?.takeIf { it.id == userId }?.name
        ?: userId
}

internal fun memberLabel(member: MemberDto): String =
    if (member.isSelf) "You" else member.name.ifBlank { "Member" }

internal fun splitLabel(type: SplitType): String = when (type) {
    SplitType.EQUAL -> "Equal"
    SplitType.EXACT -> "Exact"
    SplitType.PERCENT -> "Percent"
    SplitType.SHARES -> "Shares"
}

/** One line that explains the selected mode, instead of four repeated descriptions. */
internal fun splitHint(type: SplitType): String = when (type) {
    SplitType.EQUAL -> "Everyone in the split pays the same amount."
    SplitType.EXACT -> "Type each person's exact amount — it must add up to the total."
    SplitType.PERCENT -> "Give each person a percentage — it must add up to 100."
    SplitType.SHARES -> "Weight each person's portion; shares are relative."
}

/**
 * When exactly one person is included and someone else paid, the expense is a
 * one-to-one debt rather than a split. Returns a human sentence, or null.
 */
internal fun debtLabel(state: AddExpenseUiState, includedCount: Int): String? {
    if (includedCount != 1) return null
    val debtor = state.serverPeople.firstOrNull { it.id in state.draft.includedServer } ?: return null
    val payerId = state.payerIds.firstOrNull() ?: return null
    if (debtor.id == payerId) return null
    val payer = if (payerId == state.selfId) "you" else nameOf(state, payerId)
    return "${memberLabel(debtor)} owes $payer the full amount"
}

internal fun sharesValid(state: AddExpenseUiState, viewModel: AddExpenseViewModel): Boolean =
    state.draft.included.isNotEmpty() &&
        state.draft.amountMinor > 0 &&
        viewModel.previewShares(state) != null

internal fun serverSharesValid(state: AddExpenseUiState, viewModel: AddExpenseViewModel): Boolean =
    state.draft.includedServer.isNotEmpty() &&
        state.draft.amountMinor > 0 &&
        viewModel.serverShares(state) != null
