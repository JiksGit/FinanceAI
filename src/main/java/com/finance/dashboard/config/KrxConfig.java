package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "krx")
public record KrxConfig(
        String baseUrl
) {
}
