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
        LocalDateTime updatedAt,
        String market,      // KOSPI / KOSDAQ
        String sector,      // 업종
        Long marketCap      // 시가총액 (원)
) {
    // 기존 코드 호환용 (market/sector/marketCap 없는 경우)
    public StockResponse(String symbol, String name, BigDecimal price, BigDecimal change,
                         BigDecimal changeRate, BigDecimal high, BigDecimal low,
                         long volume, LocalDateTime updatedAt) {
        this(symbol, name, price, change, changeRate, high, low, volume, updatedAt, null, null, null);
    }
}
