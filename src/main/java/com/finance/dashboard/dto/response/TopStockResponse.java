package com.finance.dashboard.dto.response;

import java.math.BigDecimal;

public record TopStockResponse(
        String stockCode,
        String stockName,
        String market,
        Long closePrice,
        Long priceChange,
        BigDecimal changeRate,
        Long volume,
        Long marketCap
) {
}
