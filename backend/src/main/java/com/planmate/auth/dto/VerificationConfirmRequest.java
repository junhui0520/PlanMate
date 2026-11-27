package com.planmate.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 인증번호 확인 입력값 (ACC-01-03). 인증번호는 6자리 숫자 문자열(앞자리 0 유지, A-F03).
 */
public record VerificationConfirmRequest(
        @NotNull(message = SignupRequest.INVALID_INPUT_MESSAGE)
        Long requestId,

        @NotNull(message = CODE_MESSAGE)
        @Pattern(regexp = "^\\d{6}$", message = CODE_MESSAGE)
        String code
) {

    public static final String CODE_MESSAGE = "인증번호가 일치하지 않아요.";

    // 인증번호를 로그에 남기지 않는다
    @Override
    public String toString() {
        return "VerificationConfirmRequest[requestId=" + requestId + ", code=***]";
    }
}
