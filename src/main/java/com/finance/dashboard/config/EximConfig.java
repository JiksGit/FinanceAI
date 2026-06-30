package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exim")
public record EximConfig(
        String apiKey,
        String baseUrl
) {
}
