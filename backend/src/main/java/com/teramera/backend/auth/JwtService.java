package com.teramera.backend.auth;

import com.teramera.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtService {

    public record TokenPair(String accessToken, String refreshToken, Instant refreshExpiresAt) {}
    public record ParsedToken(String subject, String type, String jti) {}

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecretBytes());
        this.accessTtl = Duration.ofMinutes(props.accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.refreshTtlDays());
    }

    public String issueAccessToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("typ", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Returns the refresh token plus its JTI and SHA-256 hash for persistence. */
    public record RefreshToken(String token, String jti, String hash, Instant expiresAt) {}

    public RefreshToken issueRefreshToken() {
        String jti = UUID.randomUUID().toString();
        // Opaque random string; JWTs are unnecessary for a server-side revocable token.
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(refreshTtl);
        return new RefreshToken(token, jti, sha256(token), expiresAt);
    }

    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new ParsedToken(
                claims.getSubject(),
                claims.get("typ", String.class),
                claims.getId()
        );
    }

    public boolean isValidAccess(String token) {
        try {
            ParsedToken parsed = parse(token);
            return "access".equals(parsed.type());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
