package com.example.teramera.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Amounts are stored as minor units (paise). Positive balance = they owe you.

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isSelf: Boolean = false,
    val email: String? = null,
    val upiId: String? = null,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "memberships", primaryKeys = ["groupId", "userId"])
data class MembershipEntity(
    val groupId: String,
    val userId: String,
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String?,
    val paidByUserId: String,
    val title: String,
    val amountMinor: Long,
    val createdAt: Long,
)

@Entity(tableName = "expense_shares", primaryKeys = ["expenseId", "userId"])
data class ExpenseShareEntity(
    val expenseId: Long,
    val userId: String,
    val amountMinor: Long,
)

enum class PaymentMethod { UPI, CASH, BANK }

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String?,
    val payerUserId: String,
    val paidToUserId: String,
    val amountMinor: Long,
    val method: String,
    val createdAt: Long,
)

// ---- server-synced snapshots (written by SyncRepository, read when logged in) ----

@Entity(tableName = "synced_balances")
data class SyncedBalanceEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val netMinor: Long,
    val upiId: String = "",
    val updatedAt: Long,
)

@Entity(tableName = "synced_groups")
data class SyncedGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val totalSpentMinor: Long,
    val netForMeMinor: Long,
    val updatedAt: Long,
)

@Entity(tableName = "synced_activity", primaryKeys = ["id", "type"])
data class SyncedActivityEntity(
    val id: String,
    // "expense" | "settlement"
    val type: String,
    val title: String?,
    val counterpartyName: String?,
    val secondaryName: String?,
    val paidBySelf: Boolean,
    val involvedSelf: Boolean,
    val amountMinor: Long,
    val myShareMinor: Long,
    val groupName: String?,
    val participantCount: Int,
    val methodLabel: String?,
    val createdAt: Long,
)

@Dao
interface TerameraDao {
    @Query("SELECT * FROM users")
    fun users(): Flow<List<UserEntity>>

    @Query("SELECT * FROM `groups`")
    fun groups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM memberships")
    fun memberships(): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM expenses")
    fun expenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense_shares")
    fun shares(): Flow<List<ExpenseShareEntity>>

    @Query("SELECT * FROM settlements")
    fun settlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM synced_balances ORDER BY netMinor DESC")
    fun syncedBalances(): Flow<List<SyncedBalanceEntity>>

    @Query("SELECT COUNT(*) AS c FROM synced_balances")
    fun syncedBalanceCount(): Flow<Long>

    @Query("DELETE FROM synced_balances")
    suspend fun clearSyncedBalances()

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBalances(balances: List<SyncedBalanceEntity>)

    @Query("SELECT * FROM synced_groups ORDER BY netForMeMinor DESC")
    fun syncedGroups(): Flow<List<SyncedGroupEntity>>

    @Query("SELECT COUNT(*) AS c FROM synced_groups")
    fun syncedGroupCount(): Flow<Long>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSyncedGroups(groups: List<SyncedGroupEntity>)

    @Query("SELECT * FROM synced_activity ORDER BY createdAt DESC")
    fun syncedActivity(): Flow<List<SyncedActivityEntity>>

    @Query("DELETE FROM synced_activity")
    suspend fun clearSyncedActivity()

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSyncedActivity(events: List<SyncedActivityEntity>)

    @Query("SELECT * FROM users WHERE isSelf = 1 LIMIT 1")
    fun selfUser(): Flow<UserEntity?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert suspend fun insertUsers(users: List<UserEntity>)
    @Insert suspend fun insertGroups(groups: List<GroupEntity>)
    @Insert suspend fun insertMemberships(memberships: List<MembershipEntity>)
    @Insert suspend fun insertExpenses(expenses: List<ExpenseEntity>): List<Long>
    @Insert suspend fun insertShares(shares: List<ExpenseShareEntity>)
    @Insert suspend fun insertSettlement(settlement: SettlementEntity)
}

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        MembershipEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        SettlementEntity::class,
        SyncedBalanceEntity::class,
        SyncedGroupEntity::class,
        SyncedActivityEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class TerameraDatabase : RoomDatabase() {
    abstract fun dao(): TerameraDao

    companion object {
        const val NAME = "teramera.db"
    }
}
