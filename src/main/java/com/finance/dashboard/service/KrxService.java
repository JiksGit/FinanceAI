package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.dashboard.config.KrxConfig;
import com.finance.dashboard.dto.response.MarketIndexResponse;
import com.finance.dashboard.dto.response.StockDetailResponse;
import com.finance.dashboard.entity.KrxDailyPrice;
import com.finance.dashboard.entity.KrxStockInfo;
import com.finance.dashboard.repository.KrxDailyPriceRepository;
import com.finance.dashboard.repository.KrxStockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrxService {

    // Naver Finance PC 사이트 (sosok=0:KOSPI, sosok=1:KOSDAQ)
    private static final String NAVER_MARKET_URL =
            "https://finance.naver.com/sise/sise_market_sum.nhn?sosok=%d&page=%d";
    // 개별 종목 기본 정보 (JSON)
    private static final String NAVER_BASIC_URL =
            "https://m.stock.naver.com/api/stock/%s/basic";
    // 개별 종목 캔들 (JSON)
    private static final String NAVER_CANDLE_URL =
            "https://m.stock.naver.com/api/stock/%s/candle/day?startDate=%s&endDate=%s";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final DateTimeFormatter KRX_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KrxConfig krxConfig;
    private final KrxStockInfoRepository stockInfoRepository;
    private final KrxDailyPriceRepository dailyPriceRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean loadingInProgress = new AtomicBoolean(false);

    // ── 최신 거래일 ─────────────────────────────────────────────

    public LocalDate resolveLatestTradingDate() {
        Optional<LocalDate> cached = dailyPriceRepository.findLatestTradeDate();
        if (cached.isPresent() && !cached.get().isBefore(LocalDate.now().minusDays(7))) {
            return cached.get();
        }
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

    // ── 전종목 시세 로드 (Naver Finance HTML 스크래핑) ──────────

    public boolean isCacheReady(LocalDate date) {
        return dailyPriceRepository.countByTradeDate(date) > 100;
    }

    /** 동기 로드 (시그널 생성 등 내부용) */
    @Transactional
    public void loadAllPrices(LocalDate date) {
        if (isCacheReady(date)) {
            log.info("시세 이미 캐시됨: {}", date);
            return;
        }
        if (!loadingInProgress.compareAndSet(false, true)) {
            log.info("시세 로드 이미 진행 중: {}", date);
            return;
        }
        try {
            log.info("네이버 전종목 시세 로드 시작: {}", date);
            loadMarketFromNaver("KOSPI", 0, date);
            loadMarketFromNaver("KOSDAQ", 1, date);
            log.info("네이버 전종목 시세 로드 완료: {}", date);
        } finally {
            loadingInProgress.set(false);
        }
    }

    /** 비동기 로드 (CompletableFuture - @Async 자기호출 우회) */
    public void loadAllPricesAsync(LocalDate date) {
        CompletableFuture.runAsync(() -> loadAllPrices(date));
    }

    private void loadMarketFromNaver(String market, int sosok, LocalDate date) {
        int page = 1;
        int totalLoaded = 0;
        List<KrxDailyPrice> batch = new ArrayList<>();

        while (true) {
            try {
                String url = String.format(NAVER_MARKET_URL, sosok, page);
                log.info("{} {}페이지 스크래핑: {}", market, page, url);

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .timeout(10_000)
                        .get();

                // 테이블 rows (빈 tr 포함 - 짝수 행이 실제 데이터)
                Elements rows = doc.select("table.type_2 tr");
                boolean hasData = false;

                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() < 10) continue;

                    // 종목명 셀에 링크가 있어야 함
                    Element nameCell = cells.get(1);
                    Element link = nameCell.selectFirst("a[href]");
                    if (link == null) continue;

                    String href = link.attr("href"); // /item/main.naver?code=005930
                    String code = extractCode(href);
                    if (code == null || code.isBlank()) continue;

                    String name = link.text().trim();
                    long closePrice = parseLong(cells.get(2).text());
                    long priceChange = parseChange(cells.get(3));
                    BigDecimal changeRate = parseBD(cells.get(4).text());
                    long marketCap = parseMarketCap(cells.get(6).text()); // 억원 단위
                    long volume = parseLong(cells.get(9).text());

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
                            .closePrice(closePrice).openPrice(0L)
                            .highPrice(0L).lowPrice(0L)
                            .priceChange(priceChange).changeRate(changeRate)
                            .volume(volume).marketCap(marketCap)
                            .build());

                    hasData = true;
                }

                if (!hasData) {
                    log.info("{} 로드 완료: 총 {}건", market, totalLoaded);
                    break;
                }

                totalLoaded += batch.size() - (totalLoaded > 0 ? totalLoaded : 0);
                log.info("{} {}페이지 완료, 누적 {}건", market, page, batch.size());
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
        if (!isCacheReady(date)) loadAllPricesAsync(date);
        return dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, date);
    }

    // ── 종목 검색 ─────────────────────────────────────────────────

    public List<KrxStockInfo> searchStocks(String keyword) {
        if (stockInfoRepository.count() == 0) {
            loadAllPricesAsync(resolveLatestTradingDate());
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

        // Naver Finance 일별 시세 HTML 스크래핑
        List<KrxDailyPrice> result = new ArrayList<>();
        int page = 1;

        while (result.size() < days && page <= 10) {
            try {
                String url = String.format(
                        "https://finance.naver.com/item/sise_day.nhn?code=%s&page=%d",
                        stockCode, page);
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .header("Referer", "https://finance.naver.com/item/main.naver?code=" + stockCode)
                        .timeout(10_000)
                        .get();

                Elements rows = doc.select("table.type2 tr");
                boolean hasData = false;

                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() < 7) continue;
                    String dateStr = cells.get(0).text().trim();
                    if (!dateStr.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) continue;

                    LocalDate tradeDate = LocalDate.parse(dateStr.replace(".", "-"));
                    if (tradeDate.isBefore(from)) { result.add(null); break; } // sentinel to stop

                    long close = parseLong(cells.get(1).text());
                    long open  = parseLong(cells.get(3).text());
                    long high  = parseLong(cells.get(4).text());
                    long low   = parseLong(cells.get(5).text());
                    long vol   = parseLong(cells.get(6).text());

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
                    hasData = true;
                    if (result.size() >= days) break;
                }

                if (!hasData) break;
                page++;
            } catch (Exception e) {
                log.error("히스토리 스크래핑 실패 [{}] {}p: {}", stockCode, page, e.getMessage());
                break;
            }
        }

        // sentinel null 제거
        result.removeIf(p -> p == null);
        return result.subList(0, Math.min(result.size(), days));
    }

    // ── 종목 상세 (투자정보 포함) ─────────────────────────────────

    public StockDetailResponse getStockDetail(String stockCode) {
        LocalDate date = resolveLatestTradingDate();

        // DB 캐시에서 먼저 조회; 없으면 Naver 개별 종목 페이지에서 직접 스크래핑
        KrxDailyPrice price = dailyPriceRepository
                .findByStockCodeAndTradeDate(stockCode, date)
                .orElseGet(() -> scrapeIndividualStock(stockCode, date));

        if (price == null) throw new RuntimeException("종목 데이터 없음: " + stockCode);
        KrxStockInfo info = stockInfoRepository.findById(stockCode).orElse(null);

        NaverFinanceInfo naver = scrapeNaverFinanceInfo(stockCode);

        return new StockDetailResponse(
                price.getStockCode(),
                price.getStockName(),
                price.getMarket(),
                info != null ? info.getSector() : null,
                price.getClosePrice(),
                price.getPriceChange(),
                price.getChangeRate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getVolume(),
                price.getMarketCap() != null ? price.getMarketCap() : 0L,
                naver.per(),
                naver.eps(),
                naver.pbr(),
                naver.bps(),
                naver.roe(),
                naver.dividendYield(),
                naver.high52w(),
                naver.low52w(),
                naver.sharesOutstanding(),
                naver.foreignRatio()
        );
    }

    /**
     * 시가총액 상위권 외 종목: Naver Finance 개별 종목 시세 페이지에서 당일 가격 스크래핑
     */
    @Transactional
    private KrxDailyPrice scrapeIndividualStock(String stockCode, LocalDate date) {
        try {
            String url = "https://finance.naver.com/item/main.naver?code=" + stockCode;
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(10_000)
                    .get();

            // 종목명
            Element nameEl = doc.selectFirst("div.wrap_company h2 a");
            String stockName = nameEl != null ? nameEl.text().trim() : stockCode;

            // 현재가
            Element priceEl = doc.selectFirst("p.no_today .blind");
            long closePrice = priceEl != null ? parseLong(priceEl.text()) : 0L;

            // 등락
            Element changeEl = doc.selectFirst("p.no_exday .blind");
            long priceChange = 0L;
            BigDecimal changeRate = BigDecimal.ZERO;
            if (changeEl != null) {
                boolean negative = doc.select("p.no_exday .ico.down").size() > 0;
                priceChange = parseLong(changeEl.text());
                if (negative) priceChange = -priceChange;
            }

            // 거래량
            long volume = 0L;
            for (Element tr : doc.select("table.no_info tr")) {
                if (tr.text().contains("거래량")) {
                    Element td = tr.selectFirst("td");
                    if (td != null) volume = parseLong(td.text());
                    break;
                }
            }

            // 시장 (KOSPI/KOSDAQ)
            String market = "KOSPI";
            Element marketEl = doc.selectFirst("div.wrap_company .exchange_name");
            if (marketEl != null && marketEl.text().contains("KOSDAQ")) market = "KOSDAQ";

            // KrxStockInfo upsert
            final String mkt = market;
            stockInfoRepository.findById(stockCode).ifPresentOrElse(
                    info -> {},
                    () -> stockInfoRepository.save(KrxStockInfo.builder()
                            .stockCode(stockCode).isinCode(null)
                            .stockName(stockName).market(mkt).sector(null)
                            .build())
            );

            KrxDailyPrice entity = KrxDailyPrice.builder()
                    .stockCode(stockCode).stockName(stockName).market(market)
                    .tradeDate(date)
                    .closePrice(closePrice).openPrice(0L).highPrice(0L).lowPrice(0L)
                    .priceChange(priceChange).changeRate(changeRate)
                    .volume(volume).marketCap(null)
                    .build();

            // DB에 저장하지 않음 - existsByTradeDate 트리거 방지 (bulk loadAllPrices 간섭 차단)
            return entity;
        } catch (Exception e) {
            log.warn("개별 종목 스크래핑 실패 [{}]: {}", stockCode, e.getMessage());
            return null;
        }
    }

    private record NaverFinanceInfo(
            BigDecimal per, BigDecimal eps, BigDecimal pbr, BigDecimal bps,
            BigDecimal roe, BigDecimal dividendYield,
            long high52w, long low52w, long sharesOutstanding, BigDecimal foreignRatio) {
        static NaverFinanceInfo empty() {
            return new NaverFinanceInfo(null, null, null, null, null, null, 0L, 0L, 0L, null);
        }
    }

    private NaverFinanceInfo scrapeNaverFinanceInfo(String stockCode) {
        // 1차: Naver 모바일 JSON API (PER/EPS/PBR/BPS 직접 제공)
        BigDecimal per = null, eps = null, pbr = null, bps = null, roe = null, dividendYield = null;
        try {
            JsonNode basic = getJson(String.format(NAVER_BASIC_URL, stockCode));
            if (basic != null) {
                per = jsonBD(basic, "per");
                eps = jsonBD(basic, "eps");
                pbr = jsonBD(basic, "pbr");
                bps = jsonBD(basic, "bps");
                log.debug("모바일 API 투자지표 [{}]: per={} eps={} pbr={} bps={}", stockCode, per, eps, pbr, bps);
            }
        } catch (Exception e) {
            log.debug("모바일 API 실패 [{}]: {}", stockCode, e.getMessage());
        }

        // 2차: PC main 페이지 HTML → 52주, 상장주식수, 외국인소진률, PER fallback
        long high52w = 0L, low52w = 0L, shares = 0L;
        BigDecimal foreignRatio = null;
        try {
            Document main = Jsoup.connect("https://finance.naver.com/item/main.naver?code=" + stockCode)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(10_000)
                    .get();

            // 52주 고저 (.blind 텍스트로 찾기)
            for (Element tr : main.select("table tr")) {
                String txt = tr.text();
                if (txt.contains("52주") && (txt.contains("최고") || txt.contains("고가"))) {
                    Elements spans = tr.select(".blind");
                    if (spans.size() >= 2) {
                        high52w = parseLong(spans.get(0).text());
                        low52w  = parseLong(spans.get(1).text());
                    } else {
                        Elements tds2 = tr.select("td");
                        if (tds2.size() >= 2) {
                            high52w = parseLong(tds2.get(0).text());
                            low52w  = parseLong(tds2.get(1).text());
                        }
                    }
                    if (high52w > 0) break;
                }
            }

            // 상장주식수
            for (Element tr : main.select("table tr")) {
                if (tr.text().contains("상장주식수")) {
                    Element td = tr.selectFirst("td");
                    if (td != null) shares = parseLong(td.text());
                    break;
                }
            }

            // 외국인소진률
            for (Element tr : main.select("table tr")) {
                if (tr.text().contains("외국인소진률") || tr.text().contains("외인소진")) {
                    Element td = tr.selectFirst("td");
                    if (td != null) foreignRatio = parseBD(td.text());
                    break;
                }
            }

            // PER/EPS fallback: th/td 페어링 (행 단위)
            if (per == null) {
                java.util.Map<String, String> data = new java.util.HashMap<>();
                for (Element tr : main.select("table tr")) {
                    Elements ths = tr.select("th");
                    Elements tds2 = tr.select("td");
                    for (int i = 0; i < ths.size() && i < tds2.size(); i++) {
                        String key = ths.get(i).text().trim().replaceAll("\\s+", "");
                        String val = tds2.get(i).text().trim().replaceAll("[^0-9.\\-]", "");
                        if (!key.isBlank() && !val.isBlank()) data.putIfAbsent(key, val);
                    }
                }
                if (per == null) per = parseOrNull(data.getOrDefault("PER", data.get("PER배율")));
                if (eps == null) eps = parseOrNull(data.getOrDefault("EPS", data.get("EPS원")));
                if (pbr == null) pbr = parseOrNull(data.getOrDefault("PBR", data.get("PBR배율")));
                if (bps == null) bps = parseOrNull(data.getOrDefault("BPS", data.get("BPS원")));
                if (roe == null) roe = parseOrNull(data.getOrDefault("ROE", data.get("ROE%")));
                if (dividendYield == null) dividendYield = parseOrNull(data.get("배당수익률"));
                log.debug("HTML fallback 투자지표 [{}]: {}", stockCode, data);
            }
        } catch (Exception e) {
            log.warn("투자정보 HTML 스크래핑 실패 [{}]: {}", stockCode, e.getMessage());
        }

        return new NaverFinanceInfo(per, eps, pbr, bps, roe, dividendYield,
                high52w, low52w, shares, foreignRatio);
    }

    private BigDecimal jsonBD(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || n.asText().equals("-") || n.asText().isBlank()) return null;
        try { return new BigDecimal(n.asText().replaceAll("[^0-9.\\-]", "")); }
        catch (Exception e) { return null; }
    }

    private BigDecimal parseOrNull(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        try { return new BigDecimal(raw); } catch (Exception e) { return null; }
    }

    // ── 시가총액 TOP N ────────────────────────────────────────────

    public List<KrxDailyPrice> getTopByMarketCap(String market, int limit) {
        LocalDate date = resolveLatestTradingDate();
        // 캐시 미준비 시 백그라운드 로드 트리거 후 즉시 반환 (로딩 중 빈 목록)
        if (!isCacheReady(date)) {
            loadAllPricesAsync(date);
        }
        PageRequest page = PageRequest.of(0, limit);
        if (market == null || market.isBlank()) {
            return dailyPriceRepository.findByTradeDateOrderByMarketCapDesc(date, page);
        }
        return dailyPriceRepository.findByTradeDateAndMarketOrderByMarketCapDesc(date, market, page);
    }

    // ── KOSPI/KOSDAQ 지수 ──────────────────────────────────────────

    /**
     * 네이버 금융 지수 페이지에서 KOSPI/KOSDAQ 현재값·등락 스크래핑
     * URL: https://finance.naver.com/sise/sise_index.naver?code=KOSPI
     */
    public List<MarketIndexResponse> getMarketIndices() {
        List<MarketIndexResponse> result = new ArrayList<>();
        for (String[] indexInfo : new String[][]{{"KOSPI", "KOSPI"}, {"KOSDAQ", "KOSDAQ"}}) {
            String code = indexInfo[0];
            String name = indexInfo[1];
            try {
                String url = "https://finance.naver.com/sise/sise_index.naver?code=" + code;
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .timeout(8_000)
                        .get();

                // 현재 지수값
                Element nowEl = doc.selectFirst("#now_value");
                if (nowEl == null) nowEl = doc.selectFirst(".num.today");
                BigDecimal current = nowEl != null ? parseBD(nowEl.text()) : BigDecimal.ZERO;

                // 등락 (절대값 + 방향)
                Element changeEl = doc.selectFirst("#change_value");
                BigDecimal change = BigDecimal.ZERO;
                if (changeEl != null) {
                    boolean negative = doc.select(".now_down").size() > 0
                            || doc.select("img[src*=down]").size() > 0
                            || doc.select("img[src*=fall]").size() > 0;
                    change = parseBD(changeEl.text());
                    if (negative) change = change.negate();
                }

                // 등락률
                Element rateEl = doc.selectFirst("#change_rate");
                BigDecimal changeRate = rateEl != null ? parseBD(rateEl.text()) : BigDecimal.ZERO;
                if (change.compareTo(BigDecimal.ZERO) < 0) changeRate = changeRate.negate();

                // 거래량·거래대금
                long volume = 0L, tradingValue = 0L;
                for (Element tr : doc.select("table.tb_type1 tr")) {
                    String text = tr.text();
                    if (text.contains("거래량")) {
                        Element td = tr.selectFirst("td");
                        if (td != null) volume = parseLong(td.text());
                    }
                    if (text.contains("거래대금")) {
                        Element td = tr.selectFirst("td");
                        if (td != null) tradingValue = parseLong(td.text());
                    }
                }

                result.add(new MarketIndexResponse(name, current, change, changeRate, volume, tradingValue));
            } catch (Exception e) {
                log.warn("지수 스크래핑 실패 [{}]: {}", code, e.getMessage());
                result.add(new MarketIndexResponse(name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));
            }
        }
        return result;
    }

    // ── 디버그용 raw JSON ─────────────────────────────────────────

    public String getRawJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://m.stock.naver.com/")
                    .GET()
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return "status=" + resp.statusCode() + "\n" + resp.body();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // ── HTTP GET → JsonNode ───────────────────────────────────────

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://m.stock.naver.com/")
                    .GET()
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("API 비정상 응답: {} → {}", url, resp.statusCode());
                return null;
            }
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.error("API 호출 실패 [{}]: {}", url, e.getMessage());
            return null;
        }
    }

    // ── 파싱 유틸 ─────────────────────────────────────────────────

    private String extractCode(String href) {
        // /item/main.naver?code=005930
        int idx = href.indexOf("code=");
        if (idx < 0) return null;
        String code = href.substring(idx + 5).split("&")[0].trim();
        return code.length() == 6 ? code : null;
    }

    private long parseChange(Element cell) {
        // Naver Finance는 ▼/▲ 이미지 태그나 클래스로 방향 표시
        String text = cell.text();
        String imgSrc = "";
        Element img = cell.selectFirst("img[src]");
        if (img != null) imgSrc = img.attr("src").toLowerCase();

        // "fall" or "down" in img src → 하락, "up" or "rise" → 상승
        boolean negative = imgSrc.contains("fall") || imgSrc.contains("down")
                || text.contains("▼") || text.contains("-");

        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return 0L;
        try {
            long val = Long.parseLong(digits);
            return negative ? -val : val;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 네이버 시가총액: "4,709,810" (억원 단위) → long
     */
    private long parseMarketCap(String raw) {
        return parseLong(raw);
    }

    private long parseLong(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return 0L;
        try {
            return Long.parseLong(raw.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal parseBD(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
