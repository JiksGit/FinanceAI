package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiConfig(
        String apiKey,
        String baseUrl,
        String model
) {
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
