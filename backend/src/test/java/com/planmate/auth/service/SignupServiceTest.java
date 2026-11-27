package com.planmate.auth.service;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.entity.EmailVerification.Purpose;
import com.planmate.auth.service.EmailVerificationService.ConfirmResult;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** ACC-01-03·04 가입 확인·가입 완료 (DB 없이) */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Abcd1234!";

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private SignupService service;

    @BeforeEach
    void setUp() {
        service = new SignupService(emailVerificationService, memberRepository, passwordEncoder);
    }

    @Test
    void 인증_확인_뒤_이미_가입한_이메일이면_409() {
        given(emailVerificationService.confirm(Purpose.SIGNUP, 1L, "012345"))
                .willReturn(new ConfirmResult(EMAIL, new VerificationConfirmResponse("token", 300)));
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(true);

        assertDenied(() -> service.confirmCode(1L, "012345"), ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void 인증_확인에_성공하면_확인값을_돌려준다() {
        VerificationConfirmResponse response = new VerificationConfirmResponse("token", 300);
        given(emailVerificationService.confirm(Purpose.SIGNUP, 1L, "012345")).willReturn(new ConfirmResult(EMAIL, response));
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(false);

        assertThat(service.confirmCode(1L, "012345")).isEqualTo(response);
    }

    @Test
    void 가입하면_EMAIL_USER_ACTIVE로_해시한_비밀번호와_함께_저장한다() {
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(false);

        service.signup(request());

        verify(emailVerificationService).consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token");
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(saved.capture());
        Member member = saved.getValue();
        assertThat(member.getProvider()).isEqualTo(Member.Provider.EMAIL);
        assertThat(member.getSystemRole()).isEqualTo(Member.SystemRole.USER);
        assertThat(member.getStatus()).isEqualTo(Member.Status.ACTIVE);
        assertThat(member.getEmailNormalized()).isEqualTo(EMAIL);
        assertThat(member.getNickname()).isEqualTo("여행자_1");
        assertThat(member.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
        assertThat(member.getPasswordChangedAt()).isNull();
        assertThat(member.getZipCode()).isNull();
    }

    @Test
    void 미리_조회해서_이미_가입한_이메일이면_409_저장하지_않는다() {
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(true);

        assertDenied(() -> service.signup(request()), ErrorCode.EMAIL_ALREADY_REGISTERED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 확인값을_쓸_수_없으면_저장하지_않는다() {
        willThrow(new BusinessException(ErrorCode.VERIFICATION_EXPIRED))
                .given(emailVerificationService).consumeConfirmToken(Purpose.SIGNUP, EMAIL, "token");

        assertDenied(() -> service.signup(request()), ErrorCode.VERIFICATION_EXPIRED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_가입으로_UQ_MEMBER_01_위반이면_409() {
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException("ORA-00001: unique constraint (PLANMATE.UQ_MEMBER_01) violated")));

        assertDenied(() -> service.signup(request()), ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void 다른_제약_위반은_그대로_다시_던진다() {
        given(memberRepository.existsByEmailNormalized(EMAIL)).willReturn(false);
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException("ORA-00001: unique constraint (PLANMATE.UQ_MEMBER_02) violated"));
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(other);

        assertThatThrownBy(() -> service.signup(request())).isSameAs(other);
    }

    private static SignupRequest request() {
        return new SignupRequest(" User@Example.com ", "token", PASSWORD, PASSWORD, "여행자_1", true);
    }

    private static void assertDenied(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
    }
}
