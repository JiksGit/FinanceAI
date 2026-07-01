package com.finance.dashboard.dto.response;

import com.finance.dashboard.entity.StockSignal;

import java.time.LocalDateTime;

public record SignalResponse(
        String stockSymbol,
        String stockName,
        StockSignal.SignalType signalType,
        String indicator,
        String signalDate,
        String aiExplanation,
        LocalDateTime createdAt
) {
    public static SignalResponse from(StockSignal signal) {
        return from(signal, null);
    }

    public static SignalResponse from(StockSignal signal, String stockName) {
        return new SignalResponse(
                signal.getStockSymbol(),
                stockName,
                signal.getSignalType(),
                signal.getIndicator(),
                signal.getSignalDate(),
                signal.getAiExplanation(),
                signal.getCreatedAt()
        );
    }
}
