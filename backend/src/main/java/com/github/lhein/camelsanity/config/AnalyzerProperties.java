package com.github.lhein.camelsanity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analyzer")
public record AnalyzerProperties(GitHub github, Http http, Cache cache) {

    public record GitHub(String token) {
        public boolean hasToken() {
            return token != null && !token.isBlank() && !token.startsWith("ghp_your_token");
        }
    }

    public record Http(int connectTimeoutMs, int readTimeoutMs) {}

    public record Cache(int ttlMinutes, int maxEntries) {}
}
