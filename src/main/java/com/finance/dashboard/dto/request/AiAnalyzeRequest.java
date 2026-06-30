package com.finance.dashboard.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AiAnalyzeRequest(

        @NotBlank(message = "질문을 입력해주세요.")
        String question,

        boolean includeExchangeRate,
        boolean includeStock
) {
}
