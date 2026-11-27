package com.planmate.auth.dto;

/**
 * 인증번호 확인 결과 (ACC-01-03). confirmToken은 가입 요청에 보내는 일회 확인값(5분, A-P04).
 * 서버는 해시만 저장하고 원문은 이 응답에만 담는다.
 */
public record VerificationConfirmResponse(String confirmToken, int confirmExpiresInSeconds) {

    // 확인값을 로그에 남기지 않는다
    @Override
    public String toString() {
        return "VerificationConfirmResponse[confirmToken=***, confirmExpiresInSeconds=" + confirmExpiresInSeconds + "]";
    }
}
