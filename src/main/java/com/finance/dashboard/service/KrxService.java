package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.dashboard.config.KrxConfig;
import com.finance.dashboard.entity.KrxDailyPrice;
import com.finance.dashboard.entity.KrxStockInfo;
import com.finance.dashboard.repository.KrxDailyPriceRepository;
import com.finance.dashboard.repository.KrxStockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrxService {

    private static final String BLD_STOCK_LIST = "dbms/MDC/STAT/standard/MDCSTAT01901";
    private static final String BLD_PRICE_HISTORY = "dbms/MDC/STAT/standard/MDCSTAT01701";
    private static final DateTimeFormatter KRX_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HISTORY_DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final KrxConfig krxConfig;
    private final KrxStockInfoRepository stockInfoRepository;
    private final KrxDailyPriceRepository dailyPriceRepository;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .defaultHeader(HttpHeaders.REFERER, "http://data.krx.co.kr/")
            .defaultHeader(HttpHeaders.CONTENT_TYPE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
            .build();

    // ── 최신 거래일 조회 ────────────────────────────────────────

    public LocalDate resolveLatestTradingDate() {
        // DB에 있으면 바로 반환
        Optional<LocalDate> cached = dailyPriceRepository.findLatestTradeDate();
        if (cached.isPresent() && !cached.get().isBefore(LocalDate.now().minusDays(7))) {
            return cached.get();
        }
        // 없으면 오늘부터 최대 7일 앞으로 탐색
        LocalDate cursor = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY
                    && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                if (existsOnKrx(cursor)) return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private boolean existsOnKrx(LocalDate date) {
        try {
            JsonNode root = callKrx(BLD_STOCK_LIST, "STK", date, null, null);
            JsonNode output = root != null ? root.get("output") : null;
            return output != null && output.isArray() && !output.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ── 전종목 시세 로드 (KOSPI + KOSDAQ) ──────────────────────

    @Transactional
    public void loadAllPrices(LocalDate date) {
        if (dailyPriceRepository.existsByTradeDate(date)) {
            log.info("KRX 시세 이미 캐시됨: {}", date);
            return;
        }
        log.info("KRX 전종목 시세 로드 시작: {}", date);
        loadMarket("STK", "KOSPI", date);
        loadMarket("KSQ", "KOSDAQ", date);
        log.info("KRX 전종목 시세 로드 완료: {}", date);
    }

    private void loadMarket(String mktId, String marketName, LocalDate date) {
        try {
            JsonNode root = callKrx(BLD_STOCK_LIST, mktId, date, null, null);
            JsonNode output = root != null ? root.get("output") : null;
            if (output == null || !output.isArray()) return;

            List<KrxDailyPrice> prices = new ArrayList<>();
            for (JsonNode row : output) {
                String stockCode = row.path("ISU_SRT_CD").asText("").trim();
                String isinCode = row.path("ISU_CD").asText("").trim();
                String stockName = row.path("ISU_ABBRV").asText("").trim();
                String sector = row.path("SECT_TP_NM").asText("").trim();

                if (stockCode.isBlank() || stockName.isBlank()) continue;

                Long closePrice = parseLong(row.path("TDD_CLSPRC").asText());
                Long openPrice = parseLong(row.path("TDD_OPNPRC").asText());
                Long highPrice = parseLong(row.path("TDD_HGPRC").asText());
                Long lowPrice = parseLong(row.path("TDD_LWPRC").asText());
                Long priceChange = parseLong(row.path("CMPPREVDD_PRC").asText());
                BigDecimal changeRate = parseBigDecimal(row.path("FLUC_RT").asText());
                Long volume = parseLong(row.path("ACC_TRDVOL").asText());
                Long marketCap = parseLong(row.path("MKTCAP").asText());

                // 종목 정보 upsert
                stockInfoRepository.findById(stockCode).ifPresentOrElse(
                        info -> info.update(isinCode, stockName, marketName, sector),
                        () -> stockInfoRepository.save(KrxStockInfo.builder()
                                .stockCode(stockCode)
                                .isinCode(isinCode)
                                .stockName(stockName)
                                .market(marketName)
                                .sector(sector)
                                .build())
                );

                prices.add(KrxDailyPrice.builder()
                        .stockCode(stockCode)
                        .stockName(stockName)
                        .market(marketName)
                        .tradeDate(date)
                        .closePrice(closePrice)
                        .openPrice(openPrice)
                        .highPrice(highPrice)
                        .lowPrice(lowPrice)
                        .priceChange(priceChange)
                        .changeRate(changeRate)
                        .volume(volume)
                        .marketCap(marketCap)
                        .build());
            }

            dailyPriceRepository.saveAll(prices);
            log.info("{} 종목 저장 완료: {}건", marketName, prices.size());

        } catch (Exception e) {
            log.error("{} 시세 로드 실패: {}", marketName, e.getMessage());
        }
    }

    // ── 단일 종목 현재가 ────────────────────────────────────────

    public Optional<KrxDailyPrice> getCurrentPrice(String stockCode) {
        LocalDate date = resolveLatestTradingDate();
        loadAllPrices(date);
        return dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, date);
    }

    // ── 종목 검색 ───────────────────────────────────────────────

    public List<KrxStockInfo> searchStocks(String keyword) {
        // DB에 종목 정보 없으면 먼저 로드
        if (stockInfoRepository.count() == 0) {
            loadAllPrices(resolveLatestTradingDate());
        }
        return stockInfoRepository.findByStockNameContainingOrStockCodeContaining(keyword, keyword);
    }

    // ── 가격 히스토리 ───────────────────────────────────────────

    public List<KrxDailyPrice> getPriceHistory(String stockCode, int days) {
        LocalDate to = resolveLatestTradingDate();
        LocalDate from = to.minusDays(days * 2L); // 주말/공휴일 감안해 넉넉하게

        List<KrxDailyPrice> cached = dailyPriceRepository
                .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to);

        if (cached.size() >= days) {
            return cached.subList(0, days);
        }

        // DB 부족 → KRX에서 히스토리 직접 조회
        return fetchHistoryFromKrx(stockCode, from, to, days);
    }

    @Transactional
    private List<KrxDailyPrice> fetchHistoryFromKrx(String stockCode, LocalDate from, LocalDate to, int days) {
        Optional<KrxStockInfo> info = stockInfoRepository.findById(stockCode);
        if (info.isEmpty()) return List.of();

        String isinCode = info.get().getIsinCode();
        String stockName = info.get().getStockName();
        String market = info.get().getMarket();

        try {
            JsonNode root = callKrx(BLD_PRICE_HISTORY, null, null, isinCode,
                    from.format(KRX_DATE_FMT) + "|" + to.format(KRX_DATE_FMT));
            JsonNode output = root != null ? root.get("output") : null;
            if (output == null || !output.isArray()) return List.of();

            List<KrxDailyPrice> result = new ArrayList<>();
            for (JsonNode row : output) {
                String rawDate = row.path("TRD_DD").asText("").replace("/", "");
                if (rawDate.isBlank()) continue;

                LocalDate tradeDate = LocalDate.parse(rawDate, KRX_DATE_FMT);
                Long closePrice = parseLong(row.path("TDD_CLSPRC").asText());
                Long openPrice = parseLong(row.path("TDD_OPNPRC").asText());
                Long highPrice = parseLong(row.path("TDD_HGPRC").asText());
                Long lowPrice = parseLong(row.path("TDD_LWPRC").asText());
                Long priceChange = parseLong(row.path("CMPPREVDD_PRC").asText());
                BigDecimal changeRate = parseBigDecimal(row.path("FLUC_RT").asText());
                Long volume = parseLong(row.path("ACC_TRDVOL").asText());

                KrxDailyPrice price = KrxDailyPrice.builder()
                        .stockCode(stockCode)
                        .stockName(stockName)
                        .market(market)
                        .tradeDate(tradeDate)
                        .closePrice(closePrice)
                        .openPrice(openPrice)
                        .highPrice(highPrice)
                        .lowPrice(lowPrice)
                        .priceChange(priceChange)
                        .changeRate(changeRate)
                        .volume(volume)
                        .build();

                // 중복 체크 후 저장
                if (dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate).isEmpty()) {
                    dailyPriceRepository.save(price);
                }
                result.add(price);

                if (result.size() >= days) break;
            }
            return result;

        } catch (Exception e) {
            log.error("KRX 히스토리 조회 실패 [{}]: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    // ── 시가총액 TOP N ──────────────────────────────────────────

    public List<KrxDailyPrice> getTopByMarketCap(String market, int limit) {
        LocalDate date = resolveLatestTradingDate();
        loadAllPrices(date);
        if (market == null || market.isBlank()) {
            return dailyPriceRepository.findTopByMarketCapOnDate(date, limit);
        }
        return dailyPriceRepository.findTopByMarketCapOnDateAndMarket(date, market, limit);
    }

    // ── KRX HTTP 호출 ───────────────────────────────────────────

    private JsonNode callKrx(String bld, String mktId, LocalDate trdDd,
                              String isinCd, String dateRange) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("bld", bld);
        form.add("locale", "ko_KR");
        form.add("csvxls_isNo", "false");

        if (mktId != null) form.add("mktId", mktId);
        if (trdDd != null) form.add("trdDd", trdDd.format(KRX_DATE_FMT));
        if (isinCd != null) form.add("isuCd", isinCd);
        if (dateRange != null) {
            String[] parts = dateRange.split("\\|");
            form.add("strtDd", parts[0]);
            form.add("endDd", parts[1]);
        }

        return restClient.post()
                .uri(krxConfig.baseUrl())
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    // ── 파싱 유틸 ───────────────────────────────────────────────

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return 0L;
        try {
            return Long.parseLong(raw.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.replaceAll("[,\\s%]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
