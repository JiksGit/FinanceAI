package com.finance.dashboard.repository;

import com.finance.dashboard.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PriceAlert> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndStockSymbolAndTargetPriceAndReadFalse(
            Long userId, String stockSymbol, java.math.BigDecimal targetPrice);
}
