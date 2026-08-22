package com.teramera.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        long accessTtlMinutes,
        long refreshTtlDays,
        String googleClientId,
        boolean exposeDevOtp,
        String dbMode,
        String sqlitePath,
        Cloudflare cloudflare
) {
    public record Cloudflare(String accountId, String databaseId, String apiToken) {}

    public byte[] jwtSecretBytes() {
        return jwtSecret.getBytes();
    }
}
