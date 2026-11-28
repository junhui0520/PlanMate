package com.planmate.auth.controller;

import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.VerificationConfirmRequest;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.dto.VerificationSendRequest;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.service.LoginService;
import com.planmate.auth.service.SignupService;
import com.planmate.common.response.ApiResponse;
import com.planmate.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 가입·인증·로그인·로그아웃 요청 받기 (ACC-01-02·03·04, ACC-02-01·02). /api/auth/**는 로그인 없이 쓰는 주소(SecurityConfig).
 * 성공 안내 문구는 화면이 보여 주므로 서버 message는 비워 둔다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;

    /** 이메일 로그인 (ACC-02-01). 인증(트랜잭션) 뒤 세션을 새로 만든다. X-Forwarded-For는 믿지 않는다 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Member member = loginService.authenticate(request, httpRequest.getRemoteAddr());
        return ApiResponse.ok(loginService.establishSession(member, httpRequest, httpResponse));
    }

    /** 현재 로그인 로그아웃 (ACC-02-02). 세션이 없거나 만료돼도 성공 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginService.logout(httpRequest, httpResponse);
        return ApiResponse.ok();
    }

    /** 로그인 상태 (ACC-02-01). 비로그인이면 data null */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        return ApiResponse.ok(loginService.findMe());
    }

    /**
     * CSRF 토큰 쿠키 발급 (ACC-07). 204, 본문 없음.
     * 첫 화면에서 바로 로그인하거나 로그인·로그아웃 뒤 쿠키가 없을 때 화면(client.ts)이 부른다.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // 지연 생성된 토큰을 읽어 XSRF-TOKEN 쿠키가 이 응답에 써지게 한다
        return ResponseEntity.noContent().build();
    }

    /**
     * 가입 인증번호 받기·다시 받기 (ACC-01-02). 이메일만 받는다.
     * 기능정의서의 '가입용 요청 식별값'은 용도(SIGNUP)로 해석했다(이 주소가 가입 전용이라 용도가 정해짐).
     * IP는 요청 제한(IP당 시간당 30회)에만 쓰고 해시로만 저장한다. X-Forwarded-For는 믿지 않는다.
     */
    @PostMapping("/signup/verification-codes")
    public ApiResponse<VerificationSendResponse> sendSignupCode(@Valid @RequestBody VerificationSendRequest request,
                                                                HttpServletRequest httpRequest) {
        return ApiResponse.ok(signupService.sendCode(request.email(), httpRequest.getRemoteAddr()));
    }

    /** 가입 인증번호 확인 (ACC-01-03). 성공하면 일회 확인값을 돌려준다 */
    @PostMapping("/signup/verification-codes/confirm")
    public ApiResponse<VerificationConfirmResponse> confirmSignupCode(
            @Valid @RequestBody VerificationConfirmRequest request) {
        return ApiResponse.ok(signupService.confirmCode(request.requestId(), request.code()));
    }

    /** 이메일 가입 완료 (ACC-01-04). 자동 로그인 없음 */
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request);
        return ApiResponse.ok();
    }
}
