package com.finance.dashboard.dto.response;

import java.math.BigDecimal;

public record StockDetailResponse(
        String stockCode,
        String stockName,
        String market,
        String sector,

        // 현재 시세
        long closePrice,
        long priceChange,
        BigDecimal changeRate,
        long openPrice,
        long highPrice,
        long lowPrice,
        long volume,
        long marketCap,

        // 투자지표 (Naver Finance 스크래핑)
        BigDecimal per,
        BigDecimal eps,
        BigDecimal pbr,
        BigDecimal bps,
        BigDecimal roe,
        BigDecimal dividendYield,

        // 52주 범위
        long high52w,
        long low52w,

        // 기타
        long sharesOutstanding,
        BigDecimal foreignRatio
) {}
