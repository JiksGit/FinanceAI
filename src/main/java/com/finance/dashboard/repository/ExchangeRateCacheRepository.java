package com.finance.dashboard.repository;

import com.finance.dashboard.entity.ExchangeRateCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRateCacheRepository extends JpaRepository<ExchangeRateCache, Long> {

    List<ExchangeRateCache> findByBaseDate(String baseDate);
}
