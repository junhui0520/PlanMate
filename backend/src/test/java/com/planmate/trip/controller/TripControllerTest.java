package com.planmate.trip.controller;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.trip.dto.TripCreateResponse;
import com.planmate.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TRP-01-03 여행 생성 API 확인 (contracts/trip.yaml POST /api/trips).
 * 입력 검사(@Valid)·로그인 확인·응답 모양만 본다. DB 없이 서비스·로그인 검사는 가짜로 둔다.
 */
@WebMvcTest(TripController.class)
class TripControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripService tripService;

    @MockitoBean
    private AccountStatusChecker accountStatusChecker;

    @Test
    @WithMockUser
    void 올바른_입력이면_만든_여행_번호를_공통_응답으로_돌려준다() throws Exception {
        givenLoginMember();
        given(tripService.create(any(), eq(MEMBER_ID))).willReturn(new TripCreateResponse(12L));

        createTrip("도쿄 여행", 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tripId").value(12))
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    @WithMockUser
    void 로그인_회원이_아니면_401_LOGIN_REQUIRED() throws Exception {
        given(accountStatusChecker.requireLoginMemberId())
                .willThrow(new BusinessException(ErrorCode.LOGIN_REQUIRED));

        createTrip("도쿄 여행", 4)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("LOGIN_REQUIRED"));
        then(tripService).should(never()).create(any(), any());
    }

    @Test
    @WithMockUser
    void 제목이_공백뿐이면_제목_입력_안내() throws Exception {
        createTrip("   ", 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.data.fieldErrors[0].message").value("여행 제목을 입력해 주세요."));
        // 입력 검사에서 막히면 서비스까지 가지 않는다
        then(tripService).should(never()).create(any(), any());
    }

    @Test
    @WithMockUser
    void 인원이_21명이면_인원_안내() throws Exception {
        createTrip("도쿄 여행", 21)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("peopleCount"))
                .andExpect(jsonPath("$.data.fieldErrors[0].message").value("여행 인원은 1~20 사이의 정수로 입력해 주세요."));
    }

    @Test
    @WithMockUser
    void 인원이_0명이면_인원_안내() throws Exception {
        createTrip("도쿄 여행", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("peopleCount"));
    }

    @Test
    @WithMockUser
    void 제목이_이모지_50개면_통과한다() throws Exception {
        // 이모지 1개 = 1자 (화면 countChars·Oracle 50 CHAR와 같게)
        givenLoginMember();
        given(tripService.create(any(), eq(MEMBER_ID))).willReturn(new TripCreateResponse(12L));

        createTrip("😀".repeat(50), 4)
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void 제목이_이모지_51개면_입력값_오류() throws Exception {
        createTrip("😀".repeat(51), 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("title"));
    }

    @Test
    @WithMockUser
    void 제목_앞뒤_공백은_50자에_세지_않는다() throws Exception {
        givenLoginMember();
        given(tripService.create(any(), eq(MEMBER_ID))).willReturn(new TripCreateResponse(12L));

        createTrip("  " + "가".repeat(50) + "  ", 4)
                .andExpect(status().isOk());
    }

    // 로그인한 정상 회원 1번
    private void givenLoginMember() {
        given(accountStatusChecker.requireLoginMemberId()).willReturn(MEMBER_ID);
    }

    // POST /api/trips 요청 보내기 (날짜는 형식만 맞으면 됨: 범위 검사는 서비스에서 함)
    private ResultActions createTrip(String title, int peopleCount) throws Exception {
        String body = """
                {
                  "requestKey": "3f8c2a9e-1b47-4c6d-9a0e-5d2f7b8c1e34",
                  "title": "%s",
                  "cityId": 1,
                  "startDate": "2027-01-10",
                  "endDate": "2027-01-12",
                  "peopleCount": %d
                }
                """.formatted(title, peopleCount);
        return mockMvc.perform(post("/api/trips")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}