package com.finance.dashboard.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AiAnalyzeResponse(
        String answer,
        List<String> contextUsed,
        LocalDateTime createdAt
) {
}
