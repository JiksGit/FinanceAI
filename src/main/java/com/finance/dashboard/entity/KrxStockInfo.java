package com.finance.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "krx_stock_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class KrxStockInfo {

    @Id
    @Column(name = "stock_code", length = 6)
    private String stockCode;   // 6자리 종목코드 (005930)

    @Column(name = "isin_code", length = 12)
    private String isinCode;    // ISIN 코드 (KR7005930003)

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "market", length = 10)
    private String market;      // KOSPI / KOSDAQ

    @Column(name = "sector")
    private String sector;      // 업종명

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void update(String isinCode, String stockName, String market, String sector) {
        this.isinCode = isinCode;
        this.stockName = stockName;
        this.market = market;
        this.sector = sector;
        this.updatedAt = LocalDateTime.now();
    }
}
