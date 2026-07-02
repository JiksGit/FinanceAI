package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.dashboard.dto.response.MetalPriceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetalsService {

    private static final String BASE_URL = "https://api.gold-api.com/price/";
    private static final String USER_AGENT = "Mozilla/5.0";

    private final ExchangeRateService exchangeRateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<MetalPriceResponse> getMetalPrices() {
        BigDecimal usdKrw = resolveUsdKrw();

        MetalPriceResponse gold   = fetchMetal("XAU", "GOLD", "금", usdKrw);
        MetalPriceResponse silver = fetchMetal("XAG", "SILVER", "은", usdKrw);

        return List.of(gold, silver);
    }

    private MetalPriceResponse fetchMetal(String symbol, String name, String nameKr, BigDecimal usdKrw) {
        try {
            String url = BASE_URL + symbol;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("metals API 오류 [{}]: status={}", symbol, resp.statusCode());
                return empty(name, nameKr);
            }

            JsonNode node = objectMapper.readTree(resp.body());

            BigDecimal priceUsd   = bd(node, "price");
            // gold-api.com: ch = change amount, chp = change percent
            BigDecimal changeUsd  = bd(node, "ch");
            BigDecimal changeRate = bd(node, "chp");

            BigDecimal priceKrw      = priceUsd.multiply(usdKrw).setScale(0, RoundingMode.HALF_UP);
            BigDecimal priceGram24k  = bd(node, "price_gram_24k");

            BigDecimal prevClose = priceUsd.subtract(changeUsd);

            return new MetalPriceResponse(
                    name, nameKr, "oz",
                    priceUsd.setScale(2, RoundingMode.HALF_UP),
                    prevClose.setScale(2, RoundingMode.HALF_UP),
                    changeUsd.setScale(2, RoundingMode.HALF_UP),
                    changeRate.setScale(2, RoundingMode.HALF_UP),
                    priceKrw,
                    priceGram24k.setScale(2, RoundingMode.HALF_UP)
            );
        } catch (Exception e) {
            log.warn("metals 조회 실패 [{}]: {}", symbol, e.getMessage());
            return empty(name, nameKr);
        }
    }

    public String getRawJson(String symbol) {
        try {
            String url = BASE_URL + symbol;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return "status=" + resp.statusCode() + "\n" + resp.body();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private BigDecimal resolveUsdKrw() {
        try {
            return exchangeRateService.getTodayRates().rates().stream()
                    .filter(r -> "USD".equals(r.currency()))
                    .map(r -> r.rate())
                    .findFirst()
                    .orElse(BigDecimal.valueOf(1380));
        } catch (Exception e) {
            return BigDecimal.valueOf(1380);
        }
    }

    private BigDecimal bd(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return BigDecimal.ZERO;
        try { return new BigDecimal(n.asText()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private MetalPriceResponse empty(String name, String nameKr) {
        return new MetalPriceResponse(name, nameKr, "oz",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
