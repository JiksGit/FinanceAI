package com.finance.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 기술적 지표 계산 유틸리티.
 * 모든 메서드는 closes 리스트가 과거→현재 순서로 정렬되어 있다고 가정.
 */
public final class TechnicalIndicator {

    private TechnicalIndicator() {}

    // ── 단순이동평균 (SMA) ───────────────────────────────────────────

    public static BigDecimal sma(List<BigDecimal> closes, int endInclusive, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endInclusive - period + 1; i <= endInclusive; i++) {
            sum = sum.add(closes.get(i));
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    // ── 지수이동평균 (EMA) ───────────────────────────────────────────

    public static List<BigDecimal> ema(List<BigDecimal> closes, int period) {
        List<BigDecimal> result = new ArrayList<>();
        if (closes.size() < period) return result;

        // 첫 EMA = SMA(period)
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(closes.get(i));
        }
        BigDecimal prev = sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        // period-1개는 계산 불가 (null 대신 생략) → index 0에 SMA 결과 넣음
        for (int i = 0; i < period - 1; i++) result.add(null);
        result.add(prev);

        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal oneMinusM = BigDecimal.ONE.subtract(multiplier);

        for (int i = period; i < closes.size(); i++) {
            prev = closes.get(i).multiply(multiplier)
                    .add(prev.multiply(oneMinusM))
                    .setScale(8, RoundingMode.HALF_UP);
            result.add(prev);
        }
        return result;
    }

    // ── RSI(14) ──────────────────────────────────────────────────────

    /**
     * @return RSI 값 (0~100), 계산 불가 시 null
     */
    public static BigDecimal rsi(List<BigDecimal> closes, int period) {
        if (closes.size() < period + 1) return null;

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        for (int i = closes.size() - period; i < closes.size(); i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            if (diff.compareTo(BigDecimal.ZERO) > 0) gainSum = gainSum.add(diff);
            else lossSum = lossSum.add(diff.abs());
        }

        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        // RSI = 100 - (100 / (1 + RS))
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP));
    }

    // ── MACD(12, 26, 9) ─────────────────────────────────────────────

    public record MacdResult(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {}

    /**
     * @return 최신 MACD 결과, 데이터 부족 시 null
     */
    public static MacdResult macd(List<BigDecimal> closes) {
        List<BigDecimal> ema12 = ema(closes, 12);
        List<BigDecimal> ema26 = ema(closes, 26);
        if (ema12.isEmpty() || ema26.isEmpty()) return null;

        // MACD line = EMA12 - EMA26 (EMA26가 null이 아닌 구간만)
        List<BigDecimal> macdLine = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            BigDecimal e12 = i < ema12.size() ? ema12.get(i) : null;
            BigDecimal e26 = i < ema26.size() ? ema26.get(i) : null;
            if (e12 == null || e26 == null) {
                macdLine.add(null);
            } else {
                macdLine.add(e12.subtract(e26).setScale(8, RoundingMode.HALF_UP));
            }
        }

        // 유효한 MACD 값만 모아 Signal(EMA9) 계산
        List<BigDecimal> validMacd = macdLine.stream().filter(v -> v != null).toList();
        if (validMacd.size() < 9) return null;

        List<BigDecimal> signalLine = ema(validMacd, 9);
        if (signalLine.isEmpty()) return null;

        BigDecimal lastMacd = validMacd.get(validMacd.size() - 1);
        BigDecimal lastSignal = signalLine.get(signalLine.size() - 1);
        if (lastSignal == null) return null;

        return new MacdResult(lastMacd, lastSignal,
                lastMacd.subtract(lastSignal).setScale(4, RoundingMode.HALF_UP));
    }

    /**
     * 직전 MACD 결과 (크로스 판단용)
     */
    public static MacdResult prevMacd(List<BigDecimal> closes) {
        if (closes.size() < 2) return null;
        return macd(closes.subList(0, closes.size() - 1));
    }
}
