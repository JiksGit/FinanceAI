package com.finance.dashboard.repository;

import com.finance.dashboard.entity.KrxDailyPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KrxDailyPriceRepository extends JpaRepository<KrxDailyPrice, Long> {

    Optional<KrxDailyPrice> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    boolean existsByTradeDate(LocalDate tradeDate);

    long countByTradeDate(LocalDate tradeDate);

    List<KrxDailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            String stockCode, LocalDate from, LocalDate to);

    List<KrxDailyPrice> findByTradeDateOrderByMarketCapDesc(LocalDate date, Pageable pageable);

    List<KrxDailyPrice> findByTradeDateAndMarketOrderByMarketCapDesc(LocalDate date, String market, Pageable pageable);

    @Query("SELECT MAX(p.tradeDate) FROM KrxDailyPrice p")
    Optional<LocalDate> findLatestTradeDate();
}
