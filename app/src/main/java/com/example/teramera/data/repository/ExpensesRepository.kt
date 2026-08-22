package com.example.teramera.data.repository

import com.example.teramera.data.local.ExpenseEntity
import com.example.teramera.data.local.ExpenseShareEntity
import com.example.teramera.data.local.TerameraDao
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
    suspend fun saveExpense(
        groupId: String?,
        paidByUserId: String,
        title: String,
        input: SplitInput,
        createdAt: Long = System.currentTimeMillis(),
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Title is required"))
        if (input.totalMinor <= 0) return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        if (input.participants.isEmpty()) return Result.failure(IllegalArgumentException("Pick at least one person"))

        val split = computeSplit(input)
        val shares = when (split) {
            is SplitResult.Ok -> split.shares
            is SplitResult.Invalid -> return Result.failure(IllegalArgumentException(split.reason))
        }

        val expenseId = dao.insertExpenses(
            listOf(
                ExpenseEntity(
                    groupId = groupId,
                    paidByUserId = paidByUserId,
                    title = title.trim(),
                    amountMinor = shares.sumOf { it.second },
                    createdAt = createdAt,
                )
            )
        ).first()

        dao.insertShares(shares.map { ExpenseShareEntity(expenseId, it.first, it.second) })
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

        private fun ok(shares: List<Pair<String, Long>>) =
            if (shares.any { it.second < 0 }) SplitResult.Invalid("Shares cannot be negative") else SplitResult.Ok(shares)

        fun paiseToRupeeString(minor: Long): String =
            java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(minor / 100)
    }
}
