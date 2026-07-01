package com.finance.dashboard.repository;

import com.finance.dashboard.entity.KrxStockInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KrxStockInfoRepository extends JpaRepository<KrxStockInfo, String> {
    List<KrxStockInfo> findByStockNameContainingOrStockCodeContaining(String name, String code);
}
