package com.example.teramera.core.network

import java.io.IOException
import retrofit2.HttpException

/**
 * Turns Retrofit/network failures into the reason the server actually sent.
 * Without this, a 403 shows as "HTTP 403 Forbidden" instead of, say,
 * "Only the person who added this expense can delete it".
 */
fun Throwable.userMessage(fallback: String = "Something went wrong. Try again."): String {
    return when (this) {
        is HttpException -> {
            val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val serverMessage = runCatching {
                org.json.JSONObject(body).optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }

            serverMessage ?: when (code()) {
                400 -> "That request wasn't valid."
                401 -> "Your session expired — sign in again."
                403 -> "You don't have permission to do that."
                404 -> "That item no longer exists."
                409 -> "That conflicts with something else. Refresh and try again."
                429 -> "Too many requests — wait a moment and try again."
                in 500..599 -> "The server had a problem. Try again in a moment."
                else -> fallback
            }
        }
        is IOException -> "Couldn't reach the server. Check your connection."
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
