package com.example.teramera.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {

    private fun facts(
        restoring: Boolean = false,
        hasTokens: Boolean = true,
        userId: String = "user-a",
        reason: SignedOutReason? = null,
        delayed: Boolean = false,
    ) = SessionFacts(restoring, hasTokens, userId, reason, delayed)

    @Test
    fun `restoring wins until storage emits`() {
        assertEquals(SessionState.Restoring, facts(restoring = true, hasTokens = false).toState())
    }

    @Test
    fun `tokens mean signed in`() {
        assertEquals(SessionState.SignedIn("user-a"), facts().toState())
    }

    @Test
    fun `a delayed refresh stays signed in`() {
        assertEquals(
            SessionState.SignedIn("user-a", refreshDelayed = true),
            facts(delayed = true).toState(),
        )
    }

    @Test
    fun `no tokens and no reason is a fresh install`() {
        assertEquals(
            SessionState.SignedOut(SignedOutReason.Never),
            facts(hasTokens = false).toState(),
        )
    }

    @Test
    fun `an explicit sign-out is remembered`() {
        assertEquals(
            SessionState.SignedOut(SignedOutReason.Manual),
            facts(hasTokens = false, reason = SignedOutReason.Manual).toState(),
        )
    }

    @Test
    fun `a rejected refresh is remembered as expired`() {
        assertEquals(
            SessionState.SignedOut(SignedOutReason.Expired),
            facts(hasTokens = false, reason = SignedOutReason.Expired).toState(),
        )
    }

    @Test
    fun `tokens take precedence over a stale reason`() {
        assertEquals(
            SessionState.SignedIn("user-a"),
            facts(hasTokens = true, reason = SignedOutReason.Expired).toState(),
        )
    }
}
