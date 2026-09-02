package com.example.teramera.data.repository

import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.ExpenseShareEntity
import com.example.teramera.data.local.GroupEntity
import com.example.teramera.data.local.MembershipEntity
import com.example.teramera.data.local.SettlementEntity
import com.example.teramera.data.local.TerameraDao
import com.example.teramera.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class BalanceEntry(
    val id: String,
    val name: String,
    val initials: String,
    val isViolet: Boolean,
    val subtitle: String,
    val amountMinor: Long, // paise; positive = they owe you
    val isGroup: Boolean = false,
    val upiId: String? = null,
)

data class HomeData(
    val friends: List<BalanceEntry>,
    val groups: List<BalanceEntry>,
)

internal data class LedgerSnapshot(
    val usersById: Map<String, UserEntity>,
    val groups: List<GroupEntity>,
    val memberships: List<MembershipEntity>,
    val expenses: List<ExpenseEntity>,
    val shares: List<ExpenseShareEntity>,
    val settlements: List<SettlementEntity> = emptyList(),
) {
    fun toHomeData(selfId: String): HomeData {
        // net[a] = how much a owes self (positive) or is owed by self (negative)
        val friendNet = mutableMapOf<String, Long>()
        val groupNet = mutableMapOf<String, Long>()

        // O(S+E) instead of O(S×E): one Map lookup per share instead of a list scan.
        val expenseById = expenses.associateBy { it.id }
        for (share in shares) {
            val expense = expenseById[share.expenseId] ?: continue
            when {
                expense.paidByUserId == selfId && share.userId != selfId -> {
                    friendNet.merge(share.userId, share.amountMinor, Long::plus)
                    expense.groupId?.let { groupNet.merge(it, share.amountMinor, Long::plus) }
                }
                expense.paidByUserId != selfId && share.userId == selfId -> {
                    friendNet.merge(expense.paidByUserId, -share.amountMinor, Long::plus)
                    expense.groupId?.let { groupNet.merge(it, -share.amountMinor, Long::plus) }
                }
            }
        }

        for (s in settlements) {
            when {
                // They paid me → whatever they owed shrinks
                s.payerUserId != selfId && s.paidToUserId == selfId ->
                    friendNet.merge(s.payerUserId, -s.amountMinor, Long::plus)
                // I paid them → my debt shrinks, so their net vs me rises
                s.payerUserId == selfId && s.paidToUserId != selfId ->
                    friendNet.merge(s.paidToUserId, s.amountMinor, Long::plus)
            }
        }

        val membersByGroup = memberships.groupBy({ it.groupId }, { it.userId })
        // O(G+E) for the subtitle instead of O(G×E) — count once per group.
        val expenseCountByGroup = expenses.groupBy { it.groupId }.mapValues { it.value.size }

        val friends = friendNet.mapNotNull { (userId, net) ->
            val user = usersById[userId] ?: return@mapNotNull null
            BalanceEntry(
                id = userId,
                name = user.name,
                initials = initials(user.name),
                isViolet = user.name.hashCode() % 2 == 0,
                subtitle = subtitleFor(net),
                amountMinor = net,
                isGroup = false,
            )
        }.sortedByDescending { it.amountMinor }

        val groupEntries = groups.mapNotNull { group ->
            if (selfId !in membersByGroup[group.id].orEmpty()) return@mapNotNull null
            val memberCount = membersByGroup[group.id]?.size ?: 0
            val net = groupNet[group.id] ?: 0L
            val count = expenseCountByGroup[group.id] ?: 0
            BalanceEntry(
                id = group.id,
                name = group.name,
                initials = initials(group.name),
                isViolet = true,
                subtitle = "$memberCount members · $count expenses",
                amountMinor = net,
                isGroup = true,
            )
        }.sortedByDescending { it.amountMinor }

        return HomeData(friends = friends, groups = groupEntries)
    }
}

internal fun subtitleFor(net: Long): String =
    if (net > 0) "owes you" else if (net < 0) "you owe" else "settled up"

@Singleton
class HomeRepository @Inject constructor(
    private val dao: TerameraDao,
) {
    fun homeData(selfId: String = "u_dev"): Flow<HomeData> {
        val core = combine(
            dao.users(), dao.groups(), dao.memberships(), dao.expenses(), dao.shares(),
        ) { users, groups, memberships, expenses, shares ->
            LedgerSnapshot(users.associateBy { it.id }, groups, memberships, expenses, shares)
        }
        return combine(core, dao.settlements()) { snapshot, settlements ->
            snapshot.copy(settlements = settlements).toHomeData(selfId)
        }
    }

    /** Server-synced snapshot; null until the first successful sync. */
    fun syncedHomeData(): Flow<HomeData?> =
        combine(dao.syncedBalances(), dao.syncedGroups()) { balances, groups ->
            if (balances.isEmpty() && groups.isEmpty()) {
                null
            } else {
                HomeData(
                    friends = balances.map { row ->
                        BalanceEntry(
                            id = row.userId,
                            name = row.name,
                            initials = initials(row.name),
                            isViolet = row.name.hashCode() % 2 == 0,
                            subtitle = subtitleFor(row.netMinor),
                            amountMinor = row.netMinor,
                            isGroup = false,
                            upiId = row.upiId.ifEmpty { null },
                        )
                    },
                    groups = groups.map { row ->
                        BalanceEntry(
                            id = row.id,
                            name = row.name,
                            initials = initials(row.name),
                            isViolet = true,
                            subtitle = "₹${row.totalSpentMinor / 100} spent",
                            amountMinor = row.netForMeMinor,
                            isGroup = true,
                        )
                    },
                )
            }
        }

    fun selfUser() = dao.selfUser()
}
