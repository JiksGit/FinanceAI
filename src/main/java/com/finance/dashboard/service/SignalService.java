package com.finance.dashboard.service;

import com.finance.dashboard.config.SignalConfig;
import com.finance.dashboard.dto.response.SignalResponse;
import com.finance.dashboard.dto.response.StockHistoryResponse;
import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.entity.StockSignal;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.repository.FavoriteStockRepository;
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

    private static final String INDICATOR = "SMA5_SMA20_CROSS";
    private static final int SHORT_PERIOD = 5;
    private static final int LONG_PERIOD = 20;
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
            Optional<DetectedSignal> detected;
            try {
                detected = computeSignal(symbol);
            } catch (CustomException e) {
                log.warn("시그널 계산 스킵 ({}): {}", symbol, e.getMessage());
                continue;
            }

            detected.ifPresent(signal -> {
                boolean alreadyExists = stockSignalRepository
                        .findByStockSymbolAndSignalDateAndIndicator(signal.stockSymbol(), signal.signalDate(), INDICATOR)
                        .isPresent();
                if (alreadyExists) {
                    return;
                }

                String explanation = explain(signal);

                StockSignal saved = stockSignalRepository.save(StockSignal.builder()
                        .stockSymbol(signal.stockSymbol())
                        .signalType(signal.type())
                        .indicator(INDICATOR)
                        .signalDate(signal.signalDate())
                        .aiExplanation(explanation)
                        .build());

                generated.add(SignalResponse.from(saved));
                notifySubscribers(saved);
            });
        }

        log.info("일일 시그널 생성 완료: {}건 (추적 종목 {}개)", generated.size(), trackedSymbols.size());
        return generated;
    }

    private List<String> resolveTrackedSymbols() {
        List<String> favoritedSymbols = favoriteStockRepository.findDistinctStockSymbols();
        return favoritedSymbols.isEmpty() ? DEFAULT_WATCHLIST : favoritedSymbols;
    }

    public List<SignalResponse> getRecentSignals() {
        return stockSignalRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(SignalResponse::from)
                .toList();
    }

    public List<SignalResponse> getMySignals(Long userId) {
        List<String> symbols = favoriteStockRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FavoriteStock::getStockSymbol)
                .toList();

        if (symbols.isEmpty()) {
            return List.of();
        }

        return stockSignalRepository.findByStockSymbolInOrderByCreatedAtDesc(symbols).stream()
                .map(SignalResponse::from)
                .toList();
    }

    private record DetectedSignal(String stockSymbol, StockSignal.SignalType type, String signalDate,
                                   BigDecimal shortMa, BigDecimal longMa) {
    }

    private Optional<DetectedSignal> computeSignal(String symbol) {
        StockHistoryResponse history = stockService.getHistory(symbol, LONG_PERIOD + 2);
        List<StockHistoryResponse.HistoryItem> items = new ArrayList<>(history.history());
        // getHistory는 최신순이므로 과거->현재 순으로 뒤집는다.
        java.util.Collections.reverse(items);

        if (items.size() < LONG_PERIOD + 1) {
            return Optional.empty();
        }

        List<BigDecimal> closes = items.stream().map(StockHistoryResponse.HistoryItem::price).toList();
        int last = closes.size() - 1;

        BigDecimal todayShort = average(closes, last, SHORT_PERIOD);
        BigDecimal todayLong = average(closes, last, LONG_PERIOD);
        BigDecimal prevShort = average(closes, last - 1, SHORT_PERIOD);
        BigDecimal prevLong = average(closes, last - 1, LONG_PERIOD);

        boolean goldenCross = prevShort.compareTo(prevLong) <= 0 && todayShort.compareTo(todayLong) > 0;
        boolean deadCross = prevShort.compareTo(prevLong) >= 0 && todayShort.compareTo(todayLong) < 0;

        String today = items.get(last).date();

        if (goldenCross) {
            return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.BUY, today, todayShort, todayLong));
        }
        if (deadCross) {
            return Optional.of(new DetectedSignal(symbol, StockSignal.SignalType.SELL, today, todayShort, todayLong));
        }
        return Optional.empty();
    }

    private BigDecimal average(List<BigDecimal> closes, int endIndexInclusive, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndexInclusive - period + 1; i <= endIndexInclusive; i++) {
            sum = sum.add(closes.get(i));
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private String explain(DetectedSignal signal) {
        String typeLabel = signal.type() == StockSignal.SignalType.BUY ? "골든크로스(매수)" : "데드크로스(매도)";

        if (openAiService.isEnabled()) {
            String prompt = """
                    종목: %s
                    시그널: %s
                    5일 이동평균선: %s
                    20일 이동평균선: %s
                    기준일: %s

                    위 기술적 시그널을 일반 투자자가 이해하기 쉽게 3문장 이내 한국어로 설명해줘.
                    투자 추천이 아니라 시그널의 의미를 설명하는 톤으로, 과도한 확신은 피해줘.
                    """.formatted(signal.stockSymbol(), typeLabel, signal.shortMa(), signal.longMa(), signal.signalDate());

            String aiResult = openAiService.chat(
                    "너는 주식 기술적 지표를 쉽게 설명하는 금융 어시스턴트야.",
                    prompt
            );
            if (aiResult != null && !aiResult.isBlank()) {
                return aiResult.trim();
            }
        }

        return ruleBasedExplanation(signal, typeLabel);
    }

    private String ruleBasedExplanation(DetectedSignal signal, String typeLabel) {
        if (signal.type() == StockSignal.SignalType.BUY) {
            return "%s의 5일 이동평균선(%s)이 20일 이동평균선(%s)을 상향 돌파했습니다(%s). 단기 추세가 강세로 전환되는 신호로 해석됩니다."
                    .formatted(signal.stockSymbol(), signal.shortMa(), signal.longMa(), typeLabel);
        }
        return "%s의 5일 이동평균선(%s)이 20일 이동평균선(%s)을 하향 돌파했습니다(%s). 단기 추세가 약세로 전환되는 신호로 해석됩니다."
                .formatted(signal.stockSymbol(), signal.shortMa(), signal.longMa(), typeLabel);
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
