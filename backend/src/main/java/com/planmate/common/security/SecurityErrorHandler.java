package com.planmate.common.security;

import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 보안 필터 단계의 거부를 공통 실패 응답 {success:false, data:{code, fieldErrors}, message}로 바꾼다 (ACC-07).
 * - 로그인 안 됨 -> LOGIN_REQUIRED(401)
 * - CSRF 토큰 없음·불일치 -> CSRF_TOKEN_INVALID(403)
 * - 관리자 API(/api/admin/) -> ADMIN_ONLY(403)
 * - 그 밖의 허용 안 된 주소 -> API_NOT_FOUND(404)
 * 로그에는 code·경로(쿼리 제외)·memberId만 남긴다. 세션 ID·토큰은 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ADMIN_API_PREFIX = "/api/admin/";

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, ErrorCode.LOGIN_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code;
        if (accessDeniedException instanceof CsrfException) {
            code = ErrorCode.CSRF_TOKEN_INVALID;
        } else if (path(request).startsWith(ADMIN_API_PREFIX)) {
            code = ErrorCode.ADMIN_ONLY;
        } else {
            code = ErrorCode.API_NOT_FOUND;
        }
        write(request, response, code);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        log.info("접근 거부: code={}, path={}, memberId={}", code.name(), path(request), currentMemberId());
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code));
    }

    // 요청 경로 (getRequestURI는 쿼리를 포함하지 않는다)
    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** 체커의 거부 로그용 현재 요청 경로(쿼리 제외). 요청 밖이면 null */
    static String currentRequestPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return path(attributes.getRequest());
        }
        return null;
    }

    private static Long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginMember member) {
            return member.memberId();
        }
        return null;
    }
}
