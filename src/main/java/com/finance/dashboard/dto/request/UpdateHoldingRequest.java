package com.finance.dashboard.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

public record UpdateHoldingRequest(

        @Min(value = 0, message = "수량은 0 이상이어야 합니다.")
        Integer quantity,

        @DecimalMin(value = "0", message = "평균 매수 단가는 0 이상이어야 합니다.")
        java.math.BigDecimal avgPrice
) {
}
