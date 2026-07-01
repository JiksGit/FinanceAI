package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfitLoss,
        BigDecimal totalProfitLossRate,
        int holdingCount,
        List<HoldingWeight> weights
) {
    public record HoldingWeight(
            String symbol,
            String name,
            BigDecimal currentValue,
            double weightPercent
    ) {}
}
