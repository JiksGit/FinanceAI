package com.finance.dashboard.dto.response;

public record LoginResponse(String accessToken, String refreshToken, String nickname) {
}
