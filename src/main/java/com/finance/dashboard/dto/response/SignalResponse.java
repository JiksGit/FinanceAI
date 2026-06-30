package com.finance.dashboard.dto.response;

import com.finance.dashboard.entity.StockSignal;

import java.time.LocalDateTime;

public record SignalResponse(
        String stockSymbol,
        StockSignal.SignalType signalType,
        String indicator,
        String signalDate,
        String aiExplanation,
        LocalDateTime createdAt
) {
    public static SignalResponse from(StockSignal signal) {
        return new SignalResponse(
                signal.getStockSymbol(),
                signal.getSignalType(),
                signal.getIndicator(),
                signal.getSignalDate(),
                signal.getAiExplanation(),
                signal.getCreatedAt()
        );
    }
}
