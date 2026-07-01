package com.finance.dashboard.dto.response;

import java.util.List;

public record NewsResponse(
        String symbol,
        List<NewsItem> news
) {
    public record NewsItem(
            String title,
            String url,
            String summary,
            String sentiment,   // Bullish / Bearish / Neutral
            String timePublished,
            String source
    ) {}
}
