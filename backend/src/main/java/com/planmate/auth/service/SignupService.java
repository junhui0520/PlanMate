package com.planmate.auth.service;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.entity.EmailVerification.Purpose;
import com.planmate.auth.service.EmailVerificationService.ConfirmResult;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 이메일 가입 처리 (ACC-01-01·02·03·04).
 * 가입 직후 자동 로그인하지 않는다(화면이 로그인 화면으로 이동, A-P05).
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    // MEMBER.EMAIL_NORMALIZED 유일 조건
    private static final String EMAIL_UNIQUE_CONSTRAINT = "UQ_MEMBER_01";

    private final EmailVerificationService emailVerificationService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /** 가입 인증번호 발송 (ACC-01-02). 용도는 SIGNUP으로 고정 */
    public VerificationSendResponse sendCode(String email, String clientIp) {
        return emailVerificationService.send(Purpose.SIGNUP, email, clientIp);
    }

    /**
     * 가입 인증번호 확인 (ACC-01-03).
     * 이메일 소유가 확인된 뒤에만 기존 이메일 계정 여부를 알려 준다(ACC-01-04 I, 결정 3a).
     * 오입력 횟수가 예외 뒤에도 남도록 BusinessException으로는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public VerificationConfirmResponse confirmCode(Long requestId, String code) {
        ConfirmResult result = emailVerificationService.confirm(Purpose.SIGNUP, requestId, code);
        if (memberRepository.existsByEmailNormalized(result.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return result.response();
    }

    /**
     * 이메일 가입 완료 (ACC-01-04). 확인값 소비와 회원 저장은 한 트랜잭션이라 어느 쪽이 실패해도 둘 다 롤백된다.
     * TODO(ACC-05-02): 집 주소(선택) 받기 — ACC-01-04 입력값, 11/23 결정
     */
    @Transactional
    public void signup(SignupRequest request) {
        emailVerificationService.consumeConfirmToken(Purpose.SIGNUP, request.email(), request.confirmToken());

        // TODO(ACC-06·A-U02): 탈퇴 후 2주 안 재가입 복원 — 지금은 같은 409
        if (memberRepository.existsByEmailNormalized(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Member member = Member.createEmailMember(request.nickname(), request.email(),
                passwordEncoder.encode(request.password()));
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입한 경우만 409로 바꾸고, 다른 제약 위반은 공통 처리(500)로 보낸다
            if (isEmailUniqueViolation(e)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            throw e;
        }
    }

    // 원인 예외 메시지에 UQ_MEMBER_01이 있는지 (Oracle: ORA-00001 unique constraint (스키마.UQ_MEMBER_01) violated)
    private static boolean isEmailUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
