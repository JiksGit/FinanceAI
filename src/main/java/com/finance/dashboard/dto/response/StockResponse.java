package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        BigDecimal high,
        BigDecimal low,
        long volume,
        LocalDateTime updatedAt
) {
}
