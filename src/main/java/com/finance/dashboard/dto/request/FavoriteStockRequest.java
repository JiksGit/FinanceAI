package com.finance.dashboard.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FavoriteStockRequest(

        @NotBlank(message = "종목 심볼을 입력해주세요.")
        String stockSymbol,

        String stockName
) {
}
