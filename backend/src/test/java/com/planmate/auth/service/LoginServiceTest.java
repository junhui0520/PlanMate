package com.planmate.auth.service;

import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.entity.LoginAttempt;
import com.planmate.auth.entity.LoginAttempt.Result;
import com.planmate.auth.repository.LoginAttemptRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** ACC-02-01 로그인 인증·계정/IP 제한·형식 검사 (DB 없이) */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Abcd1234!";
    private static final String HASH = "{bcrypt}stored";
    private static final String DUMMY = "{bcrypt}dummy";
    private static final String IP = "203.0.113.7";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountStatusChecker accountStatusChecker;

    @Mock
    private CsrfTokenRepository csrfTokenRepository;

    private final AuthHasher hasher = new AuthHasher("test-secret-for-unit-tests");
    private LoginService service;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn(DUMMY); // 가짜 해시
        service = new LoginService(memberRepository, loginAttemptRepository, hasher, passwordEncoder,
                accountStatusChecker, csrfTokenRepository);
    }

    // ===== 실패는 모두 같은 오류 =====

    @Test
    void 없는_이메일도_가짜_해시로_비교하고_LOGIN_FAILED() {
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.matches(PASSWORD, DUMMY)).willReturn(false);

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder).matches(PASSWORD, DUMMY);
        assertRecorded(Result.FAIL, EMAIL);
    }

    @Test
    void 틀린_비밀번호는_LOGIN_FAILED() {
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(false);

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_FAILED);
        assertRecorded(Result.FAIL, EMAIL);
    }

    @Test
    void 소셜_회원처럼_비밀번호_해시가_없으면_가짜_해시로_비교하고_LOGIN_FAILED() {
        Member social = member(Member.Status.ACTIVE);
        ReflectionTestUtils.setField(social, "provider", Member.Provider.KAKAO);
        ReflectionTestUtils.setField(social, "passwordHash", null);
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(social));
        given(passwordEncoder.matches(PASSWORD, DUMMY)).willReturn(true); // 가짜 해시가 우연히 맞아도 실패

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder).matches(PASSWORD, DUMMY);
    }

    // ===== 계정 제한 =====

    @Test
    void 십오분_안_5회_실패면_LIMITED_비밀번호도_보지_않고_기록도_안_함() {
        LocalDateTime now = LocalDateTime.now();
        givenFails(now.minusMinutes(1), now.minusMinutes(3), now.minusMinutes(5), now.minusMinutes(8), now.minusMinutes(12));

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_ATTEMPTS_LIMITED);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginAttemptRepository, never()).save(any());
    }

    @Test
    void 가장_최근_실패_15분_정각부터_풀린다() {
        LocalDateTime now = LocalDateTime.now();
        // 가장 최근 실패가 15분 전 -> 안쪽 now는 이보다 늦어 '최근 + 15분' 이상이라 해제
        givenFails(now.minusMinutes(15), now.minusMinutes(16), now.minusMinutes(17), now.minusMinutes(18), now.minusMinutes(19));
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        assertThat(service.authenticate(login(EMAIL, PASSWORD), IP).getMemberId()).isEqualTo(1L);
    }

    @Test
    void 가장_최근_실패_15분이_되기_직전이면_아직_제한() {
        LocalDateTime now = LocalDateTime.now();
        givenFails(now.minusMinutes(15).plusSeconds(5), now.minusMinutes(16), now.minusMinutes(17), now.minusMinutes(18),
                now.minusMinutes(19));

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_ATTEMPTS_LIMITED);
    }

    @Test
    void 다섯_번이_15분보다_넓게_퍼져_있으면_안_걸린다() {
        LocalDateTime now = LocalDateTime.now();
        givenFails(now.minusMinutes(1), now.minusMinutes(5), now.minusMinutes(10), now.minusMinutes(14), now.minusMinutes(17));
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        assertThat(service.authenticate(login(EMAIL, PASSWORD), IP).getMemberId()).isEqualTo(1L);
    }

    @Test
    void 마지막_성공_이전_실패는_세지_않는다_조회_시작이_마지막_성공_시각() {
        LocalDateTime lastSuccess = LocalDateTime.now().minusMinutes(10);
        given(loginAttemptRepository.findLastAttemptedAt(EMAIL, Result.SUCCESS)).willReturn(lastSuccess);
        given(loginAttemptRepository.findRecentAttemptedAt(eq(EMAIL), eq(Result.FAIL), any(), any(Limit.class)))
                .willReturn(List.of());
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        service.authenticate(login(EMAIL, PASSWORD), IP);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loginAttemptRepository).findRecentAttemptedAt(eq(EMAIL), eq(Result.FAIL), since.capture(), eq(Limit.of(5)));
        assertThat(since.getValue()).isEqualTo(lastSuccess);
    }

    @Test
    void 성공이_30분보다_오래됐으면_조회_시작은_30분_전() {
        given(loginAttemptRepository.findLastAttemptedAt(EMAIL, Result.SUCCESS)).willReturn(LocalDateTime.now().minusHours(2));
        given(loginAttemptRepository.findRecentAttemptedAt(eq(EMAIL), eq(Result.FAIL), any(), any(Limit.class)))
                .willReturn(List.of());
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        service.authenticate(login(EMAIL, PASSWORD), IP);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loginAttemptRepository).findRecentAttemptedAt(eq(EMAIL), eq(Result.FAIL), since.capture(), any(Limit.class));
        assertThat(since.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(30), within(3, java.time.temporal.ChronoUnit.SECONDS));
    }

    // ===== IP 제한 =====

    @Test
    void IP_15분_50회면_LIMITED_기록_안_함() {
        given(loginAttemptRepository.countByIpHashAndAttemptedAtAfter(eq(hasher.ipHash(IP)), any())).willReturn(50L);

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_ATTEMPTS_LIMITED);
        verify(loginAttemptRepository, never()).save(any());
        verify(memberRepository, never()).findByEmailNormalized(any());
    }

    // ===== 형식 오류 =====

    @Test
    void 잘못된_이메일_형식은_비교_없이_LOGIN_FAILED와_FAIL_기록() {
        assertDenied(() -> service.authenticate(login("not-an-email", PASSWORD), IP), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder, never()).matches(any(), any());
        assertRecorded(Result.FAIL, "not-an-email");
    }

    @Test
    void 비밀번호_65자는_비교_없이_LOGIN_FAILED() {
        assertDenied(() -> service.authenticate(login(EMAIL, "A1!" + "a".repeat(62)), IP), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder, never()).matches(any(), any());
        assertRecorded(Result.FAIL, EMAIL);
    }

    @Test
    void 한글_비밀번호는_비교_없이_LOGIN_FAILED() {
        assertDenied(() -> service.authenticate(login(EMAIL, "Abcd1234!가"), IP), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder, never()).matches(any(), any());
        assertRecorded(Result.FAIL, EMAIL);
    }

    @Test
    void 이메일이_254자를_넘으면_제한_판정_없이_실패_기록에도_이메일을_비운다() {
        String longEmail = "a".repeat(250) + "@example.com";

        assertDenied(() -> service.authenticate(login(longEmail, PASSWORD), IP), ErrorCode.LOGIN_FAILED);
        verify(loginAttemptRepository, never()).findRecentAttemptedAt(any(), any(), any(), any());
        assertRecorded(Result.FAIL, null);
    }

    // ===== 계정 상태 =====

    @Test
    void 탈퇴_회원은_비밀번호가_맞으면_ACCOUNT_UNAVAILABLE() {
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.WITHDRAWN)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.ACCOUNT_UNAVAILABLE);
        assertRecorded(Result.FAIL, EMAIL);
    }

    @Test
    void 탈퇴_회원도_비밀번호가_틀리면_LOGIN_FAILED() {
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.WITHDRAWN)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(false);

        assertDenied(() -> service.authenticate(login(EMAIL, PASSWORD), IP), ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 성공하면_SUCCESS_기록_이용_제한_회원도_검사하지_않고_로그인() {
        given(memberRepository.findByEmailNormalized(EMAIL)).willReturn(Optional.of(member(Member.Status.ACTIVE)));
        given(passwordEncoder.matches(PASSWORD, HASH)).willReturn(true);

        Member result = service.authenticate(login(" User@Example.com ", PASSWORD), IP);

        assertThat(result.getMemberId()).isEqualTo(1L);
        assertRecorded(Result.SUCCESS, EMAIL);
        verify(accountStatusChecker, never()).requireCommunityWritable(any());
    }

    // ===== 도우미 =====

    private static LoginRequest login(String email, String password) {
        return new LoginRequest(email, password);
    }

    private static Member member(Member.Status status) {
        Member member = Member.createEmailMember("테스트용", EMAIL, HASH);
        ReflectionTestUtils.setField(member, "memberId", 1L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private void givenFails(LocalDateTime... failTimes) {
        given(loginAttemptRepository.findRecentAttemptedAt(eq(EMAIL), eq(Result.FAIL), any(), eq(Limit.of(5))))
                .willReturn(List.of(failTimes));
    }

    private void assertRecorded(Result result, String email) {
        ArgumentCaptor<LoginAttempt> saved = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(saved.capture());
        assertThat(saved.getValue().getResult()).isEqualTo(result);
        assertThat(saved.getValue().getEmailNormalized()).isEqualTo(email);
        assertThat(saved.getValue().getIpHash()).isEqualTo(hasher.ipHash(IP));
        assertThat(saved.getValue().getAttemptedAt()).isNotNull();
    }

    private static void assertDenied(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
    }
}
