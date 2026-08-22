package com.teramera.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full auth-flow smoke test against a real HTTP server + SQLite file.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest {

    // fresh DB file per JVM run — avoids OTP rate-limit and stale-token flakiness
    static final String dbId = java.util.UUID.randomUUID().toString();

    @org.springframework.test.context.DynamicPropertySource
    static void props(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.sqlite-path", () ->
                System.getProperty("java.io.tmpdir") + "/auth-flow-" + dbId + ".db");
        registry.add("app.db-mode", () -> "local");
        registry.add("app.expose-dev-otp", () -> "true");
    }

    @Autowired
    private TestRestTemplate rest;

    private static final String PHONE = "+919812345678";

    @Test
    void otpLoginRefreshAndMeFlow() {
        // 1. request OTP
        var requestResponse = rest.postForEntity("/auth/otp/request", Map.of("phone", PHONE), Map.class);
        assertEquals(HttpStatus.OK, requestResponse.getStatusCode());
        String requestId = (String) requestResponse.getBody().get("requestId");
        String devCode = (String) requestResponse.getBody().get("devCode");
        assertNotNull(requestId);
        assertTrue(Pattern.compile("\\d{6}").matcher(devCode).matches());

        // 2. verify OTP → tokens
        var verifyResponse = rest.postForEntity(
                "/auth/otp/verify", Map.of("requestId", requestId, "code", devCode), Map.class);
        assertEquals(HttpStatus.OK, verifyResponse.getStatusCode());
        String accessToken = (String) verifyResponse.getBody().get("accessToken");
        String refreshToken = (String) verifyResponse.getBody().get("refreshToken");
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // 3. /me with token works
        var me = exchange("/me", accessToken);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals(PHONE, ((Map<?, ?>) me.getBody()).get("phone"));

        // 4. /me without token → 401
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/me", Map.class).getStatusCode());

        // 5. refresh rotation works and old token is dead
        var refreshed = rest.postForEntity("/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertEquals(HttpStatus.OK, refreshed.getStatusCode());
        String newRefresh = (String) refreshed.getBody().get("refreshToken");
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                rest.postForEntity("/auth/refresh", Map.of("refreshToken", refreshToken), Map.class).getStatusCode()
        );

        // 6. logout revokes the new token too
        rest.postForEntity("/auth/logout", Map.of("refreshToken", newRefresh), Void.class);
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                rest.postForEntity("/auth/refresh", Map.of("refreshToken", newRefresh), Map.class).getStatusCode()
        );
    }

    @Test
    void malformedPhoneIsRejected() {
        ResponseEntity<Map> response = rest.postForEntity("/auth/otp/request", Map.of("phone", "98765"), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private ResponseEntity<Map> exchange(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
