package com.teramera.backend.db;

import com.teramera.backend.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the SQL execution backend: Cloudflare D1 over HTTP in production,
 * a local SQLite file for development ("local" mode).
 */
@Configuration
public class ExecutorConfig {

    @Bean
    public SqlExecutor sqlExecutor(AppProperties props) {
        return switch (props.dbMode()) {
            case "d1" -> new D1HttpExecutor(props);
            case "local" -> new LocalSqliteExecutor(props.sqlitePath());
            default -> throw new IllegalStateException("Unknown app.db-mode: " + props.dbMode());
        };
    }
}
