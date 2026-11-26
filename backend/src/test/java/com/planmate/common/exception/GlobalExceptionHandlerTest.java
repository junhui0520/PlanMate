package com.planmate.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공통 에러 응답 형식 확인 (DB·Spring 컨텍스트 없이) */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 규칙_위반은_ErrorCode의_상태와_문구로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("DUPLICATE_REQUEST"))
                .andExpect(jsonPath("$.data.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.message").value("이미 처리된 요청이에요."));
    }

    @Test
    void 입력값_검증_실패는_칸별_오류를_담는다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.data.fieldErrors[0].message").value("테스트 문구"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."));
    }

    @Test
    void 잘못된_JSON_본문은_INVALID_INPUT() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.fieldErrors").isEmpty());
    }

    @Test
    void 처리하지_못한_예외는_INTERNAL_SERVER_ERROR() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."));
    }

    /** 테스트용 컨트롤러 */
    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/runtime")
        void runtime() {
            throw new RuntimeException("테스트 예외");
        }
    }

    record TestRequest(@NotBlank(message = "테스트 문구") String title) {
    }
}
