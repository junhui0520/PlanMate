package com.planmate.common.exception;

import lombok.Getter;

import java.time.Duration;

/**
 * 규칙 위반일 때 던지는 공통 예외 (AGENTS §6).
 * 사용: throw new BusinessException(ErrorCode.XXX);
 * GlobalExceptionHandler가 ErrorCode의 상태·문구로 공통 응답을 만든다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 다시 요청할 수 있을 때까지 남은 시간. 있으면 Retry-After 헤더로 내보낸다 (예: ACC-01-02 요청 제한). 없으면 null */
    private final Duration retryAfter;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Duration retryAfter) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }
}
