package com.planmate.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * EMAIL_VERIFICATION (이메일 인증). 가입·비밀번호 재설정 인증번호 발급과 확인 (ACC-01-02·03, ACC-04-02).
 * 인증번호 원문은 저장하지 않고 HMAC(CODE_HMAC)만 저장한다(A-P09). 가입 확인값도 해시만 저장한다.
 * 상태 메서드는 모두 now를 인자로 받는다(경계 시각 테스트용).
 */
@Entity
@Table(name = "EMAIL_VERIFICATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    /** 인증 용도 (A-F03) */
    public enum Purpose { SIGNUP, RESET_PASSWORD }

    /** 인증번호 유효 시간 5분 (A-P04) */
    public static final Duration CODE_VALID = Duration.ofMinutes(5);
    /** 인증 후 가입·재설정 확인값 유효 시간 5분 (A-P04) */
    public static final Duration CONFIRM_VALID = Duration.ofMinutes(5);
    /** 발급건당 오입력 허용 횟수. 5회면 폐기 (A-P04) */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_EMAIL_VERIFICATION_GENERATOR")
    @SequenceGenerator(name = "SEQ_EMAIL_VERIFICATION_GENERATOR", sequenceName = "SEQ_EMAIL_VERIFICATION", allocationSize = 1)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @Column(name = "EMAIL", nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "PURPOSE", nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "CODE_HMAC", nullable = false, length = 128)
    private String codeHmac;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private Integer attemptCount;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;

    // 번호 폐기(재발송·5회 오입력) 또는 확인값 소비(가입·재설정 완료) 시각
    @Column(name = "CONSUMED_AT")
    private LocalDateTime consumedAt;

    @Column(name = "CONFIRM_TOKEN_HASH", length = 128)
    private String confirmTokenHash;

    @Column(name = "CONFIRM_EXPIRES_AT")
    private LocalDateTime confirmExpiresAt;

    @Column(name = "REQUEST_IP_HASH", length = 128)
    private String requestIpHash;

    // DB 기본값에 맡기지 않고 발급 시각(now)을 넣는다
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 인증번호 발급 (ACC-01-02). 만료 = now + 5분, 오입력 0회 */
    public static EmailVerification issue(String email, Purpose purpose, String codeHmac, String requestIpHash,
                                          LocalDateTime now) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.purpose = purpose;
        verification.codeHmac = codeHmac;
        verification.requestIpHash = requestIpHash;
        verification.expiresAt = now.plus(CODE_VALID);
        verification.attemptCount = 0;
        verification.createdAt = now;
        return verification;
    }

    /** 인증번호 만료 여부. 만료 시각과 같거나 지나면 만료 (ACC-01-03 '만료시각 이상이면 실패') */
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /** 오입력 5회 이상 */
    public boolean isAttemptsExhausted() {
        return attemptCount >= MAX_ATTEMPTS;
    }

    /** 오입력 기록. 5회가 되면 번호를 폐기한다 (A-P04) */
    public void recordMismatch(LocalDateTime now) {
        attemptCount = attemptCount + 1;
        if (isAttemptsExhausted() && consumedAt == null) {
            consumedAt = now;
        }
    }

    /** 인증 성공: 확인값 해시와 만료(now + 5분)를 기록한다 (ACC-01-03) */
    public void verify(String confirmTokenHash, LocalDateTime now) {
        this.verifiedAt = now;
        this.confirmTokenHash = confirmTokenHash;
        this.confirmExpiresAt = now.plus(CONFIRM_VALID);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** 확인값을 쓸 수 있는지: 인증됨 + 아직 소비 안 됨 + 확인 만료 전 */
    public boolean isConfirmUsable(LocalDateTime now) {
        return verifiedAt != null && consumedAt == null && confirmExpiresAt != null && now.isBefore(confirmExpiresAt);
    }

    /** 확인값 소비 (ACC-01-04 가입 완료). 한 번만 쓸 수 있다 */
    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }
}
