package com.example.teramera.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.teramera.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Canonical seed data — matches the approved mockup story.
@Singleton
class DatabaseProvider @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val daoProvider: Provider<TerameraDao>,
) {
    fun provide(context: Context): TerameraDatabase =
        Room.databaseBuilder(context, TerameraDatabase::class.java, TerameraDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(SeedCallback(scope, daoProvider))
            .build()
}

private class SeedCallback(
    private val scope: CoroutineScope,
    private val daoProvider: Provider<TerameraDao>,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        scope.launch { seed(daoProvider.get()) }
    }

    companion object {
        const val DEV = "u_dev"
        const val PRIYA = "u_priya"
        const val ARJUN = "u_arjun"
        const val SNEHA = "u_sneha"
        const val KABIR = "u_kabir"
        const val GOA = "g_goa"
        const val FLAT = "g_flat"

        suspend fun seed(dao: TerameraDao) {
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000

            dao.insertUsers(
                listOf(
                    UserEntity(DEV, "Dev", isSelf = true),
                    UserEntity(PRIYA, "Priya Sharma"),
                    UserEntity(ARJUN, "Arjun Mehta"),
                    UserEntity(SNEHA, "Sneha Kulkarni"),
                    UserEntity(KABIR, "Kabir Shah"),
                )
            )
            dao.insertGroups(
                listOf(GroupEntity(GOA, "Goa Trip"), GroupEntity(FLAT, "Flat 402"))
            )
            dao.insertMemberships(
                listOf(DEV, PRIYA, ARJUN, SNEHA, KABIR).map { MembershipEntity(GOA, it) } +
                    listOf(DEV, ARJUN, KABIR).map { MembershipEntity(FLAT, it) }
            )

            val expenses = listOf(
                // Non-group IOUs (exact splits)
                ExpenseEntity(groupId = null, paidByUserId = DEV, title = "Concert tickets", amountMinor = 624_000L, createdAt = now - 2 * day),
                ExpenseEntity(groupId = null, paidByUserId = DEV, title = "Flat deposit", amountMinor = 290_000L, createdAt = now - 4 * day),
                ExpenseEntity(groupId = null, paidByUserId = DEV, title = "Gift for Riya's birthday", amountMinor = 236_000L, createdAt = now - 5 * day),
                ExpenseEntity(groupId = null, paidByUserId = KABIR, title = "Cab to airport", amountMinor = 184_000L, createdAt = now - 6 * day),
                // Goa Trip
                ExpenseEntity(groupId = GOA, paidByUserId = DEV, title = "Airbnb Anjuna", amountMinor = 960_000L, createdAt = now - day),
                ExpenseEntity(groupId = GOA, paidByUserId = PRIYA, title = "Beach shack dinner", amountMinor = 448_000L, createdAt = now - day),
                ExpenseEntity(groupId = GOA, paidByUserId = DEV, title = "Scooter rentals", amountMinor = 200_000L, createdAt = now - day),
                // Flat 402
                ExpenseEntity(groupId = FLAT, paidByUserId = ARJUN, title = "Internet bill", amountMinor = 189_900L, createdAt = now - 3 * day),
            )
            val ids = dao.insertExpenses(expenses)

            val everyone = listOf(DEV, PRIYA, ARJUN, SNEHA, KABIR)
            val noSneha = listOf(DEV, PRIYA, ARJUN, KABIR)
            val shares = buildList {
                fun even(index: Int, participants: List<String>) {
                    val per = expenses[index].amountMinor / participants.size
                    participants.forEach { add(ExpenseShareEntity(ids[index], it, per)) }
                }
                addAll(listOf(DEV, PRIYA).map { ExpenseShareEntity(ids[0], it, 312_000L) })
                addAll(listOf(DEV, ARJUN).map { ExpenseShareEntity(ids[1], it, 145_000L) })
                addAll(listOf(DEV, SNEHA).map { ExpenseShareEntity(ids[2], it, 118_000L) })
                addAll(listOf(DEV, KABIR).map { ExpenseShareEntity(ids[3], it, 92_000L) })
                even(4, everyone)
                even(5, everyone)
                even(6, noSneha)
                even(7, listOf(DEV, ARJUN, KABIR))
            }
            dao.insertShares(shares)
        }
    }
}
