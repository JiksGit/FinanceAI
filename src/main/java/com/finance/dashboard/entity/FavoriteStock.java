package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_stocks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_stock", columnNames = {"user_id", "stock_symbol"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    /** 보유 수량. null이면 즐겨찾기만 하고 실보유는 아닌 상태. */
    @Column(name = "quantity")
    private Integer quantity;

    /** 평균 매수 단가. quantity와 함께 설정된다. */
    @Column(name = "avg_price", precision = 15, scale = 2)
    private BigDecimal avgPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FavoriteStock(Long userId, String stockSymbol, String stockName) {
        this.userId = userId;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void updateHolding(Integer quantity, BigDecimal avgPrice) {
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }
}
