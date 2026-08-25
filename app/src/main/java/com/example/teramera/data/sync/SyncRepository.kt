package com.example.teramera.data.sync

import com.example.teramera.core.network.CreateExpenseRequestDto
import com.example.teramera.core.network.CreateSettlementRequestDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.core.network.TokenStore
import com.example.teramera.data.local.SyncedActivityEntity
import com.example.teramera.data.local.SyncedBalanceEntity
import com.example.teramera.data.local.SyncedGroupEntity
import com.example.teramera.data.local.TerameraDao
import javax.inject.Inject
import javax.inject.Singleton

/** Pulls server state into the synced_* Room tables and pushes writes when online. */
@Singleton
class SyncRepository @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val dao: TerameraDao,
    private val tokenStore: TokenStore,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.current() != null

    suspend fun selfUserId(): String? = tokenStore.current()?.userId

    suspend fun joinGroup(groupId: String) {
        ledgerApi.joinGroup(groupId)
    }

    suspend fun inviteByEmail(groupId: String, email: String): String? =
        try {
            ledgerApi.inviteByEmail(
                groupId,
                com.example.teramera.core.network.LedgerApi.InviteEmailRequestDto(email),
            )["status"]
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) null else throw e
        }

    suspend fun refreshNow(): Result = try {
        val now = System.currentTimeMillis()

        // cache the signed-in profile (name/UPI) for greetings & settling
        try {
            val me = ledgerApi.me()
            dao.insertUser(
                com.example.teramera.data.local.UserEntity(
                    id = me.id, name = me.name ?: "You", isSelf = true,
                    email = me.email, upiId = me.upiId?.ifEmpty { null },
                )
            )
        } catch (_: Exception) {
            // profile refresh is best-effort
        }

        val balances = ledgerApi.friendBalances().map { dto ->
            SyncedBalanceEntity(userId = dto.userId, name = dto.name, netMinor = dto.netMinor, upiId = dto.upiId.orEmpty(), updatedAt = now)
        }
        dao.clearSyncedBalances()
        dao.insertSyncedBalances(balances)

        val groups = ledgerApi.myGroups().map { dto ->
            SyncedGroupEntity(
                id = dto.id,
                name = dto.name,
                currency = dto.currency,
                totalSpentMinor = dto.totalSpentMinor,
                netForMeMinor = dto.netForMeMinor,
                updatedAt = now,
            )
        }
        dao.insertSyncedGroups(groups)

        val activity = ledgerApi.activity().map { e ->
            SyncedActivityEntity(
                id = e.id,
                type = e.type,
                title = e.title,
                counterpartyName = e.payerName,
                secondaryName = e.payeeName,
                paidBySelf = e.paidBySelf,
                involvedSelf = e.involvedSelf,
                amountMinor = e.amountMinor,
                myShareMinor = e.myShareMinor,
                groupName = e.groupName,
                participantCount = e.participantCount,
                methodLabel = e.methodLabel,
                createdAt = e.createdAt,
            )
        }
        dao.clearSyncedActivity()
        dao.insertSyncedActivity(activity)

        Result.Success
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Sync failed")
    }

    suspend fun updateProfile(name: String?, upiId: String?): Result = try {
        ledgerApi.updateMe(com.example.teramera.core.network.UpdateMeRequestDto(name = name, upiId = upiId))
        refreshNow()
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Couldn't save profile")
    }

    /** Registers an FCM token so expense notifications reach this device. */
    suspend fun registerDeviceToken(token: String): Boolean = try {
        ledgerApi.registerDevice(com.example.teramera.core.network.DeviceTokenRequestDto(token))
        true
    } catch (_: Exception) {
        false
    }

    /** Pushes an equal-split expense scoped to a group; server expands participants. */
    suspend fun pushEqualExpense(
        groupId: String,
        title: String,
        amountMinor: Long,
    ): Result {
        val selfId = tokenStore.current()?.userId ?: return Result.Failure("Not signed in")
        return try {
            ledgerApi.createExpense(
                CreateExpenseRequestDto(
                    groupId = groupId,
                    title = title,
                    amountMinor = amountMinor,
                    paidByUserId = selfId,
                    splitType = "EQUAL",
                    participants = emptyMap(),
                )
            )
            refreshNow()
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Couldn't save to server")
        }
    }

    /** Records a settlement against a counterparty, in whichever direction applies. */
    suspend fun pushSettlement(
        personUserId: String,
        personNetMinor: Long,
        amountMinor: Long,
        method: String,
    ): Result {
        val selfId = tokenStore.current()?.userId ?: return Result.Failure("Not signed in")
        // If they owed me (net > 0), they are the payer and I receive.
        val payer = if (personNetMinor > 0) personUserId else selfId
        val payee = if (personNetMinor > 0) selfId else personUserId
        return try {
            ledgerApi.createSettlement(
                CreateSettlementRequestDto(
                    groupId = null,
                    payerUserId = payer,
                    paidToUserId = payee,
                    amountMinor = amountMinor,
                    method = method,
                )
            )
            refreshNow()
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Couldn't save to server")
        }
    }
}
