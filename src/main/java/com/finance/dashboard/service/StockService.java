package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.dashboard.config.AlphaVantageConfig;
import com.finance.dashboard.dto.request.FavoriteStockRequest;
import com.finance.dashboard.dto.request.UpdateHoldingRequest;
import com.finance.dashboard.dto.response.*;
import com.finance.dashboard.dto.response.NewsResponse;
import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.FavoriteStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final AlphaVantageConfig alphaVantageConfig;
    private final RestClient restClient = RestClient.create();

    public List<StockSearchResult> search(String keyword) {
        if (alphaVantageConfig.mockEnabled()) {
            return MockStockData.search(keyword).values().stream()
                    .map(meta -> new StockSearchResult(meta.symbol(), meta.name()))
                    .toList();
        }

        try {
            JsonNode root = restClient.get()
                    .uri(alphaVantageConfig.baseUrl() + "?function=SYMBOL_SEARCH&keywords={keyword}&apikey={key}",
                            keyword, alphaVantageConfig.apiKey())
                    .retrieve()
                    .body(JsonNode.class);

            List<StockSearchResult> results = new ArrayList<>();
            if (root != null && root.has("bestMatches")) {
                for (JsonNode match : root.get("bestMatches")) {
                    results.add(new StockSearchResult(
                            match.path("1. symbol").asText(),
                            match.path("2. name").asText()
                    ));
                }
            }
            return results;
        } catch (RestClientException e) {
            log.error("Alpha Vantage 종목 검색 실패", e);
            throw new CustomException(ErrorCode.STOCK_API_ERROR);
        }
    }

    public StockResponse getStock(String symbol) {
        if (alphaVantageConfig.mockEnabled()) {
            return getMockStock(symbol);
        }

        try {
            JsonNode root = restClient.get()
                    .uri(alphaVantageConfig.baseUrl() + "?function=GLOBAL_QUOTE&symbol={symbol}&apikey={key}",
                            symbol, alphaVantageConfig.apiKey())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode quote = root != null ? root.get("Global Quote") : null;
            if (quote == null || !quote.has("01. symbol")) {
                throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
            }

            BigDecimal price = new BigDecimal(quote.path("05. price").asText());
            BigDecimal change = new BigDecimal(quote.path("09. change").asText());
            String changePercentRaw = quote.path("10. change percent").asText().replace("%", "");
            BigDecimal changeRate = new BigDecimal(changePercentRaw);

            return new StockResponse(
                    quote.path("01. symbol").asText(),
                    quote.path("01. symbol").asText(),
                    price,
                    change,
                    changeRate,
                    new BigDecimal(quote.path("03. high").asText()),
                    new BigDecimal(quote.path("04. low").asText()),
                    quote.path("06. volume").asLong(),
                    LocalDateTime.now()
            );
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Alpha Vantage 시세 조회 실패", e);
            throw new CustomException(ErrorCode.STOCK_API_ERROR);
        }
    }

    private StockResponse getMockStock(String symbol) {
        if (!MockStockData.has(symbol)) {
            throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        }

        MockStockData.StockMeta meta = MockStockData.get(symbol);
        LocalDate today = LocalDate.now();
        BigDecimal price = MockStockData.priceOn(meta, today);
        BigDecimal previousClose = MockStockData.previousClose(meta, today);
        BigDecimal change = price.subtract(previousClose).setScale(2, RoundingMode.HALF_UP);
        BigDecimal changeRate = previousClose.compareTo(BigDecimal.ZERO) != 0
                ? change.divide(previousClose, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal high = price.max(previousClose).multiply(BigDecimal.valueOf(1.01)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal low = price.min(previousClose).multiply(BigDecimal.valueOf(0.99)).setScale(2, RoundingMode.HALF_UP);

        return new StockResponse(
                meta.symbol(),
                meta.name(),
                price,
                change,
                changeRate,
                high,
                low,
                MockStockData.volumeOn(meta, today),
                LocalDateTime.now()
        );
    }

    public StockHistoryResponse getHistory(String symbol, int days) {
        if (alphaVantageConfig.mockEnabled()) {
            return getMockHistory(symbol, days);
        }

        try {
            JsonNode root = restClient.get()
                    .uri(alphaVantageConfig.baseUrl() + "?function=TIME_SERIES_DAILY&symbol={symbol}&apikey={key}",
                            symbol, alphaVantageConfig.apiKey())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode series = root != null ? root.get("Time Series (Daily)") : null;
            if (series == null) {
                throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
            }

            List<StockHistoryResponse.HistoryItem> history = new ArrayList<>();
            Iterator<String> dates = series.fieldNames();
            int count = 0;
            List<String> sortedDates = new ArrayList<>();
            dates.forEachRemaining(sortedDates::add);
            sortedDates.sort((a, b) -> b.compareTo(a));

            for (String date : sortedDates) {
                if (count >= days) break;
                BigDecimal close = new BigDecimal(series.get(date).path("4. close").asText());
                history.add(new StockHistoryResponse.HistoryItem(date, close));
                count++;
            }

            return new StockHistoryResponse(symbol.toUpperCase(), history);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Alpha Vantage 시세 히스토리 조회 실패", e);
            throw new CustomException(ErrorCode.STOCK_API_ERROR);
        }
    }

    private StockHistoryResponse getMockHistory(String symbol, int days) {
        if (!MockStockData.has(symbol)) {
            throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        }

        MockStockData.StockMeta meta = MockStockData.get(symbol);
        List<StockHistoryResponse.HistoryItem> history = new ArrayList<>();
        LocalDate cursor = LocalDate.now();
        int collected = 0;

        while (collected < days) {
            if (MockStockData.isBusinessDay(cursor)) {
                history.add(new StockHistoryResponse.HistoryItem(cursor.toString(), MockStockData.priceOn(meta, cursor)));
                collected++;
            }
            cursor = cursor.minusDays(1);
        }

        return new StockHistoryResponse(meta.symbol(), history);
    }

    public List<FavoriteStockResponse> getFavorites(Long userId) {
        return favoriteStockRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toFavoriteResponse)
                .toList();
    }

    private FavoriteStockResponse toFavoriteResponse(FavoriteStock fav) {
        BigDecimal currentPrice = null;
        BigDecimal profitLoss = null;
        BigDecimal profitLossRate = null;

        try {
            currentPrice = getStock(fav.getStockSymbol()).price();
        } catch (CustomException e) {
            log.warn("현재가 조회 실패: {}", fav.getStockSymbol());
        }

        if (currentPrice != null && fav.getQuantity() != null && fav.getAvgPrice() != null) {
            BigDecimal invested = fav.getAvgPrice().multiply(BigDecimal.valueOf(fav.getQuantity()));
            BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(fav.getQuantity()));
            profitLoss = currentValue.subtract(invested).setScale(2, RoundingMode.HALF_UP);
            profitLossRate = invested.compareTo(BigDecimal.ZERO) != 0
                    ? profitLoss.divide(invested, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        return new FavoriteStockResponse(
                fav.getStockSymbol(),
                fav.getStockName(),
                fav.getQuantity(),
                fav.getAvgPrice(),
                currentPrice,
                profitLoss,
                profitLossRate,
                fav.getCreatedAt()
        );
    }

    public NewsResponse getNews(String symbol) {
        if (alphaVantageConfig.mockEnabled()) {
            return getMockNews(symbol.toUpperCase());
        }

        try {
            JsonNode root = restClient.get()
                    .uri(alphaVantageConfig.baseUrl()
                            + "?function=NEWS_SENTIMENT&tickers={symbol}&limit=5&apikey={key}",
                            symbol, alphaVantageConfig.apiKey())
                    .retrieve()
                    .body(JsonNode.class);

            List<NewsResponse.NewsItem> items = new ArrayList<>();
            if (root != null && root.has("feed")) {
                for (JsonNode article : root.get("feed")) {
                    String sentiment = "Neutral";
                    JsonNode tickerSentiments = article.path("ticker_sentiment");
                    for (JsonNode ts : tickerSentiments) {
                        if (symbol.equalsIgnoreCase(ts.path("ticker").asText())) {
                            sentiment = ts.path("ticker_sentiment_label").asText("Neutral");
                            break;
                        }
                    }
                    items.add(new NewsResponse.NewsItem(
                            article.path("title").asText(),
                            article.path("url").asText(),
                            article.path("summary").asText(),
                            sentiment,
                            article.path("time_published").asText(),
                            article.path("source").asText()
                    ));
                    if (items.size() >= 5) break;
                }
            }
            return new NewsResponse(symbol.toUpperCase(), items);
        } catch (RestClientException e) {
            log.warn("Alpha Vantage 뉴스 조회 실패: {}", symbol);
            return getMockNews(symbol.toUpperCase());
        }
    }

    private NewsResponse getMockNews(String symbol) {
        List<NewsResponse.NewsItem> items = List.of(
                new NewsResponse.NewsItem(
                        symbol + " posts strong quarterly earnings beat",
                        "https://example.com/news/1",
                        symbol + " reported earnings per share above analyst expectations, driven by robust demand.",
                        "Bullish",
                        LocalDate.now().toString(),
                        "MarketWatch"
                ),
                new NewsResponse.NewsItem(
                        "Analysts raise price target for " + symbol,
                        "https://example.com/news/2",
                        "Several Wall Street analysts upgraded their outlook following recent product announcements.",
                        "Bullish",
                        LocalDate.now().minusDays(1).toString(),
                        "Bloomberg"
                ),
                new NewsResponse.NewsItem(
                        symbol + " faces macro headwinds amid rate uncertainty",
                        "https://example.com/news/3",
                        "Rising interest rates continue to weigh on growth stock valuations including " + symbol + ".",
                        "Bearish",
                        LocalDate.now().minusDays(2).toString(),
                        "Reuters"
                )
        );
        return new NewsResponse(symbol, items);
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

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP);

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
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    return new PortfolioSummaryResponse.HoldingWeight(
                            f.stockSymbol(), f.stockName(), value.setScale(2, RoundingMode.HALF_UP), pct);
                })
                .toList();

        return new PortfolioSummaryResponse(
                totalInvested.setScale(2, RoundingMode.HALF_UP),
                totalCurrentValue.setScale(2, RoundingMode.HALF_UP),
                totalProfitLoss,
                totalProfitLossRate,
                withHolding.size(),
                weights
        );
    }

    @Transactional
    public void updateHolding(Long userId, String symbol, UpdateHoldingRequest request) {
        FavoriteStock favorite = favoriteStockRepository.findByUserIdAndStockSymbol(userId, symbol.toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        favorite.updateHolding(request.quantity(), request.avgPrice());
    }

    @Transactional
    public void addFavorite(Long userId, FavoriteStockRequest request) {
        String symbol = request.stockSymbol().toUpperCase();

        if (favoriteStockRepository.existsByUserIdAndStockSymbol(userId, symbol)) {
            throw new CustomException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        favoriteStockRepository.save(FavoriteStock.builder()
                .userId(userId)
                .stockSymbol(symbol)
                .stockName(request.stockName())
                .build());
    }

    @Transactional
    public void removeFavorite(Long userId, String symbol) {
        FavoriteStock favorite = favoriteStockRepository.findByUserIdAndStockSymbol(userId, symbol.toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        favoriteStockRepository.delete(favorite);
    }
}
