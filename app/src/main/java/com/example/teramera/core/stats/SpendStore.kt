package com.example.teramera.core.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.spendDataStore: DataStore<Preferences> by preferencesDataStore(name = "spend")

data class MonthSpend(
    val month: String, // yyyy-MM
    val spentMinor: Long,
    val expenseCount: Int,
)

data class SpendSnapshot(
    val totalSpentMinor: Long,
    val totalExpenseCount: Int,
    val thisMonthKey: String,
    val thisMonthSpentMinor: Long,
    val thisMonthExpenseCount: Int,
    val lastMonthSpentMinor: Long,
    val lastMonthExpenseCount: Int,
    val months: List<MonthSpend>,
)

/** Persists the monthly spend rollup so Home/Activity work offline too. */
@Singleton
class SpendStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val snapshotKey = stringPreferencesKey("snapshot")

    val snapshot: Flow<SpendSnapshot?> = context.spendDataStore.data.map { prefs ->
        prefs[snapshotKey]?.let(::decode)
    }

    suspend fun save(snapshot: SpendSnapshot) {
        context.spendDataStore.edit { it[snapshotKey] = encode(snapshot) }
    }

    suspend fun clear() {
        context.spendDataStore.edit { it.clear() }
    }

    private fun encode(snapshot: SpendSnapshot): String = JSONObject().apply {
        put("totalSpentMinor", snapshot.totalSpentMinor)
        put("totalExpenseCount", snapshot.totalExpenseCount)
        put("thisMonthKey", snapshot.thisMonthKey)
        put("thisMonthSpentMinor", snapshot.thisMonthSpentMinor)
        put("thisMonthExpenseCount", snapshot.thisMonthExpenseCount)
        put("lastMonthSpentMinor", snapshot.lastMonthSpentMinor)
        put("lastMonthExpenseCount", snapshot.lastMonthExpenseCount)
        put("months", JSONArray().apply {
            snapshot.months.forEach { m ->
                put(JSONObject().apply {
                    put("month", m.month)
                    put("spentMinor", m.spentMinor)
                    put("expenseCount", m.expenseCount)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): SpendSnapshot? = runCatching {
        val json = JSONObject(raw)
        val months = json.optJSONArray("months") ?: JSONArray()
        SpendSnapshot(
            totalSpentMinor = json.optLong("totalSpentMinor"),
            totalExpenseCount = json.optInt("totalExpenseCount"),
            thisMonthKey = json.optString("thisMonthKey"),
            thisMonthSpentMinor = json.optLong("thisMonthSpentMinor"),
            thisMonthExpenseCount = json.optInt("thisMonthExpenseCount"),
            lastMonthSpentMinor = json.optLong("lastMonthSpentMinor"),
            lastMonthExpenseCount = json.optInt("lastMonthExpenseCount"),
            months = (0 until months.length()).mapNotNull { index ->
                val m = months.optJSONObject(index) ?: return@mapNotNull null
                MonthSpend(
                    month = m.optString("month"),
                    spentMinor = m.optLong("spentMinor"),
                    expenseCount = m.optInt("expenseCount"),
                )
            },
        )
    }.getOrNull()
}
