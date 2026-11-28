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

import java.time.LocalDateTime;

/**
 * LOGIN_ATTEMPT (로그인 시도 기록). 계정 기준 15분 내 5회 실패·IP 기준 제한 판정용 (ACC-02-01, A-P06).
 * IP 원문은 저장하지 않고 해시만 저장한다. 비밀번호는 어디에도 남기지 않는다.
 */
@Entity
@Table(name = "LOGIN_ATTEMPT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    /** 시도 결과 */
    public enum Result { SUCCESS, FAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_LOGIN_ATTEMPT_GENERATOR")
    @SequenceGenerator(name = "SEQ_LOGIN_ATTEMPT_GENERATOR", sequenceName = "SEQ_LOGIN_ATTEMPT", allocationSize = 1)
    @Column(name = "ATTEMPT_ID")
    private Long attemptId;

    // 254자를 넘는 입력은 저장하지 않고 비운다
    @Column(name = "EMAIL_NORMALIZED", length = 254)
    private String emailNormalized;

    @Column(name = "IP_HASH", length = 128)
    private String ipHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT", nullable = false, length = 10)
    private Result result;

    // DB 기본값에 맡기지 않고 판정에 쓴 now를 넣는다
    @Column(name = "ATTEMPTED_AT", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    /** 시도 기록. 이메일·IP 해시는 비어 있을 수 있다 */
    public static LoginAttempt record(String emailNormalized, String ipHash, Result result, LocalDateTime now) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.emailNormalized = emailNormalized;
        attempt.ipHash = ipHash;
        attempt.result = result;
        attempt.attemptedAt = now;
        return attempt;
    }
}
