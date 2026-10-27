package com.example.teramera.data.repository

import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.ExpenseShareEntity
import com.example.teramera.data.local.TerameraDao
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class SplitType { EQUAL, EXACT, PERCENT, SHARES }

data class SplitInput(
    val type: SplitType,
    val totalMinor: Long,
    val participants: List<String>, // includes payer
    val rawValues: Map<String, Long> = emptyMap(), // exact: paise · percent: int pct · shares: weight
)

sealed interface SplitResult {
    data class Ok(val shares: List<Pair<String, Long>>) : SplitResult
    data class Invalid(val reason: String) : SplitResult
}

@Singleton
class ExpensesRepository @Inject constructor(
    private val dao: TerameraDao,
) {
    /**
     * Saves an expense, splitting the payment across [payments] when several
     * people fronted the money. Each payer becomes its own row (sharing a
     * `parentId`) with their slice of every share, so balances stay exact.
     */
    suspend fun saveExpense(
        groupId: String?,
        paidByUserId: String,
        title: String,
        input: SplitInput,
        payments: Map<String, Long> = emptyMap(),
        createdAt: Long = System.currentTimeMillis(),
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Title is required"))
        if (input.totalMinor <= 0) return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        if (input.participants.size < 2) return Result.failure(IllegalArgumentException("Pick at least two people"))

        val split = computeSplit(input)
        val shares = when (split) {
            is SplitResult.Ok -> split.shares
            is SplitResult.Invalid -> return Result.failure(IllegalArgumentException(split.reason))
        }

        val payers = payments.filterValues { it > 0 }.ifEmpty { mapOf(paidByUserId to input.totalMinor) }
        val paidTotal = payers.values.sum()
        if (paidTotal != input.totalMinor) {
            return Result.failure(
                IllegalArgumentException(
                    "Payer amounts add up to ₹${paiseToRupeeString(paidTotal)}, " +
                        "not ₹${paiseToRupeeString(input.totalMinor)}"
                )
            )
        }

        val shareWeights = shares.toMap()
        val parentId = if (payers.size > 1) UUID.randomUUID().toString() else null
        for ((payerId, payerAmount) in payers) {
            val allocation = if (payers.size == 1) shareWeights
            else allocateProRata(shareWeights, payerAmount, input.totalMinor)
            val expenseId = dao.insertExpenses(
                listOf(
                    ExpenseEntity(
                        groupId = groupId,
                        paidByUserId = payerId,
                        title = title.trim(),
                        amountMinor = payerAmount,
                        createdAt = createdAt,
                        parentId = parentId,
                    )
                )
            ).first()
            dao.insertShares(allocation.map { (userId, minor) -> ExpenseShareEntity(expenseId, userId, minor) })
        }
        return Result.success(Unit)
    }

    companion object {
        fun computeSplit(input: SplitInput): SplitResult {
            val people = input.participants.distinct()
            return when (input.type) {
                SplitType.EQUAL -> ok(evenSplit(input.totalMinor, people))

                SplitType.EXACT -> {
                    val sum = people.sumOf { input.rawValues[it] ?: 0L }
                    if (sum != input.totalMinor) {
                        SplitResult.Invalid("Exact amounts add up to ₹${paiseToRupeeString(sum)}, not ₹${paiseToRupeeString(input.totalMinor)}")
                    } else {
                        ok(people.map { it to (input.rawValues[it] ?: 0L) })
                    }
                }

                SplitType.PERCENT -> {
                    val totalPct = people.sumOf { input.rawValues[it] ?: 0L }
                    if (totalPct != 100L) {
                        SplitResult.Invalid("Percentages must add up to 100%")
                    } else {
                        val exact = people.map { it to input.totalMinor * (input.rawValues[it] ?: 0L) / 100 }
                        ok(distributeRemainder(exact, input.totalMinor))
                    }
                }

                SplitType.SHARES -> {
                    val weights = people.map { it to ((input.rawValues[it] ?: 1L)).coerceAtLeast(1L) }
                    val weightSum = weights.sumOf { it.second }
                    val exact = weights.map { (person, w) -> person to input.totalMinor * w / weightSum }
                    ok(distributeRemainder(exact, input.totalMinor))
                }
            }
        }

        private fun evenSplit(totalMinor: Long, people: List<String>): List<Pair<String, Long>> {
            val per = totalMinor / people.size
            var remainder = totalMinor - per * people.size
            return people.map { person ->
                val extra = if (remainder > 0) { remainder--; 1L } else 0L
                person to per + extra
            }
        }

        private fun distributeRemainder(
            computed: List<Pair<String, Long>>,
            totalMinor: Long,
        ): List<Pair<String, Long>> {
            var remainder = totalMinor - computed.sumOf { it.second }
            return computed.map { (person, amount) ->
                val extra = if (remainder > 0) { remainder--; 1L } else 0L
                person to amount + extra
            }
        }

        /**
         * Splits [bucket] across [weights] proportionally; leftover paise go to the
         * largest fractional remainders so the parts always sum to [bucket].
         */
        internal fun allocateProRata(
            weights: Map<String, Long>,
            bucket: Long,
            totalWeight: Long,
        ): Map<String, Long> {
            if (weights.isEmpty() || totalWeight <= 0 || bucket <= 0) return emptyMap()
            val total = BigInteger.valueOf(totalWeight)
            val result = mutableMapOf<String, Long>()
            val remainders = mutableMapOf<String, BigInteger>()
            var allocated = 0L
            for ((id, weight) in weights) {
                val product = BigInteger.valueOf(bucket) * BigInteger.valueOf(weight.coerceAtLeast(0))
                val part = product / total
                result[id] = part.toLong()
                remainders[id] = product % total
                allocated += part.toLong()
            }
            var leftover = bucket - allocated
            for ((id, _) in remainders.entries.sortedByDescending { it.value }) {
                if (leftover <= 0) break
                result[id] = (result[id] ?: 0L) + 1
                leftover--
            }
            return result
        }

        private fun ok(shares: List<Pair<String, Long>>) =
            if (shares.any { it.second < 0 }) SplitResult.Invalid("Shares cannot be negative") else SplitResult.Ok(shares)

        fun paiseToRupeeString(minor: Long): String =
            java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(minor / 100)
    }
}
