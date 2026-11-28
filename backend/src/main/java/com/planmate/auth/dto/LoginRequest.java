package com.planmate.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 입력값 (ACC-02-01). 이메일·비밀번호만 받는다.
 * systemRole·memberId 같은 칸은 두지 않는다(요청에 와도 받지 않음, 공격자가 보내는 ROLE·MEMBER_ID를 신뢰하지 않음).
 * 형식 검사(@Pattern)는 넣지 않는다. 형식이 틀려도 서비스에서 같은 LOGIN_FAILED로 처리한다(잘못된 이메일·비밀번호는 같은 오류).
 */
public record LoginRequest(
        @NotBlank(message = LOGIN_MESSAGE)
        String email,

        // trim 금지 (A-P01)
        @NotBlank(message = LOGIN_MESSAGE)
        String password
) {

    public static final String LOGIN_MESSAGE = "이메일 또는 비밀번호를 확인해 주세요.";

    public LoginRequest {
        email = SignupRequest.normalizeEmail(email);
    }

    // 비밀번호를 로그에 남기지 않는다
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
