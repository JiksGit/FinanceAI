package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "service")
public record ServiceAuthConfig(
        String token
) {
    public boolean isEnabled() {
        return token != null && !token.isBlank();
    }
}
