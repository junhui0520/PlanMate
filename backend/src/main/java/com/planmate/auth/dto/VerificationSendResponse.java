package com.planmate.auth.dto;

/**
 * 인증번호 받기 결과 (ACC-01-02).
 * requestId는 확인 요청에 그대로 보낸다. 화면은 expiresInSeconds로 05:00 타이머, resendAfterSeconds로 재발송 대기를 표시한다.
 */
public record VerificationSendResponse(Long requestId, int expiresInSeconds, int resendAfterSeconds) {
}
