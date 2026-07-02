package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceUpdateMessage(
        String stockCode,
        String stockName,
        String market,
        long closePrice,
        long priceChange,
        BigDecimal changeRate,
        long volume,
        long marketCap,
        LocalDateTime updatedAt
) {}
