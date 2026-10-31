package com.example.teramera.data.sync

import com.example.teramera.core.network.CreateExpenseRequestDto
import com.example.teramera.core.network.CreateSettlementRequestDto
import com.example.teramera.core.network.LedgerApi
import com.example.teramera.core.network.TokenStore
import com.example.teramera.core.network.userMessage
import com.example.teramera.data.local.SyncedActivityEntity
import com.example.teramera.data.local.SyncedBalanceEntity
import com.example.teramera.data.local.SyncedGroupEntity
import com.example.teramera.core.stats.MonthSpend
import com.example.teramera.core.stats.SpendSnapshot
import com.example.teramera.core.stats.SpendStore
import com.example.teramera.data.local.TerameraDao
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

/** Pulls server state into the synced_* Room tables and pushes writes when online. */
@Singleton
class SyncRepository @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val dao: TerameraDao,
    private val tokenStore: TokenStore,
    private val spendStore: SpendStore,
) {
    private companion object {
        // Bump when local rollups may contain rows from deleted groups.
        const val CACHE_EPOCH = 1
    }
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    /** Bumped on every successful sync — lets screens re-fetch fresh server state. */
    private val _dataVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val dataVersion: kotlinx.coroutines.flow.StateFlow<Long> = _dataVersion

    /** When the last successful sync landed, for "updated 2m ago" labels. */
    private val _lastSyncAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val lastSyncAt: kotlinx.coroutines.flow.StateFlow<Long?> = _lastSyncAt

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

    suspend fun refreshNow(): Result {
        return try {
        val now = System.currentTimeMillis()
        if (tokenStore.cacheEpoch() < CACHE_EPOCH) {
            // One-time cleanup for installs that still hold activity/spend from
            // groups deleted before the server-side purge existed.
            dao.clearSyncedActivity()
            spendStore.clear()
            tokenStore.setCacheEpoch(CACHE_EPOCH)
        }
        var wroteAnything = false
        var meLoaded = false

        // all four endpoints in parallel — sequential fetches were the main lag
        kotlinx.coroutines.coroutineScope {
            val meDeferred = async { runCatching { ledgerApi.me() }.getOrNull() }
            val balancesDeferred = async { runCatching { ledgerApi.friendBalances() }.getOrNull() }
            val groupsDeferred = async { runCatching { ledgerApi.myGroups() }.getOrNull() }
            val activityDeferred = async { runCatching { ledgerApi.activity() }.getOrNull() }
            val spendDeferred = async { runCatching { ledgerApi.spendStats() }.getOrNull() }

            meDeferred.await()?.let { me ->
                meLoaded = true
                tokenStore.saveStats(me.totalSpentMinor, me.expenseCount)
                // the previous account's "me" row must not survive a sign-in switch
                dao.clearSelfUsers()
                dao.insertUser(
                    com.example.teramera.data.local.UserEntity(
                        id = me.id, name = me.name ?: "You", isSelf = true,
                        email = me.email, upiId = me.upiId?.ifEmpty { null },
                    )
                )
            }

            // Each replace happens inside a Room transaction so the downstream
            // Flow observes exactly one emission instead of delete + insert,
            // avoiding double-recompose on the home / activity screens.
            balancesDeferred.await()?.let { balances ->
                wroteAnything = true
                dao.replaceSyncedBalances(balances.map { dto ->
                    SyncedBalanceEntity(
                        userId = dto.userId, name = dto.name, netMinor = dto.netMinor,
                        upiId = dto.upiId.orEmpty(), updatedAt = now,
                    )
                })
            }

            groupsDeferred.await()?.let { groups ->
                wroteAnything = true
                // replace, not upsert — groups you left must disappear
                dao.replaceSyncedGroups(groups.map { dto ->
                    SyncedGroupEntity(
                        id = dto.id,
                        name = dto.name,
                        currency = dto.currency,
                        totalSpentMinor = dto.totalSpentMinor,
                        netForMeMinor = dto.netForMeMinor,
                        updatedAt = now,
                    )
                })
            }

            spendDeferred.await()?.let { spend ->
                spendStore.save(
                    SpendSnapshot(
                        totalSpentMinor = spend.totalSpentMinor,
                        totalExpenseCount = spend.totalExpenseCount,
                        thisMonthKey = spend.thisMonthKey,
                        thisMonthSpentMinor = spend.thisMonthSpentMinor,
                        thisMonthExpenseCount = spend.thisMonthExpenseCount,
                        lastMonthSpentMinor = spend.lastMonthSpentMinor,
                        lastMonthExpenseCount = spend.lastMonthExpenseCount,
                        months = spend.months.map {
                            MonthSpend(it.month, it.spentMinor, it.expenseCount)
                        },
                    )
                )
            }

            activityDeferred.await()?.let { activity ->
                wroteAnything = true
                dao.replaceSyncedActivity(activity.map { e ->
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
                })
            }
        }

        // "Success" used to mean "the call returned", even when every request
        // failed. Treat a missing profile as a failed refresh so callers can
        // tell "synced" from "offline with stale cache".
        if (!meLoaded) return Result.Failure("Couldn't refresh — check your connection")
        if (wroteAnything) _dataVersion.value += 1
        _lastSyncAt.value = now
        Result.Success
    } catch (e: Exception) {
        Result.Failure(e.userMessage("Sync failed"))
    }
    }

    /** Drop local traces of a group the moment the server confirms deletion. */
    suspend fun purgeDeletedGroup(groupId: String) {
        dao.deleteSyncedGroup(groupId)
        dao.clearSyncedActivity()
        spendStore.clear()
    }

    suspend fun updateProfile(name: String?, upiId: String?): Result = try {
        ledgerApi.updateMe(com.example.teramera.core.network.UpdateMeRequestDto(name = name, upiId = upiId))
        refreshNow()
    } catch (e: Exception) {
        Result.Failure(e.userMessage("Couldn't save profile"))
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
            Result.Failure(e.userMessage("Couldn't save to server"))
        }
    }

    sealed interface SettleRequestResult {
        /** [status] is "sent" or "logged" (email provider not configured). */
        data class Sent(val status: String) : SettleRequestResult
        data class Failure(val message: String) : SettleRequestResult
    }

    /** Emails a UPI settle link to the person who owes money. */
    suspend fun requestSettlement(toUserId: String, amountMinor: Long): SettleRequestResult =
        try {
            val status = ledgerApi.requestSettlement(
                com.example.teramera.core.network.RequestSettlementRequestDto(
                    toUserId = toUserId,
                    amountMinor = amountMinor,
                )
            )["status"] ?: "sent"
            SettleRequestResult.Sent(status)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val message = runCatching {
                org.json.JSONObject(body).optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "Couldn't send the settle request"
            SettleRequestResult.Failure(message)
        } catch (e: Exception) {
            SettleRequestResult.Failure(e.userMessage("Couldn't send the settle request"))
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
            Result.Failure(e.userMessage("Couldn't save to server"))
        }
    }
}