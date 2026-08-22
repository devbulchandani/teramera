package com.teramera.backend.db;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * SQLite-file execution for local development and integration tests.
 * Mirrors the D1 executor's contract so repositories are backend-agnostic.
 */
public class LocalSqliteExecutor implements SqlExecutor {

    private final JdbcTemplate jdbc;

    public LocalSqliteExecutor(String path) {
        var file = new java.io.File(path);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        var dataSource = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.sqlite.JDBC(), // org.sqlite.JDBC implements java.sql.Driver
                "jdbc:sqlite:" + path
        );
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public static LocalSqliteExecutor inMemory() {
        return new LocalSqliteExecutor("file::memory:?cache=shared");
    }

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        return jdbc.queryForList(sql, params.toArray());
    }

    @Override
    public int update(String sql, List<Object> params) {
        return jdbc.update(sql, params.toArray());
    }
}
