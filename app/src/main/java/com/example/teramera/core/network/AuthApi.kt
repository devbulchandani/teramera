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
data class GoogleLoginRequestDto(val idToken: String)

data class EmailCheckRequestDto(val email: String)
data class EmailCheckResponseDto(val exists: Boolean, val verified: Boolean)
data class EmailRegisterRequestDto(val email: String, val password: String)
data class EmailLoginRequestDto(val email: String, val password: String)
data class EmailOtpRequestDto(val email: String)
data class OtpVerifyResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
)

interface AuthApi {

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestDto): OtpRequestResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequestDto): AuthTokensDto

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequestDto): AuthTokensDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: AuthRefreshRequestDto): AuthTokensDto

    @POST("auth/email/check")
    suspend fun emailCheck(@Body body: EmailCheckRequestDto): EmailCheckResponseDto

    @POST("auth/email/register")
    suspend fun emailRegister(@Body body: EmailRegisterRequestDto): OtpRequestResponse

    @POST("auth/email/login")
    suspend fun emailLogin(@Body body: EmailLoginRequestDto): AuthTokensDto

    @POST("auth/email/otp")
    suspend fun emailOtp(@Body body: EmailOtpRequestDto): OtpRequestResponse

    @POST("auth/email/verify")
    suspend fun emailVerify(@Body body: OtpVerifyRequestDto): OtpVerifyResponseDto
}
