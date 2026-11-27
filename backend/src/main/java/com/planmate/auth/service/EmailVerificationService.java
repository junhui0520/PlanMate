package com.planmate.auth.service;

import com.planmate.auth.client.MailClient;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.entity.EmailVerification;
import com.planmate.auth.entity.EmailVerification.Purpose;
import com.planmate.auth.repository.EmailVerificationRepository;
import com.planmate.auth.repository.EmailVerificationRepository.WindowStats;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 이메일 인증번호 발송·확인과 확인값 소비 (ACC-01-02·03·04).
 * 용도(Purpose)를 인자로 받아 비밀번호 재설정(ACC-04-02)에서도 다시 쓴다.
 * 인증번호·확인값 원문은 저장하지 않고 로그에도 남기지 않는다(A-P09).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    /** 재발송 간격 60초 (A-P04) */
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    /** 요청 제한 기간 1시간 (A-P04) */
    static final Duration LIMIT_WINDOW = Duration.ofHours(1);
    /** 이메일+용도당 시간당 5회 (A-P04) */
    static final int EMAIL_LIMIT_PER_HOUR = 5;
    /** IP당 시간당 30회 (A-P04) */
    static final int IP_LIMIT_PER_HOUR = 30;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final EmailVerificationRepository emailVerificationRepository;
    private final AuthHasher authHasher;
    private final MailClient mailClient;

    /** 인증번호 확인 결과. email은 서비스 안에서만 쓰고(기존 계정 확인 등) 응답에는 response만 내보낸다 */
    public record ConfirmResult(String email, VerificationConfirmResponse response) {
    }

    /**
     * 인증번호 발송 (ACC-01-02).
     * 메일 발송까지 한 트랜잭션이라, 발송이 실패하면 전부 롤백된다(이전 번호 유효, 새 행 없음, 제한 횟수에도 안 셈).
     */
    @Transactional
    public VerificationSendResponse send(Purpose purpose, String email, String clientIp) {
        LocalDateTime now = DateTimeUtil.now();
        String ipHash = authHasher.ipHash(clientIp);

        LocalDateTime retryAt = findRetryAt(purpose, email, ipHash, now);
        if (retryAt != null) {
            throw new BusinessException(ErrorCode.VERIFICATION_REQUEST_LIMITED, Duration.between(now, retryAt));
        }

        // 재발송 시 이전 번호 무효화
        emailVerificationRepository.invalidateActive(email, purpose, now);

        // 6자리 숫자(앞자리 0 유지), 서버 난수 (A-F03)
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerification verification = emailVerificationRepository.saveAndFlush(
                EmailVerification.issue(email, purpose, authHasher.codeHmac(purpose, email, code), ipHash, now));

        mailClient.sendVerificationCode(email, code);

        return new VerificationSendResponse(verification.getRequestId(),
                (int) EmailVerification.CODE_VALID.toSeconds(), (int) RESEND_INTERVAL.toSeconds());
    }

    /**
     * 인증번호 확인 (ACC-01-03).
     * 예외를 던져도 오입력 횟수는 DB에 남아야 해서 BusinessException으로는 롤백하지 않는다.
     * 바깥 트랜잭션(예: SignupService.confirmCode)도 같은 noRollbackFor를 붙여야 한다(안 붙이면 rollback-only로 표시됨).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ConfirmResult confirm(Purpose purpose, Long requestId, String code) {
        LocalDateTime now = DateTimeUtil.now();
        EmailVerification verification = emailVerificationRepository.findByIdForUpdate(requestId)
                .filter(found -> found.getPurpose() == purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_EXPIRED));

        if (verification.isAttemptsExhausted()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        // 재발송으로 무효화됐거나 이미 인증한 번호 (최신 발송 번호만 유효)
        if (verification.isConsumed() || verification.isVerified()) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        String candidate = authHasher.codeHmac(purpose, verification.getEmail(), code);
        if (!authHasher.matches(verification.getCodeHmac(), candidate)) {
            verification.recordMismatch(now);
            throw new BusinessException(verification.isAttemptsExhausted()
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        // 일회 확인값: 32바이트 난수, Base64 URL(패딩 없음). 원문은 응답에만 담고 해시만 저장
        String confirmToken = newConfirmToken();
        verification.verify(authHasher.sha256Hex(confirmToken), now);
        return new ConfirmResult(verification.getEmail(),
                new VerificationConfirmResponse(confirmToken, (int) EmailVerification.CONFIRM_VALID.toSeconds()));
    }

    /**
     * 확인값 소비 (ACC-01-04 가입 완료, ACC-04-03 재설정). 호출하는 쪽 트랜잭션에 참여한다.
     * 같은 이메일+용도의 최신 행만 쓸 수 있다. 재발송으로 더 새 행이 생겼으면 VERIFICATION_EXPIRED.
     */
    @Transactional
    public void consumeConfirmToken(Purpose purpose, String email, String confirmToken) {
        LocalDateTime now = DateTimeUtil.now();
        // 최신 행 ID를 잠금 없이 찾고 PK로 잠근다 (Oracle ORA-02014 회피)
        List<Long> latestIds = emailVerificationRepository.findLatestIds(email, purpose, Limit.of(1));
        EmailVerification verification = latestIds.stream().findFirst()
                .flatMap(emailVerificationRepository::findByIdForUpdate)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_EXPIRED));

        if (confirmToken == null || !verification.isConfirmUsable(now)
                || !authHasher.matches(verification.getConfirmTokenHash(), authHasher.sha256Hex(confirmToken))) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        verification.consume(now);
    }

    // 다시 요청할 수 있는 시각. 제한에 안 걸리면 null. 여러 개 걸리면 가장 늦은 시각
    private LocalDateTime findRetryAt(Purpose purpose, String email, String ipHash, LocalDateTime now) {
        LocalDateTime retryAt = null;

        LocalDateTime resendAt = emailVerificationRepository
                .findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(email, purpose)
                .map(latest -> latest.getCreatedAt().plus(RESEND_INTERVAL))
                .orElse(null);
        if (resendAt != null && resendAt.isAfter(now)) {
            retryAt = resendAt;
        }

        LocalDateTime since = now.minus(LIMIT_WINDOW);
        retryAt = later(retryAt, windowRetryAt(emailVerificationRepository.countByEmailSince(email, purpose, since),
                EMAIL_LIMIT_PER_HOUR));
        retryAt = later(retryAt, windowRetryAt(emailVerificationRepository.countByIpSince(ipHash, since),
                IP_LIMIT_PER_HOUR));
        return retryAt;
    }

    // 기간 안 횟수가 한도 이상이면 가장 오래된 발급 + 1시간
    private static LocalDateTime windowRetryAt(WindowStats stats, int limit) {
        if (stats == null || stats.getCount() < limit || stats.getOldest() == null) {
            return null;
        }
        return stats.getOldest().plus(LIMIT_WINDOW);
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static String newConfirmToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
