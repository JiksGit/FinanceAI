package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "krx_daily_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_krx_daily_price_code_date",
                columnNames = {"stock_code", "trade_date"}
        ),
        indexes = {
                @Index(name = "idx_krx_daily_price_code_date", columnList = "stock_code, trade_date"),
                @Index(name = "idx_krx_daily_price_trade_date", columnList = "trade_date"),
                @Index(name = "idx_krx_daily_price_market_cap", columnList = "market_cap")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class KrxDailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "market", length = 10)
    private String market;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_price")
    private Long closePrice;

    @Column(name = "open_price")
    private Long openPrice;

    @Column(name = "high_price")
    private Long highPrice;

    @Column(name = "low_price")
    private Long lowPrice;

    @Column(name = "price_change")
    private Long priceChange;

    @Column(name = "change_rate", precision = 10, scale = 2)
    private BigDecimal changeRate;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "market_cap")
    private Long marketCap;   // 시가총액 (원)
}
