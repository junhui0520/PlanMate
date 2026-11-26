package com.planmate.common.security;

import com.planmate.common.config.WebConfig;
import com.planmate.common.exception.GlobalExceptionHandler;
import com.planmate.common.response.ApiResponse;
import com.planmate.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ACC-07 주소 규칙·공통 거부 응답·CSRF·CORS·세션 시간 정책 */
@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, WebConfig.class, GlobalExceptionHandler.class,
        SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 익명이_보호_API를_부르면_401_LOGIN_REQUIRED() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요해요."));
    }

    @Test
    void 회원은_보호_API_200() throws Exception {
        mockMvc.perform(get("/api/test/ping").with(authentication(user())))
                .andExpect(status().isOk());
    }

    @Test
    void 일반_회원이_관리자_API를_부르면_403_ADMIN_ONLY_관리자는_200() throws Exception {
        mockMvc.perform(get("/api/admin/ping").with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("ADMIN_ONLY"));
        mockMvc.perform(get("/api/admin/ping").with(authentication(admin())))
                .andExpect(status().isOk());
    }

    @Test
    void CSRF_토큰_없는_POST는_403_CSRF_TOKEN_INVALID_있으면_200() throws Exception {
        mockMvc.perform(post("/api/test/ping").with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(post("/api/test/ping").with(authentication(user())).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void 익명도_auth_API는_200() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void 허용_안된_주소는_회원이면_404_API_NOT_FOUND_익명이면_401() throws Exception {
        mockMvc.perform(get("/other").with(authentication(user())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("API_NOT_FOUND"));
        mockMvc.perform(get("/other"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 프론트_주소의_preflight는_200_다른_주소는_403() throws Exception {
        mockMvc.perform(options("/api/test/ping")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mockMvc.perform(options("/api/test/ping")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // MockMvc 환경에서는 XSRF-TOKEN 쿠키가 응답에 담기지 않아 bootRun curl로 확인함 (2026-11-26)

    @Test
    void 일반_회원은_로그인_13시간_뒤_401() throws Exception {
        mockMvc.perform(get("/api/test/ping").with(authentication(user())).session(loginAgo(Duration.ofHours(13))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 관리자는_로그인_9시간_뒤_401() throws Exception {
        mockMvc.perform(get("/api/admin/ping").with(authentication(admin())).session(loginAgo(Duration.ofHours(9))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_로그인_1시간_뒤_200_무활동_15분() throws Exception {
        MockHttpSession session = loginAgo(Duration.ofHours(1));
        mockMvc.perform(get("/api/admin/ping").with(authentication(admin())).session(session))
                .andExpect(status().isOk());
        assertThat(session.getMaxInactiveInterval()).isEqualTo(900);
    }

    // ===== 도우미 =====

    private static Authentication user() {
        return new LoginMember(1L, Member.SystemRole.USER).toAuthentication();
    }

    private static Authentication admin() {
        return new LoginMember(100L, Member.SystemRole.ADMIN).toAuthentication();
    }

    private static MockHttpSession loginAgo(Duration ago) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionPolicyFilter.LOGIN_AT_ATTRIBUTE, System.currentTimeMillis() - ago.toMillis());
        return session;
    }

    /** 테스트용 컨트롤러 */
    @RestController
    static class TestController {

        @GetMapping("/api/auth/ping")
        ApiResponse<String> authPing() {
            return ApiResponse.ok("pong");
        }

        @GetMapping("/api/test/ping")
        ApiResponse<String> testPing() {
            return ApiResponse.ok("pong");
        }

        @PostMapping("/api/test/ping")
        ApiResponse<String> testPost() {
            return ApiResponse.ok("pong");
        }

        @GetMapping("/api/admin/ping")
        ApiResponse<String> adminPing() {
            return ApiResponse.ok("pong");
        }

        @GetMapping("/other")
        ApiResponse<String> other() {
            return ApiResponse.ok("pong");
        }
    }
}
