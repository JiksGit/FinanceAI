package com.finance.dashboard.service;

import com.finance.dashboard.dto.request.FavoriteStockRequest;
import com.finance.dashboard.dto.request.UpdateHoldingRequest;
import com.finance.dashboard.dto.request.UpdateMemoRequest;
import com.finance.dashboard.dto.response.*;
import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.entity.KrxDailyPrice;
import com.finance.dashboard.entity.KrxStockInfo;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.FavoriteStockRepository;
import com.finance.dashboard.repository.KrxStockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final KrxStockInfoRepository stockInfoRepository;
    private final KrxService krxService;

    // ── 종목 검색 ────────────────────────────────────────────────

    public List<StockSearchResult> search(String keyword) {
        return krxService.searchStocks(keyword).stream()
                .map(info -> new StockSearchResult(info.getStockCode(), info.getStockName()))
                .limit(20)
                .toList();
    }

    // ── 현재가 조회 ──────────────────────────────────────────────

    public StockResponse getStock(String stockCode) {
        KrxDailyPrice price = krxService.getCurrentPrice(stockCode)
                .orElseGet(() -> {
                    // DB에 없으면 당일 전체 로드 후 재시도
                    try {
                        krxService.loadAllPrices(krxService.resolveLatestTradingDate());
                        return krxService.getCurrentPrice(stockCode).orElse(null);
                    } catch (Exception e) {
                        return null;
                    }
                });

        if (price == null) throw new CustomException(ErrorCode.STOCK_NOT_FOUND);

        KrxStockInfo info = stockInfoRepository.findById(stockCode).orElse(null);

        return new StockResponse(
                price.getStockCode(),
                price.getStockName(),
                BigDecimal.valueOf(price.getClosePrice()),
                BigDecimal.valueOf(price.getPriceChange()),
                price.getChangeRate(),
                BigDecimal.valueOf(price.getHighPrice()),
                BigDecimal.valueOf(price.getLowPrice()),
                price.getVolume(),
                LocalDateTime.now(),
                price.getMarket(),
                info != null ? info.getSector() : null,
                price.getMarketCap()
        );
    }

    // ── 가격 히스토리 ────────────────────────────────────────────

    public StockHistoryResponse getHistory(String stockCode, int days) {
        List<KrxDailyPrice> history = krxService.getPriceHistory(stockCode, days);
        List<StockHistoryResponse.HistoryItem> items = history.stream()
                .map(p -> new StockHistoryResponse.HistoryItem(
                        p.getTradeDate().toString(),
                        BigDecimal.valueOf(p.getClosePrice())))
                .toList();
        return new StockHistoryResponse(stockCode, items);
    }

    // ── 시가총액 TOP N ───────────────────────────────────────────

    public List<TopStockResponse> getTopByMarketCap(String market, int limit) {
        return krxService.getTopByMarketCap(market, limit).stream()
                .map(p -> new TopStockResponse(
                        p.getStockCode(),
                        p.getStockName(),
                        p.getMarket(),
                        p.getClosePrice(),
                        p.getPriceChange(),
                        p.getChangeRate(),
                        p.getVolume(),
                        p.getMarketCap()))
                .toList();
    }

    // ── 뉴스 ─────────────────────────────────────────────────────

    public NewsResponse getNews(String stockCode) {
        Optional<KrxStockInfo> info = stockInfoRepository.findById(stockCode);
        String stockName = info.map(KrxStockInfo::getStockName).orElse(stockCode);

        // 국내 주식 뉴스는 현재 Mock (향후 네이버뉴스 등 연동 가능)
        List<NewsResponse.NewsItem> items = List.of(
                new NewsResponse.NewsItem(
                        stockName + " 실적 발표, 시장 전망 상회",
                        "https://finance.naver.com/item/main.naver?code=" + stockCode,
                        stockName + "의 최근 실적이 시장 기대치를 웃돌며 긍정적인 평가를 받고 있습니다.",
                        "Bullish",
                        java.time.LocalDate.now().toString(),
                        "네이버 금융"
                ),
                new NewsResponse.NewsItem(
                        "외국인 " + stockName + " 순매수 지속",
                        "https://finance.naver.com/item/main.naver?code=" + stockCode,
                        "외국인 투자자들이 " + stockName + "을 꾸준히 순매수하며 수급이 개선되고 있습니다.",
                        "Bullish",
                        java.time.LocalDate.now().minusDays(1).toString(),
                        "한국경제"
                ),
                new NewsResponse.NewsItem(
                        "글로벌 금리 불확실성에 " + stockName + " 변동성 확대",
                        "https://finance.naver.com/item/main.naver?code=" + stockCode,
                        "미 연준의 금리 정책 불확실성으로 인해 성장주 중심의 변동성이 확대되고 있습니다.",
                        "Bearish",
                        java.time.LocalDate.now().minusDays(2).toString(),
                        "매일경제"
                )
        );
        return new NewsResponse(stockCode, items);
    }

    // ── 포트폴리오 ───────────────────────────────────────────────

    public List<FavoriteStockResponse> getFavorites(Long userId) {
        return favoriteStockRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toFavoriteResponse)
                .toList();
    }

    private FavoriteStockResponse toFavoriteResponse(FavoriteStock fav) {
        BigDecimal currentPrice = null;
        BigDecimal priceChange = null;
        BigDecimal changeRate = null;
        BigDecimal profitLoss = null;
        BigDecimal profitLossRate = null;

        try {
            KrxDailyPrice dailyPrice = krxService.getCurrentPrice(fav.getStockSymbol()).orElse(null);
            if (dailyPrice != null && dailyPrice.getClosePrice() > 0) {
                currentPrice = BigDecimal.valueOf(dailyPrice.getClosePrice());
                priceChange = BigDecimal.valueOf(dailyPrice.getPriceChange());
                changeRate = dailyPrice.getChangeRate();
            }
        } catch (Exception e) {
            log.warn("현재가 조회 실패: {}", fav.getStockSymbol());
        }

        if (currentPrice != null && fav.getQuantity() != null && fav.getAvgPrice() != null) {
            BigDecimal invested = fav.getAvgPrice().multiply(BigDecimal.valueOf(fav.getQuantity()));
            BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(fav.getQuantity()));
            profitLoss = currentValue.subtract(invested).setScale(0, RoundingMode.HALF_UP);
            profitLossRate = invested.compareTo(BigDecimal.ZERO) != 0
                    ? profitLoss.divide(invested, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        return new FavoriteStockResponse(
                fav.getStockSymbol(),
                fav.getStockName(),
                fav.getQuantity(),
                fav.getAvgPrice(),
                currentPrice,
                priceChange,
                changeRate,
                profitLoss,
                profitLossRate,
                fav.getTargetPrice(),
                fav.getTargetAbove(),
                fav.getCreatedAt(),
                fav.getMemo()
        );
    }

    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        List<FavoriteStockResponse> favorites = getFavorites(userId);

        List<FavoriteStockResponse> withHolding = favorites.stream()
                .filter(f -> f.quantity() != null && f.quantity() > 0
                        && f.avgPrice() != null && f.currentPrice() != null)
                .toList();

        BigDecimal totalInvested = withHolding.stream()
                .map(f -> f.avgPrice().multiply(BigDecimal.valueOf(f.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentValue = withHolding.stream()
                .map(f -> f.currentPrice().multiply(BigDecimal.valueOf(f.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal totalProfitLossRate = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalProfitLoss.divide(totalInvested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<PortfolioSummaryResponse.HoldingWeight> weights = withHolding.stream()
                .map(f -> {
                    BigDecimal value = f.currentPrice().multiply(BigDecimal.valueOf(f.quantity()));
                    double pct = totalCurrentValue.compareTo(BigDecimal.ZERO) != 0
                            ? value.divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                    return new PortfolioSummaryResponse.HoldingWeight(
                            f.stockSymbol(), f.stockName(),
                            value.setScale(0, RoundingMode.HALF_UP), pct);
                })
                .toList();

        return new PortfolioSummaryResponse(
                totalInvested.setScale(0, RoundingMode.HALF_UP),
                totalCurrentValue.setScale(0, RoundingMode.HALF_UP),
                totalProfitLoss,
                totalProfitLossRate,
                withHolding.size(),
                weights
        );
    }

    // ── 즐겨찾기 CRUD ────────────────────────────────────────────

    @Transactional
    public void addFavorite(Long userId, FavoriteStockRequest request) {
        String stockCode = request.stockSymbol().toUpperCase();
        if (favoriteStockRepository.existsByUserIdAndStockSymbol(userId, stockCode)) {
            throw new CustomException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        // 이름이 없으면 KrxStockInfo에서 자동 조회
        String name = request.stockName();
        if (name == null || name.isBlank()) {
            name = stockInfoRepository.findById(stockCode)
                    .map(info -> info.getStockName())
                    .orElse(stockCode);
        }
        favoriteStockRepository.save(FavoriteStock.builder()
                .userId(userId)
                .stockSymbol(stockCode)
                .stockName(name)
                .build());
    }

    @Transactional
    public void removeFavorite(Long userId, String stockCode) {
        FavoriteStock favorite = favoriteStockRepository
                .findByUserIdAndStockSymbol(userId, stockCode.toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        favoriteStockRepository.delete(favorite);
    }

    @Transactional
    public void updateHolding(Long userId, String stockCode, UpdateHoldingRequest request) {
        FavoriteStock favorite = favoriteStockRepository
                .findByUserIdAndStockSymbol(userId, stockCode.toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        favorite.updateHolding(request.quantity(), request.avgPrice());
    }

    @Transactional
    public void updateMemo(Long userId, String stockCode, UpdateMemoRequest request) {
        FavoriteStock favorite = favoriteStockRepository
                .findByUserIdAndStockSymbol(userId, stockCode.toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        favorite.updateMemo(request.memo());
    }
}
