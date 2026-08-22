package com.teramera.backend.auth;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phone-number OTP issue/verify backed by the otp_requests table.
 * Codes are stored only as PBKDF2 hashes; attempts are capped.
 */
@Service
public class OtpService {

    private static final long OTP_TTL_MILLIS = 5 * 60 * 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final long REQUEST_WINDOW_MILLIS = 10 * 60 * 1000;

    public record IssuedOtp(String requestId, String code) {}

    private final SqlExecutor db;
    private final SecureRandom random = new SecureRandom();

    public OtpService(SqlExecutor db) {
        this.db = db;
    }

    /** Issues a new OTP for the phone, enforcing a simple rate limit. */
    public IssuedOtp issue(String phoneE164) {
        long now = System.currentTimeMillis();
        var recent = db.query(
                // count ALL requests in the window — invalidation below only marks old codes consumed
                "SELECT COUNT(*) AS c FROM otp_requests WHERE phone = ? AND created_at > ?",
                List.of(phoneE164, now - REQUEST_WINDOW_MILLIS)
        );
        long count = ((Number) recent.getFirst().get("c")).longValue();
        if (count >= MAX_REQUESTS_PER_WINDOW) {
            throw new OtpException("Too many codes requested. Try again later.");
        }
        // invalidate previous unconsumed codes
        db.update("UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", List.of(phoneE164));

        String code = "%06d".formatted(random.nextInt(1_000_000));
        String requestId = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO otp_requests (id, phone, code_hash, expires_at, attempts, consumed, created_at) VALUES (?, ?, ?, ?, 0, 0, ?)",
                List.of(requestId, phoneE164, hash(code), now + OTP_TTL_MILLIS, now)
        );
        return new IssuedOtp(requestId, code);
    }

    /**
     * Verifies the code and marks it consumed.
     * @return true when valid; throws {@link OtpException} with a user-safe message otherwise.
     */
    public void verify(String requestId, String code) {
        long now = System.currentTimeMillis();
        var rows = db.query("SELECT * FROM otp_requests WHERE id = ?", List.of(requestId));
        if (rows.isEmpty()) {
            throw new OtpException("Code request not found. Request a new code.");
        }
        Map<String, Object> row = rows.getFirst();

        if (toInt(row.get("consumed")) == 1) {
            throw new OtpException("Code already used. Request a new one.");
        }
        if (toLong(row.get("expires_at")) < now) {
            throw new OtpException("Code expired. Request a new one.");
        }
        int attempts = toInt(row.get("attempts"));
        if (attempts >= MAX_ATTEMPTS) {
            throw new OtpException("Too many wrong attempts. Request a new code.");
        }
        if (!hash(code).equals(row.get("code_hash"))) {
            db.update("UPDATE otp_requests SET attempts = ? WHERE id = ?", List.of(attempts + 1, requestId));
            throw new OtpException("Incorrect code.");
        }
        db.update("UPDATE otp_requests SET consumed = 1 WHERE id = ?", List.of(requestId));
    }

    public String phoneOf(String requestId) {
        var rows = db.query("SELECT phone FROM otp_requests WHERE id = ?", List.of(requestId));
        if (rows.isEmpty()) throw new OtpException("Code request not found.");
        return (String) rows.getFirst().get("phone");
    }

    public static class OtpException extends RuntimeException {
        public OtpException(String message) {
            super(message);
        }
    }

    private static String hash(String value) {
        try {
            byte[] salt = "teramera-otp-v1".getBytes(); // fixed pepper: codes are random + short-lived
            var spec = new PBEKeySpec(value.toCharArray(), salt, 30_000, 256);
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int toInt(Object o) {
        return ((Number) o).intValue();
    }

    private static long toLong(Object o) {
        return ((Number) o).longValue();
    }
}
