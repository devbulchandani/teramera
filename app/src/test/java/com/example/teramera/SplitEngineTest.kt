package com.example.teramera

import com.example.teramera.data.repository.ExpensesRepository
import com.example.teramera.data.repository.SplitInput
import com.example.teramera.data.repository.SplitResult
import com.example.teramera.data.repository.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitEngineTest {

    private fun split(
        type: SplitType,
        totalMinor: Long,
        participants: List<String>,
        rawValues: Map<String, Long> = emptyMap(),
    ) = ExpensesRepository.computeSplit(SplitInput(type, totalMinor, participants, rawValues))

    @Test
    fun `equal split with remainder distributes leftover paise`() {
        val result = split(SplitType.EQUAL, 100_001L, listOf("a", "b", "c"))
        val shares = (result as SplitResult.Ok).shares
        assertEquals(listOf(33_334L, 33_334L, 33_333L), shares.map { it.second })
        assertEquals(100_001L, shares.sumOf { it.second })
    }

    @Test
    fun `equal split exact division`() {
        val result = split(SplitType.EQUAL, 448_000L, listOf("a", "b", "c", "d", "e"))
        val shares = (result as SplitResult.Ok).shares
        assertTrue(shares.all { it.second == 89_600L })
        assertEquals(448_000L, shares.sumOf { it.second })
    }

    @Test
    fun `exact split fails when amounts do not sum to total`() {
        val result = split(SplitType.EXACT, 1_000L, listOf("a", "b"), mapOf("a" to 600L))
        assertTrue(result is SplitResult.Invalid)
    }

    @Test
    fun `exact split passes when sums match`() {
        val result = split(SplitType.EXACT, 1_000L, listOf("a", "b"), mapOf("a" to 600L, "b" to 400L))
        val shares = (result as SplitResult.Ok).shares
        assertEquals(1_000L, shares.sumOf { it.second })
    }

    @Test
    fun `percent split fails when not 100`() {
        val result = split(SplitType.PERCENT, 1_000L, listOf("a", "b"), mapOf("a" to 40L, "b" to 50L))
        assertTrue(result is SplitResult.Invalid)
    }

    @Test
    fun `percent split with rounding remainder goes to first participants`() {
        // 10001 paise at 50/50 → 5000 + 5000 + 1 leftover
        val result = split(SplitType.PERCENT, 10_001L, listOf("a", "b"), mapOf("a" to 50L, "b" to 50L))
        val shares = (result as SplitResult.Ok).shares
        assertEquals(10_001L, shares.sumOf { it.second })
        assertEquals(5_001L, shares.first { it.first == "a" }.second)
        assertEquals(5_000L, shares.first { it.first == "b" }.second)
    }

    @Test
    fun `shares split is proportional and conserves total`() {
        val result = split(SplitType.SHARES, 10_000L, listOf("a", "b", "c"), mapOf("a" to 1L, "b" to 1L, "c" to 2L))
        val shares = (result as SplitResult.Ok).shares.associate { it }
        assertEquals(2_500L, shares["a"])
        assertEquals(2_500L, shares["b"])
        assertEquals(5_000L, shares["c"])
    }
}
