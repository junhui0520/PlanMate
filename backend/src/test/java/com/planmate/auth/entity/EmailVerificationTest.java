package com.planmate.auth.entity;

import com.planmate.auth.entity.EmailVerification.Purpose;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-01-02·03 인증번호 만료·오입력·확인값 경계 */
class EmailVerificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 15, 0, 0);

    private static EmailVerification issued() {
        return EmailVerification.issue("user@example.com", Purpose.SIGNUP, "hmac", "iphash", NOW);
    }

    @Test
    void 발급하면_5분_뒤_만료_오입력_0회_발급시각_기록() {
        EmailVerification verification = issued();

        assertThat(verification.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(verification.getAttemptCount()).isZero();
        assertThat(verification.getCreatedAt()).isEqualTo(NOW);
        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.isConsumed()).isFalse();
    }

    @Test
    void 만료_시각과_정확히_같으면_만료_1초_전은_유효() {
        EmailVerification verification = issued();

        assertThat(verification.isExpired(NOW.plusMinutes(5).minusSeconds(1))).isFalse();
        assertThat(verification.isExpired(NOW.plusMinutes(5))).isTrue();
    }

    @Test
    void 오입력_4회까지는_유지되고_5회째에_폐기된다() {
        EmailVerification verification = issued();
        for (int i = 0; i < 4; i++) {
            verification.recordMismatch(NOW);
        }
        assertThat(verification.getAttemptCount()).isEqualTo(4);
        assertThat(verification.isAttemptsExhausted()).isFalse();
        assertThat(verification.getConsumedAt()).isNull();

        LocalDateTime fifth = NOW.plusSeconds(30);
        verification.recordMismatch(fifth);

        assertThat(verification.getAttemptCount()).isEqualTo(5);
        assertThat(verification.isAttemptsExhausted()).isTrue();
        assertThat(verification.getConsumedAt()).isEqualTo(fifth);
    }

    @Test
    void 확인값은_인증_뒤_5분_전까지만_쓸_수_있고_한_번만_쓴다() {
        EmailVerification verification = issued();
        assertThat(verification.isConfirmUsable(NOW)).isFalse(); // 인증 전

        LocalDateTime verifiedAt = NOW.plusMinutes(1);
        verification.verify("tokenhash", verifiedAt);

        assertThat(verification.getConfirmExpiresAt()).isEqualTo(verifiedAt.plusMinutes(5));
        assertThat(verification.isConfirmUsable(verifiedAt.plusMinutes(5).minusSeconds(1))).isTrue();
        assertThat(verification.isConfirmUsable(verifiedAt.plusMinutes(5))).isFalse(); // 확인 만료 정각

        verification.consume(verifiedAt.plusMinutes(2));
        assertThat(verification.isConfirmUsable(verifiedAt.plusMinutes(3))).isFalse(); // 이미 사용
    }
}
