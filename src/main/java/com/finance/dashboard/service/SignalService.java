package com.finance.dashboard.service;

import com.finance.dashboard.config.SignalConfig;
import com.finance.dashboard.dto.response.SignalResponse;
import com.finance.dashboard.dto.response.StockHistoryResponse;
import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.entity.StockSignal;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.repository.FavoriteStockRepository;
import com.finance.dashboard.repository.KrxStockInfoRepository;
import com.finance.dashboard.repository.StockSignalRepository;
import com.finance.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private static final String IND_MA_CROSS = "SMA5_SMA20_CROSS";
    private static final String IND_RSI      = "RSI_14";
    private static final String IND_MACD     = "MACD_12_26_9";
    private static final String INDICATOR    = IND_MA_CROSS; // 하위 호환용
    private static final int SHORT_PERIOD = 5;
    private static final int LONG_PERIOD  = 20;
    private static final int RSI_PERIOD   = 14;
    private static final int HISTORY_DAYS = 60;
    /** 즐겨찾기한 사용자가 아직 없을 때 사용할 기본 관찰 목록 (KOSPI 시총 상위). */
    private static final List<String> DEFAULT_WATCHLIST = List.of(
            "005930", // 삼성전자
            "000660", // SK하이닉스
            "005380", // 현대차
            "035420", // NAVER
            "005490", // POSCO홀딩스
            "035720", // 카카오
            "051910", // LG화학
            "006400", // 삼성SDI
            "028260", // 삼성물산
            "207940"  // 삼성바이오로직스
    );

    private final StockService stockService;
    private final StockSignalRepository stockSignalRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final KrxStockInfoRepository stockInfoRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;
    private final EmailService emailService;
    private final SignalConfig signalConfig;

    @Scheduled(cron = "${signal.cron}", zone = "Asia/Seoul")
    public void generateDailySignals() {
        if (!signalConfig.schedulerEnabled()) {
            log.debug("인스턴스 내장 스케줄러가 비활성화되어 있습니다 (signal.scheduler-enabled=false). 외부 트리거(EventBridge 등)를 사용하세요.");
            return;
        }
        log.info("일일 시그널 생성 시작");
        generateSignals();
    }

    @Transactional
    public List<SignalResponse> generateSignals() {
        List<SignalResponse> generated = new ArrayList<>();
        List<String> trackedSymbols = resolveTrackedSymbols();

        for (String symbol : trackedSymbols) {
            try {
                StockHistoryResponse history = stockService.getHistory(symbol, HISTORY_DAYS);
                List<StockHistoryResponse.HistoryItem> items = new ArrayList<>(history.history());
                java.util.Collections.reverse(items); // 과거→현재 정렬
                if (items.size() < LONG_PERIOD + 1) continue;

                List<BigDecimal> closes = items.stream()
                        .map(StockHistoryResponse.HistoryItem::price).toList();
                String today = items.get(items.size() - 1).date();

                // MA 크로스
                saveIfNew(symbol, today, IND_MA_CROSS,
                        computeMaCross(symbol, closes, today), generated);

                // RSI
                saveIfNew(symbol, today, IND_RSI,
                        computeRsi(symbol, closes, today), generated);

                // MACD
                saveIfNew(symbol, today, IND_MACD,
                        computeMacdCross(symbol, closes, today), generated);

            } catch (CustomException e) {
                log.warn("시그널 계산 스킵 ({}): {}", symbol, e.getMessage());
            } catch (Exception e) {
                log.warn("시그널 오류 ({}): {}", symbol, e.getMessage());
            }
        }

        log.info("일일 시그널 생성 완료: {}건 (추적 종목 {}개)", generated.size(), trackedSymbols.size());
        return generated;
    }

    private void saveIfNew(String symbol, String date, String indicator,
                           Optional<DetectedSignal> detected, List<SignalResponse> generated) {
        detected.ifPresent(signal -> {
            if (stockSignalRepository.findByStockSymbolAndSignalDateAndIndicator(symbol, date, indicator).isPresent()) return;
            String explanation = explain(signal);
            StockSignal saved = stockSignalRepository.save(StockSignal.builder()
                    .stockSymbol(signal.stockSymbol())
                    .signalType(signal.type())
                    .indicator(indicator)
                    .signalDate(signal.signalDate())
                    .aiExplanation(explanation)
                    .build());
            generated.add(SignalResponse.from(saved, resolveStockName(symbol)));
            notifySubscribers(saved);
        });
    }

    private List<String> resolveTrackedSymbols() {
        List<String> favoritedSymbols = favoriteStockRepository.findDistinctStockSymbols();
        return favoritedSymbols.isEmpty() ? DEFAULT_WATCHLIST : favoritedSymbols;
    }

    public List<SignalResponse> getRecentSignals() {
        return stockSignalRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(s -> SignalResponse.from(s, resolveStockName(s.getStockSymbol())))
                .toList();
    }

    private String resolveStockName(String stockCode) {
        return stockInfoRepository.findById(stockCode)
                .map(info -> info.getStockName())
                .orElse(stockCode);
    }

    public List<SignalResponse> getMySignals(Long userId) {
        List<String> symbols = favoriteStockRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FavoriteStock::getStockSymbol)
                .toList();

        if (symbols.isEmpty()) {
            return List.of();
        }

        return stockSignalRepository.findByStockSymbolInOrderByCreatedAtDesc(symbols).stream()
                .map(s -> SignalResponse.from(s, resolveStockName(s.getStockSymbol())))
                .toList();
    }

    private record DetectedSignal(String stockSymbol, StockSignal.SignalType type, String signalDate,
                                   String detail) {}

    // ── MA5/MA20 크로스 ─────────────────────────────────────────────
    private Optional<DetectedSignal> computeMaCross(String symbol, List<BigDecimal> closes, String date) {
        int last = closes.size() - 1;
        if (last < LONG_PERIOD) return Optional.empty();

        BigDecimal todayShort = TechnicalIndicator.sma(closes, last, SHORT_PERIOD);
        BigDecimal todayLong  = TechnicalIndicator.sma(closes, last, LONG_PERIOD);
        BigDecimal prevShort  = TechnicalIndicator.sma(closes, last - 1, SHORT_PERIOD);
        BigDecimal prevLong   = TechnicalIndicator.sma(closes, last - 1, LONG_PERIOD);

        boolean golden = prevShort.compareTo(prevLong) <= 0 && todayShort.compareTo(todayLong) > 0;
        boolean dead   = prevShort.compareTo(prevLong) >= 0 && todayShort.compareTo(todayLong) < 0;

        if (golden) return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.BUY, date,
                "MA5(" + todayShort.setScale(0, RoundingMode.HALF_UP) + ") 골든크로스 MA20(" + todayLong.setScale(0, RoundingMode.HALF_UP) + ")"));
        if (dead)   return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.SELL, date,
                "MA5(" + todayShort.setScale(0, RoundingMode.HALF_UP) + ") 데드크로스 MA20(" + todayLong.setScale(0, RoundingMode.HALF_UP) + ")"));
        return Optional.empty();
    }

    // ── RSI(14) ─────────────────────────────────────────────────────
    private Optional<DetectedSignal> computeRsi(String symbol, List<BigDecimal> closes, String date) {
        BigDecimal rsi = TechnicalIndicator.rsi(closes, RSI_PERIOD);
        if (rsi == null) return Optional.empty();

        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0)
            return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.BUY, date,
                    "RSI(" + rsi.setScale(1, RoundingMode.HALF_UP) + ") 과매도 구간"));
        if (rsi.compareTo(BigDecimal.valueOf(70)) > 0)
            return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.SELL, date,
                    "RSI(" + rsi.setScale(1, RoundingMode.HALF_UP) + ") 과매수 구간"));
        return Optional.empty();
    }

    // ── MACD(12,26,9) ────────────────────────────────────────────────
    private Optional<DetectedSignal> computeMacdCross(String symbol, List<BigDecimal> closes, String date) {
        TechnicalIndicator.MacdResult cur  = TechnicalIndicator.macd(closes);
        TechnicalIndicator.MacdResult prev = TechnicalIndicator.prevMacd(closes);
        if (cur == null || prev == null) return Optional.empty();

        boolean bullish = prev.macd().compareTo(prev.signal()) <= 0 && cur.macd().compareTo(cur.signal()) > 0;
        boolean bearish = prev.macd().compareTo(prev.signal()) >= 0 && cur.macd().compareTo(cur.signal()) < 0;

        if (bullish) return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.BUY, date,
                "MACD(" + cur.macd().setScale(2, RoundingMode.HALF_UP) + ") 상향 돌파 Signal(" + cur.signal().setScale(2, RoundingMode.HALF_UP) + ")"));
        if (bearish) return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.SELL, date,
                "MACD(" + cur.macd().setScale(2, RoundingMode.HALF_UP) + ") 하향 돌파 Signal(" + cur.signal().setScale(2, RoundingMode.HALF_UP) + ")"));
        return Optional.empty();
    }

    private String explain(DetectedSignal signal) {
        String typeLabel = signal.type() == StockSignal.SignalType.BUY ? "매수" : "매도";

        if (openAiService.isEnabled()) {
            String prompt = """
                    종목: %s
                    시그널: %s (%s)
                    지표 상세: %s
                    기준일: %s

                    위 기술적 시그널을 일반 투자자가 이해하기 쉽게 2~3문장 한국어로 설명해줘.
                    투자 추천이 아니라 시그널의 의미를 설명하는 톤으로, 과도한 확신은 피해줘.
                    """.formatted(signal.stockSymbol(), typeLabel, signal.detail(), signal.detail(), signal.signalDate());

            String aiResult = openAiService.chat(
                    "너는 주식 기술적 지표를 쉽게 설명하는 금융 어시스턴트야.",
                    prompt
            );
            if (aiResult != null && !aiResult.isBlank()) {
                return aiResult.trim();
            }
        }

        String direction = signal.type() == StockSignal.SignalType.BUY ? "강세" : "약세";
        return "%s에서 %s 시그널이 감지되었습니다: %s. 단기 추세가 %s로 전환될 가능성을 나타냅니다."
                .formatted(signal.stockSymbol(), typeLabel, signal.detail(), direction);
    }

    private void notifySubscribers(StockSignal signal) {
        List<FavoriteStock> subscribers = favoriteStockRepository.findByStockSymbol(signal.getStockSymbol());
        Set<Long> userIds = new LinkedHashSet<>();
        subscribers.forEach(fav -> userIds.add(fav.getUserId()));

        for (Long userId : userIds) {
            userRepository.findById(userId)
                    .ifPresent(user -> emailService.sendSignalAlert(user.getEmail(), signal));
        }
    }
}
