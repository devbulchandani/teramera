package com.example.teramera.data.repository

import com.example.teramera.data.local.TerameraDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class ExpenseLine(
    val id: String,
    val title: String,
    val payerName: String,
    val payerCount: Int = 1,
    val amountMinor: Long,
    val myShareMinor: Long,
    val participantCount: Int,
    val createdAt: Long,
)

data class DummyMember(
    val id: String,
    val name: String,
)

data class GroupMember(
    val id: String,
    val name: String,
    val isSelf: Boolean,
    val isDummy: Boolean,
)

data class DebtTransfer(
    val fromUserId: String,
    val fromName: String,
    val toUserId: String,
    val toName: String,
    val amountMinor: Long,
)

data class GroupDetail(
    val groupId: String,
    val groupName: String,
    val selfUserId: String,
    val memberInitials: List<Pair<String, Boolean>>, // initials to isSelf
    val memberIds: List<String>,
    val totalSpentMinor: Long,
    val expenses: List<ExpenseLine>,
    val simplifiedDebts: List<DebtTransfer>,
    val worstCasePayments: Int,
    val isOwner: Boolean = false,
    val dummyMembers: List<DummyMember> = emptyList(),
    val members: List<GroupMember> = emptyList(),
)

/**
 * Per-group Room flows. Replaces the old approach of watching every expense
 * / share / membership across all groups and filtering inside the VM —
 * every group-detail recompute is now scoped to this single group, so it
 * runs in O(group size) not O(all my groups).
 */
@Singleton
class GroupDetailRepository @Inject constructor(
    private val dao: TerameraDao,
) {
    fun groupDetail(groupId: String, selfId: String = "u_dev"): Flow<GroupDetail?> =
        combine(
            dao.users(),
            dao.groups(),
            dao.membershipsForGroup(groupId),
            dao.expensesForGroup(groupId),
            dao.sharesForGroup(groupId),
        ) { users, groups, members, expenses, shares ->
            val group = groups.firstOrNull { it.id == groupId } ?: return@combine null
            val usersById = users.associateBy { it.id }
            val memberIds = members.map { it.userId }
            val sharesByExpense = shares.groupBy { it.expenseId }

            // multi-payer expenses are several rows sharing a parentId — show one line each
            val lines = expenses
                .groupBy { it.parentId ?: it.id.toString() }
                .map { (_, rows) ->
                    val first = rows.first()
                    val rowShares = rows.flatMap { sharesByExpense[it.id].orEmpty() }
                    ExpenseLine(
                        id = first.id.toString(),
                        title = first.title,
                        payerName = usersById[first.paidByUserId]?.name ?: "?",
                        payerCount = rows.map { it.paidByUserId }.distinct().size,
                        amountMinor = rows.sumOf { it.amountMinor },
                        myShareMinor = rowShares.filter { it.userId == selfId }.sumOf { it.amountMinor },
                        participantCount = rowShares.map { it.userId }.distinct().size,
                        createdAt = first.createdAt,
                    )
                }
                .sortedByDescending { it.createdAt }

            // net[m] = how much m has overpaid (positive, is owed) or underpaid (negative, owes)
            val net = memberIds.associateWith { 0L }.toMutableMap()
            for (expense in expenses) {
                net.merge(expense.paidByUserId, expense.amountMinor, Long::plus)
                for (share in sharesByExpense[expense.id].orEmpty()) {
                    net.merge(share.userId, -share.amountMinor, Long::plus)
                }
            }

            val nameOf = { id: String -> usersById[id]?.name ?: id }
            val transfers = simplifyDebts(net, nameOf)
            val worstCase = net.values.count { it > 0 } * net.values.count { it < 0 }

            GroupDetail(
                groupId = group.id,
                groupName = group.name,
                selfUserId = selfId,
                memberInitials = memberIds.map { id ->
                    initials(nameOf(id)) to (id == selfId)
                },
                memberIds = memberIds,
                totalSpentMinor = expenses.sumOf { it.amountMinor },
                expenses = lines,
                simplifiedDebts = transfers,
                worstCasePayments = worstCase,
            )
        }
}

internal fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")

/**
 * Greedy debt simplification: repeatedly match the largest debtor against the
 * largest creditor. Produces at most (debtors + creditors - 1) transfers and
 * preserves every member's net exactly.
 */
internal fun simplifyDebts(
    net: Map<String, Long>,
    nameOf: (String) -> String,
): List<DebtTransfer> {
    val creditors = net.filterValues { it > 0 }.toMutableMap()
    val debtors = net.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()

    val transfers = mutableListOf<DebtTransfer>()
    while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
        val (creditorId, credit) = creditors.maxByOrNull { it.value }!!
        val (debtorId, debt) = debtors.maxByOrNull { it.value }!!
        val amount = minOf(credit, debt)
        transfers.add(
            DebtTransfer(
                fromUserId = debtorId, fromName = nameOf(debtorId),
                toUserId = creditorId, toName = nameOf(creditorId),
                amountMinor = amount,
            )
        )
        if (credit - amount <= 0) creditors.remove(creditorId) else creditors[creditorId] = credit - amount
        if (debt - amount <= 0) debtors.remove(debtorId) else debtors[debtorId] = debt - amount
    }
    return transfers
}