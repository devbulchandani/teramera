package com.example.teramera.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class BalanceDto(val userId: String, val name: String, val netMinor: Long)
data class GroupSummaryDto(
    val id: String,
    val name: String,
    val currency: String,
    val totalSpentMinor: Long,
    val netForMeMinor: Long,
)

data class CreateGroupRequestDto(val name: String, val currency: String?, val memberUserIds: List<String>?)
data class CreateGroupResponseDto(val id: String, val name: String, val currency: String)

data class CreateExpenseRequestDto(
    val groupId: String?,
    val title: String,
    val amountMinor: Long,
    val paidByUserId: String,
    val splitType: String,
    val participants: Map<String, Long>,
    val currency: String? = null,
    val fxRateToGroup: Double? = null,
)

data class CreateExpenseResponseDto(val id: String, val amountMinor: Long, val shareCount: Int)

data class CreateSettlementRequestDto(
    val groupId: String?,
    val payerUserId: String?,
    val paidToUserId: String,
    val amountMinor: Long,
    val method: String,
)

interface LedgerApi {

    @GET("groups")
    suspend fun myGroups(): List<GroupSummaryDto>

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequestDto): CreateGroupResponseDto

    @GET("balances")
    suspend fun friendBalances(): List<BalanceDto>

    @POST("expenses")
    suspend fun createExpense(@Body body: CreateExpenseRequestDto): CreateExpenseResponseDto

    @POST("settlements")
    suspend fun createSettlement(@Body body: CreateSettlementRequestDto): Map<String, String>
}
