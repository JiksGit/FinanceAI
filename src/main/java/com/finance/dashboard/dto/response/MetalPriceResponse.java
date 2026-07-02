package com.finance.dashboard.dto.response;

import java.math.BigDecimal;

public record MetalPriceResponse(
        String name,       // GOLD / SILVER
        String nameKr,     // 금 / 은
        String unit,       // oz (트로이온스)
        BigDecimal priceUsd,
        BigDecimal prevCloseUsd,
        BigDecimal changeUsd,
        BigDecimal changeRate,
        BigDecimal priceKrw,
        BigDecimal priceGram24k  // 그램당 24K (금만 유효)
) {}
