package com.example.teramera.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class BalanceDto(val userId: String, val name: String, val netMinor: Long, val upiId: String? = null)
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
    val paidByUserId: String? = null,
    val splitType: String,
    val participants: Map<String, Long>? = null,
    val participantIds: List<String>? = null,
    val payments: List<PaymentDto>? = null,
    val currency: String? = null,
    val fxRateToGroup: Double? = null,
)

data class PaymentDto(val userId: String, val amountMinor: Long)

data class CreateExpenseResponseDto(val id: String, val amountMinor: Long, val shareCount: Int)

data class CreateSettlementRequestDto(
    val groupId: String?,
    val payerUserId: String?,
    val paidToUserId: String,
    val amountMinor: Long,
    val method: String,
)

// ---- friends & members ----

data class FoundUserDto(val id: String, val name: String, val phone: String?, val email: String?)
data class AddMemberRequestDto(val userId: String)
data class MemberDto(val id: String, val name: String, val isSelf: Boolean, val upiId: String? = null)
data class MeDto(val id: String, val phone: String?, val email: String?, val name: String?, val upiId: String?)
data class UpdateMeRequestDto(val name: String? = null, val upiId: String? = null)
data class ActivityEventDto(
    val type: String,
    val id: String,
    val title: String? = null,
    val payerName: String? = null,
    val payeeName: String? = null,
    val paidBySelf: Boolean = false,
    val involvedSelf: Boolean = false,
    val amountMinor: Long = 0,
    val myShareMinor: Long = 0,
    val groupName: String? = null,
    val participantCount: Int = 0,
    val methodLabel: String? = null,
    val createdAt: Long = 0,
)

data class DeviceTokenRequestDto(val token: String)
data class UpdateExpenseRequestDto(
    val title: String? = null,
    val amountMinor: Long? = null,
    val participantIds: List<String>? = null,
)
data class GroupExpenseDto(
    val id: String,
    val title: String,
    val paidByUserId: String,
    val amountMinor: Long,
    val myShareMinor: Long,
    val participantCount: Int,
    val createdAt: Long,
)

data class DebtDto(
    val fromUserId: String,
    val fromName: String,
    val toUserId: String,
    val toName: String,
    val amountMinor: Long,
)

data class GroupDetailDto(
    val id: String,
    val name: String,
    val currency: String,
    val totalSpentMinor: Long,
    val members: List<MemberDto>,
    val expenses: List<GroupExpenseDto>,
    val balances: List<BalanceDto>,
    val simplifiedDebts: List<DebtDto>,
)

interface LedgerApi {

    @GET("me")
    suspend fun me(): MeDto

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequestDto): MeDto

    @GET("activity")
    suspend fun activity(): List<ActivityEventDto>

    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceTokenRequestDto): Map<String, String>

    @PATCH("expenses/{expenseId}")
    suspend fun updateExpense(
        @Path("expenseId") expenseId: String,
        @Body body: UpdateExpenseRequestDto,
    ): Map<String, String>

    @DELETE("expenses/{expenseId}")
    suspend fun deleteExpense(@Path("expenseId") expenseId: String): Map<String, String>

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

    @GET("users/find")
    suspend fun findUser(@Query("phone") phone: String): FoundUserDto

    @GET("users/find")
    suspend fun findUserByEmail(@Query("email") email: String): FoundUserDto

    @POST("groups/{groupId}/members")
    suspend fun addMember(
        @Path("groupId") groupId: String,
        @Body body: AddMemberRequestDto,
    ): Map<String, String>

    @POST("groups/{groupId}/join")
    suspend fun joinGroup(@Path("groupId") groupId: String): Map<String, String>

    @POST("groups/{groupId}/invite-email")
    suspend fun inviteByEmail(
        @Path("groupId") groupId: String,
        @Body body: InviteEmailRequestDto,
    ): Map<String, String>

    data class InviteEmailRequestDto(val email: String)

    @GET("groups/{groupId}/detail")
    suspend fun groupDetail(@Path("groupId") groupId: String): GroupDetailDto

    @GET("app/version")
    suspend fun appVersion(): AppVersionDto
}

data class AppVersionDto(val versionCode: Int, val versionName: String, val apkUrl: String)
