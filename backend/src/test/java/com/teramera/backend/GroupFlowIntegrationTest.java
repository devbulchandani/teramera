package com.teramera.backend;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two users register via OTP, form a group, add expenses, settle up — the full
 * server-side ledger is verified end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupFlowIntegrationTest {

    // fresh DB file per JVM run so assertions never see stale state
    static final String dbId = java.util.UUID.randomUUID().toString();

    @DynamicPropertySource
    static void props(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.sqlite-path", () ->
                System.getProperty("java.io.tmpdir") + "/group-flow-" + dbId + ".db");
        registry.add("app.db-mode", () -> "local");
        registry.add("app.expose-dev-otp", () -> "true");
    }


    @Autowired
    private TestRestTemplate rest;

    static String aliceToken;
    static String bobToken;
    static String aliceId;
    static String bobId;
    static String groupId;

    private String login(String phone) {
        var request = rest.postForEntity("/auth/otp/request", Map.of("phone", phone), Map.class);
        assertEquals(HttpStatus.OK, request.getStatusCode());
        var verify = rest.postForEntity("/auth/otp/verify", Map.of(
                "requestId", request.getBody().get("requestId"),
                "code", request.getBody().get("devCode")), Map.class);
        assertEquals(HttpStatus.OK, verify.getStatusCode());
        return (String) verify.getBody().get("accessToken");
    }

    @Test
    @Order(1)
    void twoUsersRegister() {
        aliceToken = login("+919900000001");
        bobToken = login("+919900000002");

        aliceId = (String) get("/me", aliceToken).getBody().get("id");
        bobId = (String) get("/me", bobToken).getBody().get("id");
        assertTrue(!aliceId.equals(bobId));
    }

    @Test
    @Order(2)
    void createGroupWithBothMembers() {
        var response = post("/groups", aliceToken, Map.of(
                "name", "Goa Trip", "currency", "INR",
                "memberUserIds", List.of(bobId)));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        groupId = (String) response.getBody().get("id");
    }

    @Test
    @Order(3)
    void bobPaysExpenseSplitEqually() {
        // Bob paid 2000 for both → Alice owes Bob 1000
        var response = post("/expenses", bobToken, Map.of(
                "groupId", groupId,
                "title", "Scooter rentals",
                "amountMinor", 2000,
                "paidByUserId", bobId,
                "splitType", "EQUAL",
                "participants", Map.of()));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(4)
    void groupBalancesAndSimplification() {
        var detail = get("/groups/" + groupId + "/detail", aliceToken);
        assertEquals(HttpStatus.OK, detail.getStatusCode());

        var body = detail.getBody();
        assertEquals(2, ((List<?>) body.get("members")).size());
        assertEquals(2000, ((Number) body.get("totalSpentMinor")).longValue());

        var balances = (List<Map<String, Object>>) body.get("balances");
        long aliceNet = netOf(balances, aliceId);
        long bobNet = netOf(balances, bobId);
        assertEquals(-1000L, aliceNet); // Alice owes 1000
        assertEquals(1000L, bobNet);

        var debts = (List<Map<String, Object>>) body.get("simplifiedDebts");
        assertEquals(1, debts.size());
        assertEquals(bobId, debts.getFirst().get("toUserId"));
        assertEquals(aliceId, debts.getFirst().get("fromUserId"));
        assertEquals(1000, ((Number) debts.getFirst().get("amountMinor")).intValue());
    }

    @Test
    @Order(5)
    void settlementReducesBalance() {
        // Alice pays Bob 400 → Alice now owes only 600
        var response = post("/settlements", aliceToken, Map.of(
                "groupId", groupId,
                "paidToUserId", bobId,
                "amountMinor", 400,
                "method", "UPI"));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        var detail = get("/groups/" + groupId + "/detail", aliceToken);
        var balances = (List<Map<String, Object>>) detail.getBody().get("balances");
        assertEquals(-600L, netOf(balances, aliceId));
    }

    @Test
    @Order(6)
    void nonMemberCannotReadGroup() {
        String carolToken = login("+919900000003");
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange("/groups/" + groupId + "/detail", HttpMethod.GET,
                        new HttpEntity<>(headers(carolToken)), Map.class).getStatusCode());
    }

    @Test
    @Order(7)
    void friendBalancesIncludeGroupDebt() {
        var balances = rest.exchange("/balances", HttpMethod.GET, new HttpEntity<>(headers(aliceToken)), List.class);
        assertEquals(HttpStatus.OK, balances.getStatusCode());
        long bobNet = ((List<?>) balances.getBody()).stream()
                .map(entry -> (Map<String, Object>) entry)
                .filter(entry -> bobId.equals(entry.get("userId")))
                .findFirst().orElseThrow()
                .get("netMinor") instanceof Number n ? n.longValue() : 0;
        assertEquals(-600L, bobNet);
    }

    private long netOf(List<Map<String, Object>> balances, String userId) {
        return balances.stream()
                .filter(b -> userId.equals(b.get("userId")))
                .findFirst().orElseThrow()
                .get("netMinor") instanceof Number n ? n.longValue() : 0;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
