package com.planmate.common.exception;

import lombok.Getter;

/**
 * 규칙 위반일 때 던지는 공통 예외 (AGENTS §6).
 * 사용: throw new BusinessException(ErrorCode.XXX);
 * GlobalExceptionHandler가 ErrorCode의 상태·문구로 공통 응답을 만든다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
