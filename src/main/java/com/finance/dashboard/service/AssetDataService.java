package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yahoo Finance에서 일별 종가를 가져오는 공용 서비스.
 * key = "yyyy-MM-dd", value = 종가
 */
@Slf4j
@Service
public class AssetDataService {

    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=%s";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** ticker: Yahoo Finance 심볼, range: 1mo / 3mo / 6mo / 1y / 2y */
    public Map<String, Double> getDailyClose(String ticker, String range) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            String url = String.format(YAHOO_URL, ticker, range);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("Yahoo [{}] 오류: {}", ticker, resp.statusCode());
                return result;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode r = root.path("chart").path("result").get(0);
            if (r == null || r.isMissingNode()) return result;

            JsonNode timestamps = r.path("timestamp");
            JsonNode closes = r.path("indicators").path("quote").get(0).path("close");

            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode c = closes.get(i);
                if (c == null || c.isNull()) continue;
                String date = LocalDate.ofInstant(
                        Instant.ofEpochSecond(timestamps.get(i).asLong()), KST).format(DATE_FMT);
                result.put(date, Math.round(c.asDouble() * 100.0) / 100.0);
            }
        } catch (Exception e) {
            log.warn("Yahoo [{}] 조회 실패: {}", ticker, e.getMessage());
        }
        return result;
    }
}
