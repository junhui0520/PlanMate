package com.planmate.auth.controller;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.VerificationConfirmResponse;
import com.planmate.auth.dto.VerificationSendResponse;
import com.planmate.auth.service.SignupService;
import com.planmate.common.config.WebConfig;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.SecurityConfig;
import com.planmate.common.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ACC-01-02·03·04 가입·인증 API 응답 모양·입력 검사·요청 제한·CSRF (서비스는 가짜) */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, WebConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupService signupService;

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

    // ===== 도우미 =====

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
