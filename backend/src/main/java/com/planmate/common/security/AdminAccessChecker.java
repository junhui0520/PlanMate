package com.planmate.common.security;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.member.entity.Member;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 관리자 검사 (ACC-07-03).
 * - 관리자는 MEMBER.SYSTEM_ROLE = ADMIN. 서버 관리 절차로 미리 부여(가입·프로필로 승격 없음).
 * - 주소 단계(SecurityConfig의 /api/admin/** hasRole)는 세션 권한으로 거르고, 여기서는 DB를 다시 확인한다.
 * - 운영 조치 전에는 5분 안의 본인 재확인이 필요하다(서버 세션에 관리자 ID + 재확인 시각).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccessChecker {

    /** 재확인한 관리자 ID (세션 속성) */
    public static final String REAUTH_ID_ATTRIBUTE = "PLANMATE_ADMIN_REAUTH_ID";
    /** 재확인 시각, epoch millis (세션 속성) */
    public static final String REAUTH_AT_ATTRIBUTE = "PLANMATE_ADMIN_REAUTH_AT";

    static final Duration REAUTH_VALID = Duration.ofMinutes(5);

    private final AccountStatusChecker accountStatusChecker;

    /** 로그인한 관리자 ID. 로그인·계정 상태는 AccountStatusChecker 규칙, DB의 SYSTEM_ROLE이 ADMIN이 아니면 ADMIN_ONLY */
    public Long requireAdmin() {
        Member member = accountStatusChecker.requireActiveMember();
        if (!member.isAdmin()) {
            throw deny(ErrorCode.ADMIN_ONLY, member.getMemberId());
        }
        return member.getMemberId();
    }

    /** 5분 안에 같은 관리자가 본인 재확인을 했는지. 아니면 ADMIN_REAUTH_REQUIRED */
    public void requireRecentReauth(Long adminId) {
        HttpSession session = currentSession(false);
        boolean recent = false;
        if (session != null && adminId != null
                && adminId.equals(session.getAttribute(REAUTH_ID_ATTRIBUTE))
                && session.getAttribute(REAUTH_AT_ATTRIBUTE) instanceof Long reauthAt) {
            long elapsed = System.currentTimeMillis() - reauthAt;
            recent = elapsed >= 0 && elapsed <= REAUTH_VALID.toMillis();
        }
        if (!recent) {
            throw deny(ErrorCode.ADMIN_REAUTH_REQUIRED, adminId);
        }
    }

    /**
     * 본인 재확인 성공을 세션에 기록한다.
     * 비밀번호 재확인 API(admin/service/ReauthService)는 ADM-01-02에서 이 메서드를 부른다.
     */
    public void markReauthenticated(Long adminId) {
        HttpSession session = currentSession(true);
        session.setAttribute(REAUTH_ID_ATTRIBUTE, adminId);
        session.setAttribute(REAUTH_AT_ATTRIBUTE, System.currentTimeMillis());
    }

    private static HttpSession currentSession(boolean create) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getSession(create);
        }
        if (create) {
            throw new IllegalStateException("HTTP 요청 밖에서는 관리자 재확인을 기록할 수 없음");
        }
        return null;
    }

    private static BusinessException deny(ErrorCode code, Long memberId) {
        log.info("접근 거부: code={}, path={}, memberId={}", code.name(), SecurityErrorHandler.currentRequestPath(), memberId);
        return new BusinessException(code);
    }
}
