package com.finance.dashboard.service;

import com.finance.dashboard.config.EximConfig;
import com.finance.dashboard.dto.response.EximRateItem;
import com.finance.dashboard.dto.response.ExchangeRateHistoryResponse;
import com.finance.dashboard.dto.response.ExchangeRateResponse;
import com.finance.dashboard.entity.ExchangeRateCache;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.ExchangeRateCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXIM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CACHE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> TARGET_CURRENCIES = Set.of("USD", "JPY", "EUR", "CNY");
    private static final int MAX_LOOKBACK_DAYS = 10;
    private static final String NO_DATA_SENTINEL = "NONE";

    private final ExchangeRateCacheRepository exchangeRateCacheRepository;
    private final EximConfig eximConfig;
    private final RestClient restClient = RestClient.create();

    public ExchangeRateResponse getTodayRates() {
        LocalDate today = LocalDate.now(KST);

        DatedRates latest = findLatestAvailable(today, MAX_LOOKBACK_DAYS)
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_API_ERROR));

        DatedRates previous = findLatestAvailable(latest.date().minusDays(1), MAX_LOOKBACK_DAYS)
                .orElse(null);

        List<ExchangeRateResponse.RateItem> items = latest.rates().entrySet().stream()
                .map(entry -> {
                    String currency = entry.getKey();
                    BigDecimal rate = entry.getValue();
                    BigDecimal prevRate = previous != null ? previous.rates().get(currency) : null;

                    BigDecimal change = prevRate != null ? rate.subtract(prevRate) : BigDecimal.ZERO;
                    BigDecimal changeRate = (prevRate != null && prevRate.compareTo(BigDecimal.ZERO) != 0)
                            ? change.divide(prevRate, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return new ExchangeRateResponse.RateItem(
                            currency,
                            rate,
                            change.setScale(2, RoundingMode.HALF_UP),
                            changeRate.setScale(2, RoundingMode.HALF_UP)
                    );
                })
                .toList();

        return new ExchangeRateResponse(latest.date().format(CACHE_DATE_FORMAT), items);
    }

    public ExchangeRateHistoryResponse getHistory(String currency, int days) {
        String normalizedCurrency = currency.toUpperCase();
        if (!TARGET_CURRENCIES.contains(normalizedCurrency)) {
            throw new CustomException(ErrorCode.EXCHANGE_API_ERROR);
        }

        List<ExchangeRateHistoryResponse.HistoryItem> history = new java.util.ArrayList<>();
        LocalDate cursor = LocalDate.now(KST);
        int maxAttempts = days * 2 + MAX_LOOKBACK_DAYS;

        for (int attempt = 0; attempt < maxAttempts && history.size() < days; attempt++) {
            Map<String, BigDecimal> rates = getOrFetch(cursor);
            BigDecimal rate = rates.get(normalizedCurrency);
            if (rate != null) {
                history.add(new ExchangeRateHistoryResponse.HistoryItem(cursor.format(CACHE_DATE_FORMAT), rate));
            }
            cursor = cursor.minusDays(1);
        }

        return new ExchangeRateHistoryResponse(normalizedCurrency, history);
    }

    private record DatedRates(LocalDate date, Map<String, BigDecimal> rates) {
    }

    private java.util.Optional<DatedRates> findLatestAvailable(LocalDate startDate, int maxLookback) {
        LocalDate cursor = startDate;
        for (int i = 0; i < maxLookback; i++) {
            Map<String, BigDecimal> rates = getOrFetch(cursor);
            if (!rates.isEmpty()) {
                return java.util.Optional.of(new DatedRates(cursor, rates));
            }
            cursor = cursor.minusDays(1);
        }
        return java.util.Optional.empty();
    }

    @Transactional
    public Map<String, BigDecimal> getOrFetch(LocalDate date) {
        String baseDate = date.format(CACHE_DATE_FORMAT);

        List<ExchangeRateCache> cached = exchangeRateCacheRepository.findByBaseDate(baseDate);
        if (!cached.isEmpty()) {
            // 캐시에 NONE 센티널만 있으면(휴일/주말로 데이터 없음) toRateMap이 빈 Map을 반환한다.
            return toRateMap(cached);
        }

        List<EximRateItem> fetched = fetchFromExim(date);
        if (fetched.isEmpty()) {
            exchangeRateCacheRepository.save(ExchangeRateCache.builder()
                    .currencyCode(NO_DATA_SENTINEL)
                    .rate(BigDecimal.ZERO)
                    .baseDate(baseDate)
                    .build());
            return Map.of();
        }

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (EximRateItem item : fetched) {
            String currency = normalizeCurrencyUnit(item.curUnit());
            if (!TARGET_CURRENCIES.contains(currency)) {
                continue;
            }
            BigDecimal rate = parseRate(item.dealBasR());
            result.put(currency, rate);

            exchangeRateCacheRepository.save(ExchangeRateCache.builder()
                    .currencyCode(currency)
                    .rate(rate)
                    .baseDate(baseDate)
                    .build());
        }

        return result;
    }

    private List<EximRateItem> fetchFromExim(LocalDate date) {
        if (eximConfig.apiKey() == null || eximConfig.apiKey().isBlank()) {
            log.warn("EXIM_API_KEY가 설정되지 않았습니다.");
            throw new CustomException(ErrorCode.EXCHANGE_API_ERROR);
        }

        try {
            List<EximRateItem> items = restClient.get()
                    .uri(eximConfig.baseUrl() + "?authkey={authkey}&searchdate={date}&data=AP01",
                            eximConfig.apiKey(), date.format(EXIM_DATE_FORMAT))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<EximRateItem>>() {
                    });

            if (items == null) {
                return List.of();
            }

            return items.stream()
                    .filter(item -> item.result() == 1)
                    .toList();
        } catch (RestClientException e) {
            log.error("한국수출입은행 환율 API 호출 실패", e);
            throw new CustomException(ErrorCode.EXCHANGE_API_ERROR);
        }
    }

    private Map<String, BigDecimal> toRateMap(List<ExchangeRateCache> cached) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (ExchangeRateCache cache : cached) {
            if (TARGET_CURRENCIES.contains(cache.getCurrencyCode())) {
                result.put(cache.getCurrencyCode(), cache.getRate());
            }
        }
        return result;
    }

    private String normalizeCurrencyUnit(String curUnit) {
        int parenIndex = curUnit.indexOf('(');
        String unit = parenIndex > 0 ? curUnit.substring(0, parenIndex) : curUnit;
        // EXIM은 위안화를 오프쇼어 표기인 CNH로 제공한다.
        return "CNH".equals(unit) ? "CNY" : unit;
    }

    private BigDecimal parseRate(String dealBasR) {
        return new BigDecimal(dealBasR.replace(",", ""));
    }
}
