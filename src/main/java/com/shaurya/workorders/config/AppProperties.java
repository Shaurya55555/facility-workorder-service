package com.shaurya.workorders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Seed seed) {

    public record Jwt(String secret, long expiryMinutes) {
    }

    public record Seed(String adminUsername, String adminPassword, boolean demoUsers) {
    }
}
