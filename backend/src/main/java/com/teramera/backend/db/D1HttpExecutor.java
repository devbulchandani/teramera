package com.teramera.backend.db;

import com.teramera.backend.config.AppProperties;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Executes SQL against Cloudflare D1 via the HTTP API:
 * POST /client/v4/accounts/{account}/d1/database/{db}/query
 */
public class D1HttpExecutor implements SqlExecutor {

    private final RestClient client;
    private final String endpoint;

    public D1HttpExecutor(AppProperties props) {
        AppProperties.Cloudflare cf = props.cloudflare();
        if (isBlank(cf.accountId()) || isBlank(cf.databaseId()) || isBlank(cf.apiToken())) {
            throw new IllegalStateException(
                    "db-mode=d1 requires CF_ACCOUNT_ID, CF_D1_DATABASE_ID and CF_API_TOKEN");
        }
        this.endpoint = "https://api.cloudflare.com/client/v4/accounts/" + cf.accountId()
                + "/d1/database/" + cf.databaseId() + "/query";
        this.client = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + cf.apiToken())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        Map<String, Object> body = request(sql, params);
        Boolean success = (Boolean) body.get("success");
        if (!Boolean.TRUE.equals(success)) {
            throw new IllegalStateException("D1 query failed: " + body.get("errors"));
        }
        List<Map<String, Object>> results = ((List<Map<String, Object>>) body.get("result"));
        return (List<Map<String, Object>>) results.getFirst().getOrDefault("results", List.of());
    }

    @Override
    public int update(String sql, List<Object> params) {
        Map<String, Object> body = request(sql, params);
        List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("result");
        Object changes = result == null || result.isEmpty()
                ? Map.of()
                : result.getFirst().getOrDefault("meta", Map.of());
        if (changes instanceof Map<?, ?> meta && meta.get("changes") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String sql, List<Object> params) {
        var response = client.post()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .body(Map.of("sql", sql, "params", params))
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : response;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
