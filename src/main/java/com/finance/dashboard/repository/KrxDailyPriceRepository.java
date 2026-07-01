package com.finance.dashboard.repository;

import com.finance.dashboard.entity.KrxDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KrxDailyPriceRepository extends JpaRepository<KrxDailyPrice, Long> {

    Optional<KrxDailyPrice> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    boolean existsByTradeDate(LocalDate tradeDate);

    List<KrxDailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            String stockCode, LocalDate from, LocalDate to);

    @Query("SELECT p FROM KrxDailyPrice p WHERE p.tradeDate = :date ORDER BY p.marketCap DESC LIMIT :limit")
    List<KrxDailyPrice> findTopByMarketCapOnDate(LocalDate date, int limit);

    @Query("SELECT p FROM KrxDailyPrice p WHERE p.tradeDate = :date AND p.market = :market ORDER BY p.marketCap DESC LIMIT :limit")
    List<KrxDailyPrice> findTopByMarketCapOnDateAndMarket(LocalDate date, String market, int limit);

    @Query("SELECT MAX(p.tradeDate) FROM KrxDailyPrice p")
    Optional<LocalDate> findLatestTradeDate();
}
