package com.planmate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증번호 받기 입력값 (ACC-01-02·ACC-04-02). 이메일만 받는다.
 * 이메일은 앞뒤 공백 제거 + 소문자로 정규화한다(A-P03).
 */
public record VerificationSendRequest(
        @NotBlank(message = SignupRequest.EMAIL_MESSAGE)
        @Size(max = SignupRequest.EMAIL_MAX_LENGTH, message = SignupRequest.EMAIL_MESSAGE)
        @Pattern(regexp = SignupRequest.EMAIL_PATTERN, message = SignupRequest.EMAIL_MESSAGE)
        String email
) {

    public VerificationSendRequest {
        email = SignupRequest.normalizeEmail(email);
    }
}
