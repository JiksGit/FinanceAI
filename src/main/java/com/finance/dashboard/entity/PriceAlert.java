package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "target_price", precision = 15, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "triggered_price", precision = 15, scale = 2)
    private BigDecimal triggeredPrice;

    /** true = 목표가 이상 도달, false = 목표가 이하 도달 */
    @Column(name = "target_above")
    private Boolean targetAbove;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        this.read = true;
    }
}
