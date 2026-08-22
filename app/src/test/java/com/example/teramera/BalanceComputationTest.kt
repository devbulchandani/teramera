package com.example.teramera

import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.ExpenseShareEntity
import com.example.teramera.data.local.GroupEntity
import com.example.teramera.data.local.MembershipEntity
import com.example.teramera.data.local.SettlementEntity
import com.example.teramera.data.local.UserEntity
import com.example.teramera.data.repository.HomeRepository
import com.example.teramera.data.repository.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceComputationTest {

    private val self = UserEntity("u_dev", "Dev", isSelf = true)
    private val priya = UserEntity("u_priya", "Priya Sharma")
    private val kabir = UserEntity("u_kabir", "Kabir Shah")

    @Test
    fun `expense shares produce positive net for payer`() {
        // Dev paid 1000, Priya's share is 600 → Priya owes Dev 600
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_priya" to priya),
            groups = emptyList(),
            memberships = emptyList(),
            expenses = listOf(expense(1L, groupId = null, paidBy = "u_dev", amount = 1_000L)),
            shares = listOf(share(1L, "u_dev", 400L), share(1L, "u_priya", 600L)),
            settlements = emptyList(),
        ).toHomeData("u_dev")

        assertEquals(600L, data.friends.first { it.id == "u_priya" }.amountMinor)
    }

    @Test
    fun `settlement reduces the payer's remaining debt`() {
        // Priya owed 920 via an expense, then paid me back 420 → she owes 500
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_priya" to priya),
            groups = emptyList(),
            memberships = emptyList(),
            expenses = listOf(expense(1L, null, "u_dev", 1_840L)),
            shares = listOf(
                share(1L, "u_dev", 920L),
                share(1L, "u_priya", 920L),
            ),
            settlements = listOf(settlement(groupId = null, payer = "u_priya", paidTo = "u_dev", amount = 420L)),
        ).toHomeData("u_dev")

        assertEquals(500L, data.friends.first { it.id == "u_priya" }.amountMinor)
    }

    @Test
    fun `full settlement zeroes the balance`() {
        // I owed Kabir 920 (he paid), then I paid him 920 → square
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_kabir" to kabir),
            groups = emptyList(),
            memberships = emptyList(),
            expenses = listOf(expense(1L, null, "u_kabir", 1_840L)),
            shares = listOf(share(1L, "u_dev", 920L), share(1L, "u_kabir", 920L)),
            settlements = listOf(settlement(null, payer = "u_dev", paidTo = "u_kabir", amount = 920L)),
        ).toHomeData("u_dev")

        val kabirEntry = data.friends.first { it.id == "u_kabir" }
        assertEquals(0L, kabirEntry.amountMinor)
    }

    @Test
    fun `group net sums only in-group activity`() {
        val goa = GroupEntity("g_goa", "Goa Trip")
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_priya" to priya),
            groups = listOf(goa),
            memberships = listOf(MembershipEntity("g_goa", "u_dev"), MembershipEntity("g_goa", "u_priya")),
            expenses = listOf(
                expense(1L, "g_goa", "u_dev", 2_000L),   // I paid, all mine + priya's 1000
                expense(2L, null, "u_priya", 5_000L),    // outside group — must not affect group net
            ),
            shares = listOf(
                share(1L, "u_dev", 1_000L), share(1L, "u_priya", 1_000L),
                share(2L, "u_priya", 2_500L), share(2L, "u_dev", 2_500L),
            ),
            settlements = emptyList(),
        ).toHomeData("u_dev")

        assertEquals(1_000L, data.groups.first { it.id == "g_goa" }.amountMinor)
    }

    private fun expense(id: Long, groupId: String?, paidBy: String, amount: Long) =
        ExpenseEntity(id = id, groupId = groupId, paidByUserId = paidBy, title = "t", amountMinor = amount, createdAt = 0L)

    private fun share(expenseId: Long, userId: String, minor: Long) =
        ExpenseShareEntity(expenseId, userId, minor)

    private fun settlement(groupId: String?, payer: String, paidTo: String, amount: Long) =
        SettlementEntity(groupId = groupId, payerUserId = payer, paidToUserId = paidTo, amountMinor = amount, method = "UPI", createdAt = 0L)
}
