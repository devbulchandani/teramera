package com.teramera.backend.auth;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private final SqlExecutor db;

    public RefreshTokenRepository(SqlExecutor db) {
        this.db = db;
    }

    public record StoredRefresh(String id, String userId, String hash, long expiresAt) {}

    public void save(String userId, JwtService.RefreshToken token) {
        db.update(
                "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, 0, ?)",
                List.of(token.jti(), userId, token.hash(), token.expiresAt().toEpochMilli(), System.currentTimeMillis())
        );
    }

    /** Returns the stored row when the raw token matches an active, unexpired one. */
    public ActiveToken findActiveByRawToken(String rawToken) {
        String hash = JwtService.sha256(rawToken);
        var rows = db.query(
                "SELECT id, user_id, token_hash, expires_at FROM refresh_tokens WHERE token_hash = ? AND revoked = 0",
                List.of(hash)
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        long expiresAt = ((Number) row.get("expires_at")).longValue();
        if (expiresAt < Instant.now().toEpochMilli()) return null;
        return new ActiveToken(
                (String) row.get("id"),
                (String) row.get("user_id"),
                (String) row.get("token_hash"),
                expiresAt
        );
    }

    public void revoke(String jti) {
        db.update("UPDATE refresh_tokens SET revoked = 1 WHERE id = ?", List.of(jti));
    }

    public record ActiveToken(String id, String userId, String hash, long expiresAt) {}
}
