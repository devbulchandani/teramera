package com.example.teramera.core.network

import retrofit2.http.Body
import retrofit2.http.POST

data class AuthTokensDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
)

data class AuthRefreshRequestDto(val refreshToken: String)
data class GoogleLoginRequestDto(val idToken: String)

interface AuthApi {
    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequestDto): AuthTokensDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: AuthRefreshRequestDto): AuthTokensDto
}