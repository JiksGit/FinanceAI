package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ExchangeRateHistoryResponse(String currency, List<HistoryItem> history) {

    public record HistoryItem(String date, BigDecimal rate) {
    }
}
