package com.finance.dashboard.config;

import com.finance.dashboard.entity.StockSignal;
import com.finance.dashboard.repository.StockSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StockSignalRepository signalRepository;

    @Override
    @Transactional
    public void run(String... args) {
        removeUsSymbols();
        seedKoreanSignals();
    }

    private void removeUsSymbols() {
        List<StockSignal> usSignals = signalRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(s -> s.getStockSymbol().matches("[A-Z]{2,5}"))
                .toList();
        if (!usSignals.isEmpty()) {
            signalRepository.deleteAll(usSignals);
            log.info("미국 주식 시그널 {}건 삭제", usSignals.size());
        }
    }

    private void seedKoreanSignals() {
        // 이미 국내 시그널이 있으면 스킵
        boolean hasKorean = signalRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .anyMatch(s -> s.getStockSymbol().matches("\\d{6}"));
        if (hasKorean) return;

        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String twoDaysAgo = LocalDate.now().minusDays(2).toString();

        List<StockSignal> seeds = List.of(
                signal("005930", StockSignal.SignalType.BUY,   today,       "삼성전자 5일선이 20일선을 상향 돌파. 외국인 순매수 지속."),
                signal("000660", StockSignal.SignalType.BUY,   today,       "SK하이닉스 HBM 수요 급증. 골든크로스 발생."),
                signal("035420", StockSignal.SignalType.SELL,  yesterday,   "NAVER 단기 과매수 구간 진입. 데드크로스 경고."),
                signal("005380", StockSignal.SignalType.BUY,   yesterday,   "현대차 전기차 수출 증가. 5일선 돌파."),
                signal("207940", StockSignal.SignalType.SELL,  twoDaysAgo,  "삼성바이오로직스 차익 실현 구간. 이동평균 하향.")
        );

        for (StockSignal s : seeds) {
            if (signalRepository.findByStockSymbolAndSignalDateAndIndicator(
                    s.getStockSymbol(), s.getSignalDate(), s.getIndicator()).isEmpty()) {
                signalRepository.save(s);
            }
        }
        log.info("국내 주식 목업 시그널 {}건 삽입 완료", seeds.size());
    }

    private StockSignal signal(String code, StockSignal.SignalType type, String date, String explanation) {
        return StockSignal.builder()
                .stockSymbol(code)
                .signalType(type)
                .indicator("SMA5_SMA20_CROSS")
                .signalDate(date)
                .aiExplanation(explanation)
                .build();
    }
}
