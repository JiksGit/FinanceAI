package com.finance.dashboard.repository;

import com.finance.dashboard.entity.StockSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockSignalRepository extends JpaRepository<StockSignal, Long> {

    List<StockSignal> findTop50ByOrderByCreatedAtDesc();

    List<StockSignal> findByStockSymbolInOrderByCreatedAtDesc(List<String> stockSymbols);

    Optional<StockSignal> findByStockSymbolAndSignalDateAndIndicator(String stockSymbol, String signalDate, String indicator);
}
