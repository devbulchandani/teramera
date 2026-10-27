package com.example.teramera

import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.ExpenseShareEntity
import com.example.teramera.data.local.GroupEntity
import com.example.teramera.data.local.MembershipEntity
import com.example.teramera.data.local.UserEntity
import com.example.teramera.data.repository.ExpensesRepository
import com.example.teramera.data.repository.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiPayerLocalTest {

    private val self = UserEntity("u_dev", "Dev", isSelf = true)
    private val priya = UserEntity("u_priya", "Priya Sharma")

    @Test
    fun `pro rata allocation sums to the payment and follows the split`() {
        val shares = mapOf("u_dev" to 7_000L, "u_priya" to 3_000L)
        val parts = ExpensesRepository.allocateProRata(shares, bucket = 6_000L, totalWeight = 10_000L)

        assertEquals(6_000L, parts.values.sum())
        assertEquals(4_200L, parts["u_dev"])
        assertEquals(1_800L, parts["u_priya"])
    }

    @Test
    fun `pro rata allocation hands leftover paise out instead of losing them`() {
        val parts = ExpensesRepository.allocateProRata(
            weights = mapOf("a" to 1L, "b" to 1L, "c" to 1L),
            bucket = 100L,
            totalWeight = 3L,
        )

        assertEquals(100L, parts.values.sum())
        assertEquals(listOf(34L, 33L, 33L), parts.values.sortedDescending())
    }

    @Test
    fun `multi-payer sibling rows keep friend and group nets exact`() {
        val goa = GroupEntity("g_goa", "Goa Trip")
        // One ₹100 dinner split 50/50, but Dev fronted ₹60 and Priya ₹40.
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_priya" to priya),
            groups = listOf(goa),
            memberships = listOf(MembershipEntity("g_goa", "u_dev"), MembershipEntity("g_goa", "u_priya")),
            expenses = listOf(
                expense(1L, "g_goa", "u_dev", 6_000L, parent = "p1"),
                expense(2L, "g_goa", "u_priya", 4_000L, parent = "p1"),
            ),
            shares = listOf(
                // Dev's ₹60 covers ₹30 of each person's half
                share(1L, "u_dev", 3_000L), share(1L, "u_priya", 3_000L),
                // Priya's ₹40 covers the other ₹20 each
                share(2L, "u_dev", 2_000L), share(2L, "u_priya", 2_000L),
            ),
            settlements = emptyList(),
        ).toHomeData("u_dev")

        assertEquals(1_000L, data.friends.first { it.id == "u_priya" }.amountMinor)
        assertEquals(1_000L, data.groups.first { it.id == "g_goa" }.amountMinor)
    }

    @Test
    fun `paying more than your own share still nets out to zero when nobody owes`() {
        // Single-payer expense: Dev pays ₹100 for two people, Priya's half is ₹50.
        val data = LedgerSnapshot(
            usersById = mapOf("u_dev" to self, "u_priya" to priya),
            groups = emptyList(),
            memberships = emptyList(),
            expenses = listOf(expense(1L, null, "u_dev", 10_000L)),
            shares = listOf(share(1L, "u_dev", 5_000L), share(1L, "u_priya", 5_000L)),
            settlements = emptyList(),
        ).toHomeData("u_dev")

        assertEquals(5_000L, data.friends.first { it.id == "u_priya" }.amountMinor)
    }

    private fun expense(id: Long, groupId: String?, paidBy: String, amount: Long, parent: String? = null) =
        ExpenseEntity(
            id = id,
            groupId = groupId,
            paidByUserId = paidBy,
            title = "Dinner",
            amountMinor = amount,
            createdAt = id,
            parentId = parent,
        )

    private fun share(expenseId: Long, userId: String, minor: Long) =
        ExpenseShareEntity(expenseId, userId, minor)
}
