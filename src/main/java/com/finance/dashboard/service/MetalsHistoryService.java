package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.dashboard.dto.response.MetalHistoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MetalsHistoryService {

    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=%s";
    private static final Map<String, String> SYMBOL_MAP = Map.of(
            "XAU", "GC=F",
            "XAG", "SI=F"
    );
    private static final Map<String, String> NAME_KR = Map.of(
            "XAU", "금",
            "XAG", "은"
    );
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param symbol XAU 또는 XAG
     * @param range  1mo, 3mo, 6mo, 1y, 2y
     */
    public MetalHistoryResponse getHistory(String symbol, String range) {
        String ticker = SYMBOL_MAP.getOrDefault(symbol.toUpperCase(), "GC=F");
        String nameKr = NAME_KR.getOrDefault(symbol.toUpperCase(), symbol);

        try {
            String url = String.format(YAHOO_URL, ticker, range);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() != 200) {
                log.warn("Yahoo Finance 오류 [{}]: {}", ticker, resp.statusCode());
                return empty(symbol, nameKr);
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode result = root.path("chart").path("result").get(0);
            if (result == null || result.isMissingNode()) return empty(symbol, nameKr);

            JsonNode timestamps = result.path("timestamp");
            JsonNode quote = result.path("indicators").path("quote").get(0);
            if (quote == null) return empty(symbol, nameKr);

            JsonNode opens  = quote.path("open");
            JsonNode highs  = quote.path("high");
            JsonNode lows   = quote.path("low");
            JsonNode closes = quote.path("close");

            List<MetalHistoryResponse.HistoryItem> items = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i).isNull()) continue;
                long epoch = timestamps.get(i).asLong();
                String date = LocalDate.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.of("Asia/Seoul"))
                        .format(DATE_FMT);
                items.add(new MetalHistoryResponse.HistoryItem(
                        date,
                        safeDouble(opens.get(i)),
                        safeDouble(highs.get(i)),
                        safeDouble(lows.get(i)),
                        safeDouble(closes.get(i))
                ));
            }

            return new MetalHistoryResponse(symbol.toUpperCase(), nameKr, "USD", items);
        } catch (Exception e) {
            log.warn("귀금속 히스토리 조회 실패 [{}]: {}", symbol, e.getMessage());
            return empty(symbol, nameKr);
        }
    }

    private double safeDouble(JsonNode n) {
        if (n == null || n.isNull()) return 0.0;
        return Math.round(n.asDouble() * 100.0) / 100.0;
    }

    private MetalHistoryResponse empty(String symbol, String nameKr) {
        return new MetalHistoryResponse(symbol.toUpperCase(), nameKr, "USD", List.of());
    }
}
