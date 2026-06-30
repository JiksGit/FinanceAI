package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ExchangeRateResponse(String baseDate, List<RateItem> rates) {

    public record RateItem(String currency, BigDecimal rate, BigDecimal change, BigDecimal changeRate) {
    }
}
