package com.finance.dashboard.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    DUPLICATE_EMAIL("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND("존재하지 않는 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),

    // External API
    EXCHANGE_API_ERROR("환율 데이터를 불러오는 중 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_API_ERROR("주식 데이터를 불러오는 중 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RATE_LIMIT("잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    AI_API_ERROR("AI 분석 중 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),

    // Stock
    STOCK_NOT_FOUND("존재하지 않는 종목입니다.", HttpStatus.NOT_FOUND),
    FAVORITE_ALREADY_EXISTS("이미 즐겨찾기된 종목입니다.", HttpStatus.CONFLICT),

    // Common
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
