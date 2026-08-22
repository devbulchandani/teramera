package com.teramera.backend;

import com.teramera.backend.auth.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(new com.teramera.backend.config.AppProperties(
            "test-secret-0123456789abcdef0123456789abcdef", 15L, 30L,
            "", true, "local", "./data/test.db", null));

    @Test
    void accessTokenRoundTrip() {
        String token = jwt.issueAccessToken("user-1");
        var parsed = jwt.parse(token);
        assertTrue(jwt.isValidAccess(token));
        org.junit.jupiter.api.Assertions.assertEquals("user-1", parsed.subject());
        org.junit.jupiter.api.Assertions.assertEquals("access", parsed.type());
    }

    @Test
    void refreshTokensAreUniqueAndHashable() {
        var first = jwt.issueRefreshToken();
        var second = jwt.issueRefreshToken();
        assertNotEquals(first.token(), second.token());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(second.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issueAccessToken("user-1");
        assertFalse(jwt.isValidAccess(token + "x"));
        assertThrows(Exception.class, () -> jwt.parse(token + "x"));
    }
}
