package com.planmate.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 세션 시간 정책 (A-P08, ACC-07-01).
 * - 일반 회원: 무활동 60분(application.yml server.servlet.session.timeout)·최대 12시간
 * - 관리자: 무활동 15분·최대 8시간
 * 최대 시간이 지나면 세션을 폐기하고 비로그인으로 이어 간다(보호 주소면 뒤에서 401).
 * ⚠️ @Component를 붙이지 않는다. 빈으로 등록하면 서블릿 필터로도 한 번 더 돈다. SecurityConfig에서 new로 넣는다.
 */
@Slf4j
public class SessionPolicyFilter extends OncePerRequestFilter {

    /** 로그인 시각(epoch millis). pjh/login이 로그인할 때 세션에 기록한다 (ACC-02-01) */
    public static final String LOGIN_AT_ATTRIBUTE = "PLANMATE_LOGIN_AT";

    static final Duration USER_IDLE = Duration.ofMinutes(60);
    static final Duration USER_MAX = Duration.ofHours(12);
    /** 관리자 무활동 15분. 로그인할 때도 이 값으로 세션을 만든다(LoginService, ACC-02-01) */
    public static final Duration ADMIN_IDLE = Duration.ofMinutes(15);
    static final Duration ADMIN_MAX = Duration.ofHours(8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session == null || authentication == null || !(authentication.getPrincipal() instanceof LoginMember member)) {
            chain.doFilter(request, response);
            return;
        }

        long loginAt = session.getAttribute(LOGIN_AT_ATTRIBUTE) instanceof Long value ? value : session.getCreationTime();
        Duration max = member.isAdmin() ? ADMIN_MAX : USER_MAX;
        if (System.currentTimeMillis() - loginAt > max.toMillis()) {
            log.info("세션 최대 시간 초과로 폐기: memberId={}", member.memberId());
            session.invalidate();
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        // 관리자는 무활동 15분 (일반 회원은 application.yml의 60분 그대로)
        int adminIdleSeconds = (int) ADMIN_IDLE.toSeconds();
        if (member.isAdmin() && session.getMaxInactiveInterval() != adminIdleSeconds) {
            session.setMaxInactiveInterval(adminIdleSeconds);
        }
        chain.doFilter(request, response);
    }
}
