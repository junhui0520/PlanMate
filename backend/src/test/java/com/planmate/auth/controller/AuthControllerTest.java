package com.planmate.auth.controller;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.repository.LoginAttemptRepository;
import com.planmate.auth.service.AuthHasher;
import com.planmate.auth.service.LoginService;
import com.planmate.auth.service.SignupService;
import com.planmate.common.config.WebConfig;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.SecurityConfig;
import com.planmate.common.security.SecurityErrorHandler;
import com.planmate.common.security.SessionPolicyFilter;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ACC-01-02·03·04 가입·인증, ACC-02-01·02 로그인·로그아웃 API 응답 모양·입력 검사·요청 제한·CSRF·세션.
 * 가입 서비스는 가짜, 로그인 서비스는 실제 객체(세션·CSRF 처리를 보려고)이고 저장소·체커만 가짜.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, WebConfig.class, LoginService.class})
class AuthControllerTest {

    private static final String LOGIN_EMAIL = "user@example.com";
    private static final String LOGIN_PASSWORD = "Abcd1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private SignupService signupService;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private AuthHasher authHasher;

    @MockitoBean
    private AccountStatusChecker accountStatusChecker;

    @Test
    void 인증번호_받기_성공_응답_이메일은_정규화해서_넘긴다() throws Exception {
        given(signupService.sendCode(eq("user+trip@example.com"), anyString()))
                .willReturn(new VerificationSendResponse(7L, 300, 60));

        postJson("/api/auth/signup/verification-codes", "{\"email\":\"  User+Trip@Example.COM \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(7))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60))
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void 인증번호_확인_성공_응답() throws Exception {
        given(signupService.confirmCode(7L, "012345")).willReturn(new VerificationConfirmResponse("token-abc", 300));

        postJson("/api/auth/signup/verification-codes/confirm", "{\"requestId\":7,\"code\":\"012345\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.confirmToken").value("token-abc"))
                .andExpect(jsonPath("$.data.confirmExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void 가입_성공_응답은_data와_message가_비어_있다() throws Exception {
        postJson("/api/auth/signup", signupBody("user@example.com", "Abcd1234!", "Abcd1234!", "여행자_1", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").isEmpty());
        verify(signupService).signup(any(SignupRequest.class));
    }

    @Test
    void 잘못된_이메일은_email_필드_오류() throws Exception {
        postJson("/api/auth/signup/verification-codes", "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(fieldMessage("email", "이메일 주소를 확인해 주세요."));
        verify(signupService, never()).sendCode(anyString(), anyString());
    }

    @Test
    void 인증번호가_6자리_숫자가_아니면_code_필드_오류() throws Exception {
        postJson("/api/auth/signup/verification-codes/confirm", "{\"requestId\":7,\"code\":\"12a45\"}")
                .andExpect(status().isBadRequest())
                .andExpect(fieldMessage("code", "인증번호가 일치하지 않아요."));
    }

    @Test
    void 가입_입력_오류는_칸별_이름과_문구로_나온다() throws Exception {
        postJson("/api/auth/signup", signupBody("bad", "short", "different", "a", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(fieldMessage("email", "이메일 주소를 확인해 주세요."))
                .andExpect(fieldMessage("password", "비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해 주세요."))
                .andExpect(fieldMessage("passwordConfirmed", "비밀번호가 일치하지 않아요."))
                .andExpect(fieldMessage("nickname", "닉네임은 한글·영문·숫자·밑줄로 2~12자 입력해 주세요."))
                .andExpect(fieldMessage("requiredAgreed", "입력값을 확인해 주세요."));
        verify(signupService, never()).signup(any());
    }

    @Test
    void 비밀번호에_공백이나_한글이_있으면_password_필드_오류() throws Exception {
        postJson("/api/auth/signup", signupBody("user@example.com", "Abcd 1234!", "Abcd 1234!", "여행자_1", true))
                .andExpect(status().isBadRequest())
                .andExpect(fieldMessage("password", "비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해 주세요."));
        postJson("/api/auth/signup", signupBody("user@example.com", "Abcd1234!가", "Abcd1234!가", "여행자_1", true))
                .andExpect(status().isBadRequest())
                .andExpect(fieldMessage("password", "비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해 주세요."));
    }

    @Test
    void 예약어_닉네임은_대소문자_무시하고_nicknameAllowed_필드_오류() throws Exception {
        postJson("/api/auth/signup", signupBody("user@example.com", "Abcd1234!", "Abcd1234!", "AdMin", true))
                .andExpect(status().isBadRequest())
                .andExpect(fieldMessage("nicknameAllowed", "사용할 수 없는 닉네임이에요."));
    }

    @Test
    void 요청_제한이면_429와_Retry_After_헤더() throws Exception {
        given(signupService.sendCode(anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.VERIFICATION_REQUEST_LIMITED, Duration.ofSeconds(42)));

        postJson("/api/auth/signup/verification-codes", "{\"email\":\"user@example.com\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.data.code").value("VERIFICATION_REQUEST_LIMITED"))
                .andExpect(jsonPath("$.message").value("잠시 후 다시 요청해 주세요."));
    }

    @Test
    void CSRF_없이_POST하면_403_CSRF_TOKEN_INVALID() throws Exception {
        mockMvc.perform(post("/api/auth/signup/verification-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("CSRF_TOKEN_INVALID"));
        verify(signupService, never()).sendCode(anyString(), anyString());
    }

    // ===== ACC-02-01 로그인 =====

    @Test
    void 로그인_성공_응답과_세션_교체_새_세션에_로그인_정보_새_CSRF_쿠키() throws Exception {
        givenMember(Member.SystemRole.USER, Member.Status.ACTIVE);
        MockHttpSession oldSession = new MockHttpSession();
        oldSession.setAttribute("OLD_ATTRIBUTE", "old");

        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).session(oldSession)
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(LOGIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("테스트용"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"))
                .andExpect(jsonPath("$.message").isEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        assertThat(oldSession.isInvalid()).isTrue();
        HttpSession newSession = result.getRequest().getSession(false);
        assertThat(newSession).isNotNull().isNotSameAs(oldSession);
        assertThat(newSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        assertThat(newSession.getAttribute(SessionPolicyFilter.LOGIN_AT_ATTRIBUTE)).isInstanceOf(Long.class);
        assertThat(newSession.getAttribute("OLD_ATTRIBUTE")).isNull(); // 이전 세션 속성은 넘기지 않음
        assertThat(result.getResponse().getHeaders("Set-Cookie")).filteredOn(c -> c.startsWith("XSRF-TOKEN=")).hasSize(1);
    }

    @Test
    void 관리자는_로그인할_때부터_무활동_15분() throws Exception {
        givenMember(Member.SystemRole.ADMIN, Member.Status.ACTIVE);

        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(LOGIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemRole").value("ADMIN"))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(900);
    }

    @Test
    void 로그인_실패는_401_LOGIN_FAILED() throws Exception {
        given(memberRepository.findByEmailNormalized(LOGIN_EMAIL)).willReturn(Optional.empty());

        postJson("/api/auth/login", loginBody(LOGIN_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."));
    }

    @Test
    void 로그인_시도가_많으면_429_LOGIN_ATTEMPTS_LIMITED_Retry_After_없음() throws Exception {
        given(loginAttemptRepository.countByIpHashAndAttemptedAtAfter(any(), any())).willReturn(50L);

        postJson("/api/auth/login", loginBody(LOGIN_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.data.code").value("LOGIN_ATTEMPTS_LIMITED"))
                .andExpect(jsonPath("$.message").value("로그인 시도가 많아요. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void 탈퇴_회원은_403_ACCOUNT_UNAVAILABLE() throws Exception {
        givenMember(Member.SystemRole.USER, Member.Status.WITHDRAWN);

        postJson("/api/auth/login", loginBody(LOGIN_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("ACCOUNT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("이용할 수 없는 계정이에요."));
    }

    @Test
    void 빈_칸_로그인은_400_INVALID_INPUT() throws Exception {
        postJson("/api/auth/login", "{\"email\":\"\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(fieldMessage("email", "이메일 또는 비밀번호를 확인해 주세요."))
                .andExpect(fieldMessage("password", "이메일 또는 비밀번호를 확인해 주세요."));
        verify(loginAttemptRepository, never()).save(any());
    }

    // ===== ACC-02-02 로그아웃 =====

    @Test
    void 로그아웃하면_세션을_무효화하고_200() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 세션이_없어도_로그아웃은_200() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void CSRF_없이_로그아웃하면_403() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("CSRF_TOKEN_INVALID"));
        assertThat(session.isInvalid()).isFalse();
    }

    // ===== 로그인 상태·CSRF 토큰 =====

    @Test
    void 비로그인이면_me의_data는_null() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 로그인_세션이면_me는_내_요약() throws Exception {
        given(accountStatusChecker.findLoginMemberId()).willReturn(Optional.of(1L));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(Member.SystemRole.USER, Member.Status.ACTIVE)));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("테스트용"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"));
    }

    @Test
    void csrf_토큰_발급은_204_본문_없음() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // ===== 도우미 =====

    private void givenMember(Member.SystemRole role, Member.Status status) {
        given(memberRepository.findByEmailNormalized(LOGIN_EMAIL)).willReturn(Optional.of(member(role, status)));
    }

    private Member member(Member.SystemRole role, Member.Status status) {
        Member member = Member.createEmailMember("테스트용", LOGIN_EMAIL, passwordEncoder.encode(LOGIN_PASSWORD));
        ReflectionTestUtils.setField(member, "memberId", 1L);
        ReflectionTestUtils.setField(member, "systemRole", role);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private static String loginBody(String password) {
        return "{\"email\":\" User@Example.com \",\"password\":\"" + password + "\"}";
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static org.springframework.test.web.servlet.ResultMatcher fieldMessage(String field, String message) {
        return jsonPath("$.data.fieldErrors[?(@.field == '" + field + "')].message").value(hasItem(message));
    }

    private static String signupBody(String email, String password, String passwordConfirm, String nickname,
                                     boolean requiredAgreed) {
        return """
                {"email":"%s","confirmToken":"token","password":"%s","passwordConfirm":"%s","nickname":"%s","requiredAgreed":%s}
                """.formatted(email, password, passwordConfirm, nickname, requiredAgreed);
    }
}
