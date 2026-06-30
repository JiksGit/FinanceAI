package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_signals", uniqueConstraints = {
        @UniqueConstraint(name = "uq_symbol_date_indicator", columnNames = {"stock_symbol", "signal_date", "indicator"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 10)
    private SignalType signalType;

    @Column(nullable = false, length = 50)
    private String indicator;

    @Column(name = "signal_date", nullable = false, length = 10)
    private String signalDate;

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public StockSignal(String stockSymbol, SignalType signalType, String indicator, String signalDate, String aiExplanation) {
        this.stockSymbol = stockSymbol;
        this.signalType = signalType;
        this.indicator = indicator;
        this.signalDate = signalDate;
        this.aiExplanation = aiExplanation;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum SignalType {
        BUY, SELL
    }
}
