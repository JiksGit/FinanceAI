package com.finance.dashboard.repository;

import com.finance.dashboard.entity.FavoriteStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FavoriteStockRepository extends JpaRepository<FavoriteStock, Long> {

    List<FavoriteStock> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteStock> findByUserIdAndStockSymbol(Long userId, String stockSymbol);

    boolean existsByUserIdAndStockSymbol(Long userId, String stockSymbol);

    List<FavoriteStock> findByStockSymbol(String stockSymbol);

    @Query("select distinct f.stockSymbol from FavoriteStock f")
    List<String> findDistinctStockSymbols();

    @Query("select f from FavoriteStock f where f.targetPrice is not null")
    List<FavoriteStock> findAllWithTargetPrice();
}
