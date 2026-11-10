package com.example.teramera.core.auth

/** Why the app is showing the sign-in screen. */
enum class SignedOutReason {
    /** First launch on this device. */
    Never,

    /** The user explicitly signed out. */
    Manual,

    /** The backend rejected the refresh token; the user must sign in again. */
    Expired,
}

/**
 * Explicit session state machine.
 *
 * ```
 * Restoring ──> SignedIn ──> SignedOut(Expired)              refresh rejected
 *     │            │
 *     └────────────┴───────> SignedOut(Manual)               explicit sign-out
 *                  └───────> SignedIn(refreshDelayed = true)  transient refresh failure
 * ```
 *
 * Persisted facts are the source of truth: [SessionFacts.toState] is the only
 * place they are combined. Screens render this state exhaustively instead of
 * reading independent booleans (`isLoggedIn`, `sessionExpired`, …), which used
 * to allow transient or impossible combinations.
 */
sealed interface SessionState {
    /** DataStore has not emitted yet. */
    data object Restoring : SessionState

    data class SignedOut(val reason: SignedOutReason) : SessionState

    data class SignedIn(
        val userId: String,
        /**
         * A refresh failed transiently (offline, timeout, 5xx). The session is
         * still valid and the next request retries; do not sign the user out.
         */
        val refreshDelayed: Boolean = false,
    ) : SessionState
}

/** Everything the session state is derived from. */
data class SessionFacts(
    val restoring: Boolean,
    val hasTokens: Boolean,
    val userId: String,
    val signedOutReason: SignedOutReason?,
    val refreshDelayed: Boolean,
) {
    fun toState(): SessionState = when {
        restoring -> SessionState.Restoring
        hasTokens -> SessionState.SignedIn(userId, refreshDelayed)
        else -> SessionState.SignedOut(signedOutReason ?: SignedOutReason.Never)
    }
}
