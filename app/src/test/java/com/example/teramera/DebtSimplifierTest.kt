package com.example.teramera

import com.example.teramera.data.repository.simplifyDebts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtSimplifierTest {

    private val names = mapOf("a" to "Alice", "b" to "Bob", "c" to "Carol", "d" to "Dev")

    private fun simplify(net: Map<String, Long>) = simplifyDebts(net) { names[it] ?: it }

    @Test
    fun `single debtor single creditor`() {
        val transfers = simplify(mapOf("a" to -500L, "b" to 500L))
        assertEquals(1, transfers.size)
        assertEquals("a", transfers[0].fromUserId)
        assertEquals("b", transfers[0].toUserId)
        assertEquals(500L, transfers[0].amountMinor)
    }

    @Test
    fun `transfers preserve every member's net`() {
        val net = mapOf("a" to -3_000L, "b" to -1_000L, "c" to 2_500L, "d" to 1_500L)
        val transfers = simplify(net)

        // net[i] > 0 means i is owed; after simplification inflows − outflows must equal net
        val recomputed = net.keys.associateWith { id ->
            transfers.filter { it.toUserId == id }.sumOf { it.amountMinor } -
                transfers.filter { it.fromUserId == id }.sumOf { it.amountMinor }
        }
        recomputed.forEach { (id, value) ->
            assertEquals(net[id]!!, value)
        }
    }

    @Test
    fun `transfer count stays under worst case`() {
        val net = mapOf(
            "a" to -300L, "b" to -200L, "c" to -100L,
            "d" to 250L, "e" to 350L,
        )
        val transfers = simplify(net)
        assertTrue(transfers.size <= 3 + 2 - 1)
        assertEquals(600L, transfers.sumOf { it.amountMinor })
    }

    @Test
    fun `all square produces no transfers`() {
        val net = mapOf("a" to 0L, "b" to 0L, "c" to 0L)
        assertTrue(simplify(net).isEmpty())
    }
}
