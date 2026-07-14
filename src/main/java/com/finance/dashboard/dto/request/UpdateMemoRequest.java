package com.finance.dashboard.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateMemoRequest(
        @Size(max = 500, message = "메모는 500자 이내로 입력해주세요.")
        String memo
) {
}
