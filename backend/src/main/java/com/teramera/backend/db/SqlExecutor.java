package com.teramera.backend.db;

import java.util.List;
import java.util.Map;

/**
 * Minimal SQL execution abstraction so the app can run against Cloudflare D1
 * (HTTP API) or a local SQLite file without ORM dependencies.
 */
public interface SqlExecutor {

    /** SELECT-style execution; returns rows as column→value maps. */
    List<Map<String, Object>> query(String sql, List<Object> params);

    /** INSERT/UPDATE/DELETE; returns affected row count. */
    int update(String sql, List<Object> params);
}
