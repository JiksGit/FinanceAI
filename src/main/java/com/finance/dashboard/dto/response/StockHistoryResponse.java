package com.finance.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record StockHistoryResponse(String symbol, List<HistoryItem> history) {

    public record HistoryItem(String date, BigDecimal price) {
    }
}
