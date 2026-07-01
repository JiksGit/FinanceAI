package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.dashboard.config.KrxConfig;
import com.finance.dashboard.entity.KrxDailyPrice;
import com.finance.dashboard.entity.KrxStockInfo;
import com.finance.dashboard.repository.KrxDailyPriceRepository;
import com.finance.dashboard.repository.KrxStockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrxService {

    private static final String NAVER_LIST_URL =
            "https://m.stock.naver.com/api/index/%s/stocks?pageSize=100&page=%d&type=marketValue";
    private static final String NAVER_BASIC_URL =
            "https://m.stock.naver.com/api/stock/%s/basic";
    private static final String NAVER_CANDLE_URL =
            "https://m.stock.naver.com/api/stock/%s/candle/day?startDate=%s&endDate=%s";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";
    private static final DateTimeFormatter KRX_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // KrxConfig는 유지 (application.yml 호환성)
    private final KrxConfig krxConfig;
    private final KrxStockInfoRepository stockInfoRepository;
    private final KrxDailyPriceRepository dailyPriceRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 최신 거래일 ─────────────────────────────────────────────

    public LocalDate resolveLatestTradingDate() {
        Optional<LocalDate> cached = dailyPriceRepository.findLatestTradeDate();
        if (cached.isPresent() && !cached.get().isBefore(LocalDate.now().minusDays(7))) {
            return cached.get();
        }
        // 15:30 KST 이전이면 오늘 데이터 미확정 → 어제부터 탐색
        LocalTime nowKst = LocalTime.now(ZoneId.of("Asia/Seoul"));
        LocalDate start = nowKst.isBefore(LocalTime.of(15, 30))
                ? LocalDate.now().minusDays(1) : LocalDate.now();

        LocalDate cursor = start;
        for (int i = 0; i < 10; i++) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY
                    && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    // ── 전종목 시세 로드 ─────────────────────────────────────────

    @Transactional
    public void loadAllPrices(LocalDate date) {
        if (dailyPriceRepository.existsByTradeDate(date)) {
            log.info("네이버 시세 이미 캐시됨: {}", date);
            return;
        }
        log.info("네이버 전종목 시세 로드 시작: {}", date);
        loadMarketFromNaver("KOSPI", date);
        loadMarketFromNaver("KOSDAQ", date);
        log.info("네이버 전종목 시세 로드 완료: {}", date);
    }

    private void loadMarketFromNaver(String market, LocalDate date) {
        int page = 1;
        int totalLoaded = 0;
        List<KrxDailyPrice> batch = new ArrayList<>();

        while (true) {
            try {
                String url = String.format(NAVER_LIST_URL, market, page);
                JsonNode root = getJson(url);
                if (root == null) break;

                JsonNode stocks = root.path("stocks");
                if (!stocks.isArray() || stocks.isEmpty()) break;

                for (JsonNode s : stocks) {
                    String code = s.path("itemCode").asText("").trim();
                    String name = s.path("stockName").asText("").trim();
                    if (code.isBlank()) continue;

                    long closePrice = parseLong(s.path("closePrice").asText());
                    long openPrice  = parseLong(s.path("openPrice").asText());
                    long highPrice  = parseLong(s.path("highPrice").asText());
                    long lowPrice   = parseLong(s.path("lowPrice").asText());
                    long change     = parseLong(s.path("compareToPreviousClosePrice").asText());
                    BigDecimal rate = parseBD(s.path("fluctuationsRatio").asText());
                    long volume     = parseLong(s.path("accumulatedTradingVolume").asText());
                    long marketCap  = parseLong(s.path("marketValue").asText());

                    // 종목 정보 upsert
                    stockInfoRepository.findById(code).ifPresentOrElse(
                            info -> info.update(null, name, market, null),
                            () -> stockInfoRepository.save(KrxStockInfo.builder()
                                    .stockCode(code).isinCode(null)
                                    .stockName(name).market(market).sector(null)
                                    .build())
                    );

                    batch.add(KrxDailyPrice.builder()
                            .stockCode(code).stockName(name).market(market)
                            .tradeDate(date)
                            .closePrice(closePrice).openPrice(openPrice)
                            .highPrice(highPrice).lowPrice(lowPrice)
                            .priceChange(change).changeRate(rate)
                            .volume(volume).marketCap(marketCap)
                            .build());
                }

                totalLoaded += stocks.size();
                log.info("{} 로드 중: {}페이지, 누적 {}건", market, page, totalLoaded);

                // 마지막 페이지 판단
                int totalCount = root.path("totalCount").asInt(0);
                if (totalLoaded >= totalCount) break;
                page++;

            } catch (Exception e) {
                log.error("{} {}페이지 로드 실패: {}", market, page, e.getMessage());
                break;
            }
        }

        if (!batch.isEmpty()) {
            dailyPriceRepository.saveAll(batch);
            log.info("{} 저장 완료: {}건", market, batch.size());
        }
    }

    // ── 단일 종목 현재가 ─────────────────────────────────────────

    public Optional<KrxDailyPrice> getCurrentPrice(String stockCode) {
        LocalDate date = resolveLatestTradingDate();
        loadAllPrices(date);
        return dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, date);
    }

    // ── 종목 검색 ─────────────────────────────────────────────────

    public List<KrxStockInfo> searchStocks(String keyword) {
        if (stockInfoRepository.count() == 0) {
            loadAllPrices(resolveLatestTradingDate());
        }
        return stockInfoRepository
                .findByStockNameContainingOrStockCodeContaining(keyword, keyword);
    }

    // ── 가격 히스토리 ─────────────────────────────────────────────

    public List<KrxDailyPrice> getPriceHistory(String stockCode, int days) {
        LocalDate to   = resolveLatestTradingDate();
        LocalDate from = to.minusDays(days * 2L);

        List<KrxDailyPrice> cached = dailyPriceRepository
                .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to);
        if (cached.size() >= days) return cached.subList(0, days);

        return fetchHistoryFromNaver(stockCode, from, to, days);
    }

    @Transactional
    private List<KrxDailyPrice> fetchHistoryFromNaver(String stockCode,
                                                       LocalDate from, LocalDate to, int days) {
        Optional<KrxStockInfo> info = stockInfoRepository.findById(stockCode);
        if (info.isEmpty()) return List.of();

        String stockName = info.get().getStockName();
        String market    = info.get().getMarket();

        try {
            String url = String.format(NAVER_CANDLE_URL,
                    stockCode, from.format(KRX_DATE_FMT), to.format(KRX_DATE_FMT));
            JsonNode root = getJson(url);
            if (root == null || !root.isArray()) return List.of();

            List<KrxDailyPrice> result = new ArrayList<>();
            for (JsonNode row : root) {
                long dateNum = row.path("localDate").asLong(0);
                if (dateNum == 0) continue;

                String dateStr = String.valueOf(dateNum);
                LocalDate tradeDate = LocalDate.parse(dateStr, KRX_DATE_FMT);
                long close  = row.path("closePrice").asLong(0);
                long open   = row.path("openPrice").asLong(0);
                long high   = row.path("highPrice").asLong(0);
                long low    = row.path("lowPrice").asLong(0);
                long vol    = row.path("accumulatedTradingVolume").asLong(0);

                KrxDailyPrice price = KrxDailyPrice.builder()
                        .stockCode(stockCode).stockName(stockName).market(market)
                        .tradeDate(tradeDate)
                        .closePrice(close).openPrice(open).highPrice(high).lowPrice(low)
                        .priceChange(0L).changeRate(BigDecimal.ZERO)
                        .volume(vol).marketCap(null)
                        .build();

                if (dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate).isEmpty()) {
                    dailyPriceRepository.save(price);
                }
                result.add(price);
                if (result.size() >= days) break;
            }
            return result;
        } catch (Exception e) {
            log.error("네이버 히스토리 조회 실패 [{}]: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    // ── 시가총액 TOP N ────────────────────────────────────────────

    public List<KrxDailyPrice> getTopByMarketCap(String market, int limit) {
        LocalDate date = resolveLatestTradingDate();
        loadAllPrices(date);
        if (market == null || market.isBlank()) {
            return dailyPriceRepository.findTopByMarketCapOnDate(date, limit);
        }
        return dailyPriceRepository.findTopByMarketCapOnDateAndMarket(date, market, limit);
    }

    // ── HTTP GET → JsonNode ───────────────────────────────────────

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://m.stock.naver.com/")
                    .GET()
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("네이버 API 비정상 응답: {} → {}", url, resp.statusCode());
                return null;
            }
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.error("네이버 API 호출 실패 [{}]: {}", url, e.getMessage());
            return null;
        }
    }

    // ── 파싱 유틸 ─────────────────────────────────────────────────

    private long parseLong(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return 0L;
        try {
            return Long.parseLong(raw.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal parseBD(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.replaceAll("[,\\s%]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
