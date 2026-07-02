package com.finance.dashboard.dto.response;

import java.math.BigDecimal;

public record MarketIndexResponse(
        String name,
        BigDecimal currentValue,
        BigDecimal change,
        BigDecimal changeRate,
        long volume,
        long tradingValue
) {}
