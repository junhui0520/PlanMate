package com.planmate.auth.service;

import com.planmate.auth.client.MailClient;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.entity.EmailVerification;
import com.planmate.auth.entity.EmailVerification.Purpose;
import com.planmate.auth.repository.EmailVerificationRepository;
import com.planmate.auth.repository.EmailVerificationRepository.WindowStats;
import com.planmate.auth.service.EmailVerificationService.ConfirmResult;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** ACC-01-02·03·04 인증번호 발송·확인·확인값 소비 (DB 없이, AuthHasher는 테스트 비밀키로 실제 객체) */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "203.0.113.7";
    private static final String CODE = "012345";

    @Mock
    private EmailVerificationRepository repository;

    @Mock
    private MailClient mailClient;

    private final AuthHasher hasher = new AuthHasher("test-secret-for-unit-tests");
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(repository, hasher, mailClient);
    }

    // ===== send =====

    @Test
    void 발송하면_6자리_번호를_메일로_보내고_HMAC만_저장한다() {
        noLimits();
        givenSaveAssignsId(7L);

        VerificationSendResponse response = service.send(Purpose.SIGNUP, EMAIL, IP);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailClient).sendVerificationCode(eq(EMAIL), code.capture());
        assertThat(code.getValue()).matches("^\\d{6}$");

        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(repository).saveAndFlush(saved.capture());
        EmailVerification row = saved.getValue();
        assertThat(row.getCodeHmac()).doesNotContain(code.getValue()).hasSize(64);
        assertThat(row.getCodeHmac()).isEqualTo(hasher.codeHmac(Purpose.SIGNUP, EMAIL, code.getValue()));
        assertThat(row.getCodeHmac()).isNotEqualTo(hasher.codeHmac(Purpose.SIGNUP, "other@example.com", code.getValue()));
        assertThat(row.getRequestIpHash()).isEqualTo(hasher.ipHash(IP)).doesNotContain(IP);

        assertThat(response).isEqualTo(new VerificationSendResponse(7L, 300, 60));
    }

    @Test
    void 발송_전에_이전_번호를_무효화한다() {
        noLimits();
        givenSaveAssignsId(7L);

        service.send(Purpose.SIGNUP, EMAIL, IP);

        InOrder order = inOrder(repository, mailClient);
        order.verify(repository).invalidateActive(eq(EMAIL), eq(Purpose.SIGNUP), any(LocalDateTime.class));
        order.verify(repository).saveAndFlush(any(EmailVerification.class));
        order.verify(mailClient).sendVerificationCode(eq(EMAIL), anyString());
    }

    @Test
    void 메일_발송이_실패하면_SEND_FAILED가_전파된다() {
        noLimits();
        givenSaveAssignsId(7L);
        willThrow(new BusinessException(ErrorCode.VERIFICATION_SEND_FAILED))
                .given(mailClient).sendVerificationCode(eq(EMAIL), anyString());

        assertDenied(() -> service.send(Purpose.SIGNUP, EMAIL, IP), ErrorCode.VERIFICATION_SEND_FAILED);
    }

    @Test
    void 최근_발송_60초_안이면_LIMITED_남은_시간을_돌려준다() {
        LocalDateTime now = LocalDateTime.now();
        EmailVerification recent = EmailVerification.issue(EMAIL, Purpose.SIGNUP, "h", "ip", now.minusSeconds(20));
        given(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(EMAIL, Purpose.SIGNUP))
                .willReturn(Optional.of(recent));
        given(repository.countByEmailSince(eq(EMAIL), eq(Purpose.SIGNUP), any())).willReturn(stats(1, now.minusSeconds(20)));
        given(repository.countByIpSince(anyString(), any())).willReturn(stats(1, now.minusSeconds(20)));

        assertLimited(() -> service.send(Purpose.SIGNUP, EMAIL, IP), Duration.ofSeconds(40));
        verify(repository, never()).saveAndFlush(any());
        verify(mailClient, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void 같은_이메일_6번째_요청은_LIMITED_가장_오래된_발급_1시간_뒤까지() {
        LocalDateTime now = LocalDateTime.now();
        given(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(EMAIL, Purpose.SIGNUP))
                .willReturn(Optional.empty());
        given(repository.countByEmailSince(eq(EMAIL), eq(Purpose.SIGNUP), any())).willReturn(stats(5, now.minusMinutes(50)));
        given(repository.countByIpSince(anyString(), any())).willReturn(stats(5, now.minusMinutes(50)));

        assertLimited(() -> service.send(Purpose.SIGNUP, EMAIL, IP), Duration.ofMinutes(10));
    }

    @Test
    void 같은_IP_31번째_요청은_LIMITED_가장_오래된_발급_1시간_뒤까지() {
        LocalDateTime now = LocalDateTime.now();
        given(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(EMAIL, Purpose.SIGNUP))
                .willReturn(Optional.empty());
        given(repository.countByEmailSince(eq(EMAIL), eq(Purpose.SIGNUP), any())).willReturn(stats(0, null));
        given(repository.countByIpSince(eq(hasher.ipHash(IP)), any())).willReturn(stats(30, now.minusMinutes(30)));

        assertLimited(() -> service.send(Purpose.SIGNUP, EMAIL, IP), Duration.ofMinutes(30));
    }

    @Test
    void 제한이_여러_개면_가장_늦은_시각_기준() {
        LocalDateTime now = LocalDateTime.now();
        EmailVerification recent = EmailVerification.issue(EMAIL, Purpose.SIGNUP, "h", "ip", now.minusSeconds(10));
        given(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(EMAIL, Purpose.SIGNUP))
                .willReturn(Optional.of(recent));                                          // 50초 뒤
        given(repository.countByEmailSince(eq(EMAIL), eq(Purpose.SIGNUP), any())).willReturn(stats(5, now.minusMinutes(40))); // 20분 뒤
        given(repository.countByIpSince(anyString(), any())).willReturn(stats(30, now.minusMinutes(55)));                    // 5분 뒤

        assertLimited(() -> service.send(Purpose.SIGNUP, EMAIL, IP), Duration.ofMinutes(20));
    }

    // ===== confirm =====

    @Test
    void 확인_요청_행이_없으면_EXPIRED() {
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.empty());
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 용도가_다르면_EXPIRED() {
        lockReturns(issued(Purpose.RESET_PASSWORD, LocalDateTime.now()));
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 오입력이_이미_5회면_ATTEMPTS_EXCEEDED() {
        EmailVerification row = issued(Purpose.SIGNUP, LocalDateTime.now());
        ReflectionTestUtils.setField(row, "attemptCount", 5);
        lockReturns(row);
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
    }

    @Test
    void 이미_사용됐거나_인증한_번호는_EXPIRED() {
        EmailVerification consumed = issued(Purpose.SIGNUP, LocalDateTime.now());
        consumed.consume(LocalDateTime.now());
        lockReturns(consumed);
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_EXPIRED);

        EmailVerification verified = issued(Purpose.SIGNUP, LocalDateTime.now());
        verified.verify("hash", LocalDateTime.now());
        lockReturns(verified);
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 만료된_번호는_EXPIRED() {
        lockReturns(issued(Purpose.SIGNUP, LocalDateTime.now().minusMinutes(5).minusSeconds(1)));
        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, CODE), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 틀린_번호_1회부터_4회까지는_CODE_MISMATCH_횟수가_쌓인다() {
        EmailVerification row = issued(Purpose.SIGNUP, LocalDateTime.now());
        lockReturns(row);
        for (int i = 1; i <= 4; i++) {
            assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, "999999"), ErrorCode.VERIFICATION_CODE_MISMATCH);
            assertThat(row.getAttemptCount()).isEqualTo(i);
        }
        assertThat(row.getConsumedAt()).isNull();
    }

    @Test
    void 틀린_번호_5회째는_ATTEMPTS_EXCEEDED_번호_폐기() {
        EmailVerification row = issued(Purpose.SIGNUP, LocalDateTime.now());
        ReflectionTestUtils.setField(row, "attemptCount", 4);
        lockReturns(row);

        assertDenied(() -> service.confirm(Purpose.SIGNUP, 1L, "999999"), ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(row.getAttemptCount()).isEqualTo(5);
        assertThat(row.getConsumedAt()).isNotNull();
    }

    @Test
    void 맞는_번호면_확인값을_발급하고_해시만_저장한다() {
        EmailVerification row = issued(Purpose.SIGNUP, LocalDateTime.now());
        lockReturns(row);

        ConfirmResult result = service.confirm(Purpose.SIGNUP, 1L, CODE);

        String token = result.response().confirmToken();
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$"); // 32바이트 Base64 URL, 패딩 없음
        assertThat(result.response().confirmExpiresInSeconds()).isEqualTo(300);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(row.isVerified()).isTrue();
        assertThat(row.getConfirmTokenHash()).isEqualTo(hasher.sha256Hex(token)).isNotEqualTo(token);
        assertThat(row.getConsumedAt()).isNull(); // 소비는 가입 때
    }

    // ===== consumeConfirmToken =====

    @Test
    void 인증_안_된_행이면_EXPIRED() {
        latestReturns(issued(Purpose.SIGNUP, LocalDateTime.now()));
        assertDenied(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token"), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 이미_사용된_확인값이면_EXPIRED() {
        EmailVerification row = verifiedWith("token", LocalDateTime.now());
        row.consume(LocalDateTime.now());
        latestReturns(row);
        assertDenied(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token"), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 확인값이_만료됐으면_EXPIRED() {
        latestReturns(verifiedWith("token", LocalDateTime.now().minusMinutes(5).minusSeconds(1)));
        assertDenied(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token"), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 다른_확인값이면_EXPIRED() {
        latestReturns(verifiedWith("token", LocalDateTime.now()));
        assertDenied(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "other"), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 재발송으로_더_새_행이_있으면_EXPIRED() {
        // 최신 행은 새로 발급만 된 행(인증 전)이라 예전 확인값을 쓸 수 없다
        given(repository.findLatestIds(eq(EMAIL), eq(Purpose.SIGNUP), any(Limit.class))).willReturn(List.of(2L));
        given(repository.findByIdForUpdate(2L)).willReturn(Optional.of(issued(Purpose.SIGNUP, LocalDateTime.now())));

        assertDenied(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token"), ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void 쓸_수_있는_확인값이면_소비한다() {
        EmailVerification row = verifiedWith("token", LocalDateTime.now());
        latestReturns(row);

        assertThatCode(() -> service.consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token")).doesNotThrowAnyException();
        assertThat(row.isConsumed()).isTrue();
    }

    // ===== 도우미 =====

    private void noLimits() {
        given(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(EMAIL, Purpose.SIGNUP))
                .willReturn(Optional.empty());
        given(repository.countByEmailSince(eq(EMAIL), eq(Purpose.SIGNUP), any())).willReturn(stats(0, null));
        given(repository.countByIpSince(anyString(), any())).willReturn(stats(0, null));
    }

    private void givenSaveAssignsId(Long id) {
        given(repository.saveAndFlush(any(EmailVerification.class))).willAnswer(invocation -> {
            EmailVerification row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "requestId", id);
            return row;
        });
    }

    private EmailVerification issued(Purpose purpose, LocalDateTime issuedAt) {
        return EmailVerification.issue(EMAIL, purpose, hasher.codeHmac(purpose, EMAIL, CODE), hasher.ipHash(IP), issuedAt);
    }

    private EmailVerification verifiedWith(String token, LocalDateTime verifiedAt) {
        EmailVerification row = issued(Purpose.SIGNUP, verifiedAt.minusMinutes(1));
        row.verify(hasher.sha256Hex(token), verifiedAt);
        return row;
    }

    private void lockReturns(EmailVerification row) {
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.of(row));
    }

    private void latestReturns(EmailVerification row) {
        given(repository.findLatestIds(eq(EMAIL), eq(Purpose.SIGNUP), any(Limit.class))).willReturn(List.of(1L));
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.of(row));
    }

    private static WindowStats stats(long count, LocalDateTime oldest) {
        return new WindowStats() {
            @Override
            public long getCount() {
                return count;
            }

            @Override
            public LocalDateTime getOldest() {
                return oldest;
            }
        };
    }

    private static void assertDenied(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
    }

    // retryAfter는 DateTimeUtil.now() 기준이라 몇 초 오차를 허용한다
    private static void assertLimited(ThrowingCallable call, Duration expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_REQUEST_LIMITED);
                    assertThat(e.getRetryAfter().toMillis()).isCloseTo(expected.toMillis(), within(3_000L));
                });
    }
}
