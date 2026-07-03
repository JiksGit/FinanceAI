package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.CorrelationResponse;
import com.finance.dashboard.dto.response.MetalHistoryResponse;
import com.finance.dashboard.service.AssetDataService;
import com.finance.dashboard.service.CorrelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/correlation")
@RequiredArgsConstructor
public class CorrelationController {

    private final CorrelationService correlationService;
    private final AssetDataService assetDataService;

    // 환율/금/은/KOSPI 통합 상관관계 분석
    @GetMapping
    public CorrelationResponse getCorrelation(
            @RequestParam(defaultValue = "3mo") String range) {
        return correlationService.getCorrelation(range);
    }

    // 환율 히스토리 (Yahoo Finance - 장기 데이터)
    @GetMapping("/exchange/{currency}/history")
    public MetalHistoryResponse getExchangeHistory(
            @PathVariable String currency,
            @RequestParam(defaultValue = "3mo") String range) {
        // currency: USD, JPY, EUR, CNY
        String ticker = currency.toUpperCase() + "KRW=X";
        Map<String, Double> data = assetDataService.getDailyClose(ticker, range);

        List<MetalHistoryResponse.HistoryItem> items = data.entrySet().stream()
                .map(e -> new MetalHistoryResponse.HistoryItem(
                        e.getKey(), e.getValue(), e.getValue(), e.getValue(), e.getValue()))
                .toList();

        return new MetalHistoryResponse(currency.toUpperCase(), currency + "/KRW", "KRW", items);
    }
}
