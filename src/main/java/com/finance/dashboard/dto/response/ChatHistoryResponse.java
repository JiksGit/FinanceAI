package com.finance.dashboard.dto.response;

import java.time.LocalDateTime;

public record ChatHistoryResponse(
        Long id,
        String question,
        String answer,
        LocalDateTime createdAt
) {
}
