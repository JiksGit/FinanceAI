package com.finance.dashboard.dto.response;

import java.util.List;

public record CorrelationResponse(
        List<String> labels,
        List<AssetSeries> series,
        List<CorrelationCell> matrix
) {
    public record DataPoint(String date, double price, double normalized) {}
    public record AssetSeries(String label, List<DataPoint> data) {}
    public record CorrelationCell(String assetA, String assetB, double r) {}
}
