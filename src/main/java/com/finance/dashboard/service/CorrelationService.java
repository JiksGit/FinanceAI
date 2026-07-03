package com.finance.dashboard.service;

import com.finance.dashboard.dto.response.CorrelationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelationService {

    // label → Yahoo Finance ticker
    private static final LinkedHashMap<String, String> ASSETS = new LinkedHashMap<>() {{
        put("달러/원",  "USDKRW=X");
        put("엔/원",    "JPYKRW=X");
        put("금",       "GC=F");
        put("은",       "SI=F");
        put("KOSPI",   "^KS11");
        put("KOSDAQ",  "^KQ11");
        put("삼성전자", "005930.KS");
        put("SK하이닉스","000660.KS");
    }};

    private final AssetDataService assetDataService;

    public CorrelationResponse getCorrelation(String range) {
        // 1. 각 자산 일별 종가 수집
        Map<String, Map<String, Double>> rawData = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : ASSETS.entrySet()) {
            rawData.put(e.getKey(), assetDataService.getDailyClose(e.getValue(), range));
        }

        // 2. 공통 날짜 교집합 (정렬)
        Set<String> commonDates = null;
        for (Map<String, Double> prices : rawData.values()) {
            if (prices.isEmpty()) continue;
            if (commonDates == null) commonDates = new TreeSet<>(prices.keySet());
            else commonDates.retainAll(prices.keySet());
        }
        if (commonDates == null || commonDates.size() < 10) {
            return new CorrelationResponse(List.of(), List.of(), List.of());
        }
        List<String> dates = new ArrayList<>(commonDates);

        // 3. 자산별 정규화 시계열 (첫날=100 기준)
        List<CorrelationResponse.AssetSeries> series = new ArrayList<>();
        Map<String, double[]> vectors = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Double>> entry : rawData.entrySet()) {
            String label = entry.getKey();
            Map<String, Double> prices = entry.getValue();
            if (prices.isEmpty()) continue;

            double[] raw = dates.stream()
                    .mapToDouble(d -> prices.getOrDefault(d, Double.NaN))
                    .toArray();

            // NaN 보간 (이전 값으로 채움)
            for (int i = 1; i < raw.length; i++) {
                if (Double.isNaN(raw[i])) raw[i] = raw[i - 1];
            }
            if (Double.isNaN(raw[0])) continue;

            // 정규화 (첫날 = 100)
            double base = raw[0];
            List<CorrelationResponse.DataPoint> points = new ArrayList<>();
            double[] normalized = new double[raw.length];
            for (int i = 0; i < raw.length; i++) {
                normalized[i] = raw[i] / base * 100.0;
                points.add(new CorrelationResponse.DataPoint(
                        dates.get(i),
                        Math.round(raw[i] * 100.0) / 100.0,
                        Math.round(normalized[i] * 100.0) / 100.0
                ));
            }
            series.add(new CorrelationResponse.AssetSeries(label, points));
            vectors.put(label, normalized);
        }

        // 4. Pearson 상관계수 행렬
        List<String> labels = new ArrayList<>(vectors.keySet());
        List<CorrelationResponse.CorrelationCell> matrix = new ArrayList<>();

        for (String a : labels) {
            for (String b : labels) {
                double r = pearson(vectors.get(a), vectors.get(b));
                matrix.add(new CorrelationResponse.CorrelationCell(a, b,
                        Math.round(r * 1000.0) / 1000.0));
            }
        }

        return new CorrelationResponse(labels, series, matrix);
    }

    private double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += x[i];
            sumY  += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }
        double num = n * sumXY - sumX * sumY;
        double den = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return den == 0 ? 0 : num / den;
    }
}
