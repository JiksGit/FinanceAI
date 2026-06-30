package com.finance.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alpha Vantage 무료 플랜은 25건/일로 제한되어 개발 중에는 이 Mock 데이터를 사용한다.
 * 동일 종목·날짜 조합은 항상 같은 값을 반환하도록 결정적으로 가격을 생성한다.
 */
class MockStockData {

    record StockMeta(String symbol, String name, double basePrice, double volatility) {
    }

    private static final Map<String, StockMeta> STOCKS = new LinkedHashMap<>();

    static {
        register("AAPL", "Apple Inc.", 185.0, 0.03);
        register("TSLA", "Tesla, Inc.", 245.0, 0.05);
        register("MSFT", "Microsoft Corporation", 410.0, 0.025);
        register("GOOGL", "Alphabet Inc.", 152.0, 0.03);
        register("AMZN", "Amazon.com, Inc.", 178.0, 0.035);
        register("NVDA", "NVIDIA Corporation", 880.0, 0.045);
    }

    private static void register(String symbol, String name, double basePrice, double volatility) {
        STOCKS.put(symbol, new StockMeta(symbol, name, basePrice, volatility));
    }

    static boolean has(String symbol) {
        return STOCKS.containsKey(symbol.toUpperCase());
    }

    static StockMeta get(String symbol) {
        return STOCKS.get(symbol.toUpperCase());
    }

    static Map<String, StockMeta> search(String keyword) {
        String lower = keyword.toLowerCase();
        Map<String, StockMeta> result = new LinkedHashMap<>();
        for (StockMeta meta : STOCKS.values()) {
            if (meta.symbol().toLowerCase().contains(lower) || meta.name().toLowerCase().contains(lower)) {
                result.put(meta.symbol(), meta);
            }
        }
        return result;
    }

    static BigDecimal priceOn(StockMeta meta, LocalDate date) {
        long epochDay = date.toEpochDay();
        int symbolSeed = meta.symbol().hashCode();
        double trend = Math.sin((epochDay + symbolSeed % 30) / 9.0) * meta.volatility();
        double noise = pseudoRandom(symbolSeed, epochDay) * meta.volatility() * 0.4;
        double price = meta.basePrice() * (1 + trend + noise);
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal previousClose(StockMeta meta, LocalDate date) {
        LocalDate previousDay = date.minusDays(1);
        // 주말이면 직전 영업일(금요일)로 이동
        if (previousDay.getDayOfWeek().getValue() == 6) {
            previousDay = previousDay.minusDays(1);
        } else if (previousDay.getDayOfWeek().getValue() == 7) {
            previousDay = previousDay.minusDays(2);
        }
        return priceOn(meta, previousDay);
    }

    static long volumeOn(StockMeta meta, LocalDate date) {
        long epochDay = date.toEpochDay();
        int symbolSeed = meta.symbol().hashCode();
        double base = 30_000_000 + Math.abs(symbolSeed) % 20_000_000;
        double factor = 0.7 + Math.abs(pseudoRandom(symbolSeed, epochDay)) * 0.6;
        return (long) (base * factor);
    }

    private static double pseudoRandom(int seed, long day) {
        long x = seed * 2654435761L + day * 40503L;
        x = (x ^ (x >> 13)) * 0x5DEECE66DL;
        double normalized = ((x >> 16) & 0xFFFFFF) / (double) 0xFFFFFF;
        return (normalized - 0.5) * 2;
    }

    static boolean isBusinessDay(LocalDate date) {
        int dow = date.getDayOfWeek().getValue();
        return dow != 6 && dow != 7;
    }
}
