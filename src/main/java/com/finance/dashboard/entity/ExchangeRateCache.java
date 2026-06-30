package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate_cache", uniqueConstraints = {
        @UniqueConstraint(name = "uq_currency_date", columnNames = {"currency_code", "base_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal rate;

    @Column(name = "base_date", nullable = false, length = 10)
    private String baseDate;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    public ExchangeRateCache(String currencyCode, BigDecimal rate, String baseDate) {
        this.currencyCode = currencyCode;
        this.rate = rate;
        this.baseDate = baseDate;
    }

    @PrePersist
    protected void onCreate() {
        this.fetchedAt = LocalDateTime.now();
    }
}
