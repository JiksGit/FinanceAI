package com.finance.dashboard.dto.response;

import java.util.List;

public record MetalHistoryResponse(
        String symbol,
        String nameKr,
        String currency,
        List<HistoryItem> history
) {
    public record HistoryItem(String date, double open, double high, double low, double close) {}
}
