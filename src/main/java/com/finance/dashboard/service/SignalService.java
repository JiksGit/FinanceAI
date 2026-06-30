package com.finance.dashboard.service;

import com.finance.dashboard.dto.response.SignalResponse;
import com.finance.dashboard.dto.response.StockHistoryResponse;
import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.entity.StockSignal;
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
    /** 데모용 추적 대상. 실서비스라면 사용자가 즐겨찾기한 모든 종목으로 확장. */
    private static final List<String> TRACKED_SYMBOLS = List.of("AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "NVDA");

    private final StockService stockService;
    private final StockSignalRepository stockSignalRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;
    private final EmailService emailService;

    @Scheduled(cron = "${signal.cron}", zone = "Asia/Seoul")
    public void generateDailySignals() {
        log.info("일일 시그널 생성 시작");
        generateSignals();
    }

    @Transactional
    public List<SignalResponse> generateSignals() {
        List<SignalResponse> generated = new ArrayList<>();

        for (String symbol : TRACKED_SYMBOLS) {
            computeSignal(symbol).ifPresent(signal -> {
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

        log.info("일일 시그널 생성 완료: {}건", generated.size());
        return generated;
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
