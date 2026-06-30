package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpha-vantage")
public record AlphaVantageConfig(
        String apiKey,
        String baseUrl,
        boolean mockEnabled
) {
}
