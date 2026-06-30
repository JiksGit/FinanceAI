package com.finance.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signal")
public record SignalConfig(
        boolean mailEnabled,
        String cron
) {
}
