package com.finance.dashboard.controller;

import com.finance.dashboard.dto.request.FavoriteStockRequest;
import com.finance.dashboard.dto.request.UpdateHoldingRequest;
import com.finance.dashboard.dto.response.FavoriteStockResponse;
import com.finance.dashboard.dto.response.NewsResponse;
import com.finance.dashboard.dto.response.PortfolioSummaryResponse;
import com.finance.dashboard.dto.response.StockHistoryResponse;
import com.finance.dashboard.dto.response.StockResponse;
import com.finance.dashboard.dto.response.StockSearchResult;
import com.finance.dashboard.dto.response.TopStockResponse;
import com.finance.dashboard.security.UserPrincipal;
import com.finance.dashboard.dto.response.MarketIndexResponse;
import com.finance.dashboard.dto.response.StockDetailResponse;
import com.finance.dashboard.service.KrxService;
import com.finance.dashboard.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final KrxService krxService;

    @GetMapping("/search")
    public List<StockSearchResult> search(@RequestParam String keyword) {
        return stockService.search(keyword);
    }

    @GetMapping("/favorites")
    public List<FavoriteStockResponse> getFavorites(@AuthenticationPrincipal UserPrincipal principal) {
        return stockService.getFavorites(principal.getUserId());
    }

    @GetMapping("/portfolio/summary")
    public PortfolioSummaryResponse getPortfolioSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return stockService.getPortfolioSummary(principal.getUserId());
    }

    @PostMapping("/favorites")
    public ResponseEntity<Void> addFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FavoriteStockRequest request
    ) {
        stockService.addFavorite(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/favorites/{symbol}/holding")
    public ResponseEntity<Void> updateHolding(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String symbol,
            @Valid @RequestBody UpdateHoldingRequest request
    ) {
        stockService.updateHolding(principal.getUserId(), symbol, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/favorites/{symbol}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String symbol
    ) {
        stockService.removeFavorite(principal.getUserId(), symbol);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{symbol}/detail")
    public StockDetailResponse getStockDetail(@PathVariable String symbol) {
        return krxService.getStockDetail(symbol);
    }

    @GetMapping("/{symbol}")
    public StockResponse getStock(@PathVariable String symbol) {
        return stockService.getStock(symbol);
    }

    @GetMapping("/{symbol}/history")
    public StockHistoryResponse getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        return stockService.getHistory(symbol, days);
    }

    @GetMapping("/{symbol}/news")
    public NewsResponse getNews(@PathVariable String symbol) {
        return stockService.getNews(symbol);
    }

    @GetMapping("/market/indices")
    public List<MarketIndexResponse> getMarketIndices() {
        return krxService.getMarketIndices();
    }

    @GetMapping("/market/debug")
    public String debugNaver(@RequestParam(defaultValue = "KOSPI") String market) {
        String url = String.format("https://m.stock.naver.com/api/index/%s/stocks?pageSize=5&page=1&type=marketValue", market);
        return krxService.getRawJson(url);
    }

    @GetMapping("/market/reload")
    public String reloadMarket() {
        try {
            krxService.loadAllPrices(krxService.resolveLatestTradingDate());
            return "reload triggered";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    @GetMapping("/market/top")
    public List<TopStockResponse> getTopStocks(
            @RequestParam(defaultValue = "") String market,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return stockService.getTopByMarketCap(market, Math.min(limit, 100));
    }
}
