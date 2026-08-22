package com.example.teramera.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ---- request/response DTOs mirroring the Spring Boot API ----

data class OtpRequestDto(val phone: String)
data class OtpRequestResponse(val requestId: String, val expiresInSeconds: Int, val devCode: String?)
data class OtpVerifyRequestDto(val requestId: String, val code: String)
data class AuthTokensDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
)

data class AuthRefreshRequestDto(val refreshToken: String)

interface AuthApi {

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestDto): OtpRequestResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequestDto): AuthTokensDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: AuthRefreshRequestDto): AuthTokensDto
}
