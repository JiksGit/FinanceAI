package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FavoriteStockResponse(
        String stockSymbol,
        String stockName,
        Integer quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal profitLoss,
        BigDecimal profitLossRate,
        LocalDateTime createdAt
) {
}
