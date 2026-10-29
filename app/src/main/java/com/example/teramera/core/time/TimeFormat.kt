package com.example.teramera.core.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One source of truth for dates and times. Matches the brand rule:
 * relative when recent ("Today", "Yesterday"), absolute after ("3 Mar 2026"),
 * with a lower-case 12-hour clock ("4:12 pm").
 *
 * SimpleDateFormat is not thread-safe, so every formatter is guarded.
 */
object TimeFormat {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    private val dayYearFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    /** "4:12 pm" */
    fun time(epochMillis: Long): String =
        synchronized(timeFmt) {
            timeFmt.format(Date(epochMillis)).lowercase(Locale.getDefault())
        }

    /** Midnight of the day the timestamp falls in — use as a stable grouping key. */
    fun dayStart(epochMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** "Today" · "Yesterday" · "3 Mar" · "3 Mar 2026" (year only when not this year) */
    fun dayLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val start = dayStart(epochMillis)
        val today = dayStart(now)
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return when (start) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val fmt = if (sameYear(epochMillis, now)) dayFmt else dayYearFmt
                synchronized(fmt) { fmt.format(Date(epochMillis)) }
            }
        }
    }

    /** "Today 4:12 pm" · "Yesterday 9:40 am" · "3 Mar 2026, 4:12 pm" */
    fun dayTime(epochMillis: Long): String {
        val day = dayLabel(epochMillis)
        val time = time(epochMillis)
        return if (day == "Today" || day == "Yesterday") "$day $time" else "$day, $time"
    }

    /** "just now" · "12m ago" · "3h ago" · "Yesterday" · "3 Mar" */
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = now - epochMillis
        return when {
            elapsed < 60_000 -> "just now"
            elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
            dayStart(epochMillis) == dayStart(now) -> "${elapsed / 3_600_000}h ago"
            else -> dayLabel(epochMillis, now)
        }
    }

    private fun sameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}
