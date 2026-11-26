package com.planmate.budget.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.BudgetItemRequest;
import com.planmate.budget.dto.BudgetListResponse;
import com.planmate.budget.dto.BudgetSummaryResponse;
import com.planmate.budget.dto.BudgetSummaryResponse.BudgetTotal;
import com.planmate.budget.dto.BudgetSummaryResponse.CurrencyAmount;
import com.planmate.budget.dto.BudgetSummaryResponse.MyShareTotal;
import com.planmate.budget.entity.BudgetItem.AmountSource;
import com.planmate.budget.entity.BudgetItem.CalcType;
import com.planmate.budget.entity.BudgetItem.Category;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.service.BudgetService;
import com.planmate.budget.service.BudgetSummaryService;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.participant.entity.TripMember;

/**
 * 경비 API 주소·응답 모양 확인 (contracts/budget.yaml): BUD-01-01 목록, BUD-03 합계, BUD-01-02 추가, BUD-01-04 수정·삭제, BUD-02-03 메모. DB 없이 서비스는 가짜로 둔다.
 */
@WebMvcTest(BudgetController.class)
class BudgetControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BudgetService budgetService;

	@MockitoBean
	private BudgetSummaryService budgetSummaryService;

	// 도쿄 4명 3박 4일, 나는 소유자
	private static BudgetListResponse list(Long tripId, List<BudgetItemResponse> items) {
		return new BudgetListResponse(tripId, "JPY", 4, 4, 1L, TripMember.Role.OWNER, List.of(), items);
	}

	@Test
	@WithMockUser
	void 경비_목록을_공통_응답_모양으로_돌려준다() throws Exception {
		given(budgetService.getBudgetList(1L)).willReturn(list(1L, List.of(
				new BudgetItemResponse(2L, Scope.COMMON, Category.LODGING, "숙소", CalcType.TOTAL,
						new BigDecimal("40000"), "JPY", AmountSource.MANUAL, null, null, "3박, 조식 포함", 1L),
				new BudgetItemResponse(4L, Scope.COMMON, Category.SIGHTSEEING, "입장권", CalcType.TOTAL,
						null, "JPY", AmountSource.MANUAL, null, null, null, 3L),
				new BudgetItemResponse(5L, Scope.PERSONAL, Category.ETC, "기념품", null,
						new BigDecimal("3000"), "JPY", AmountSource.MANUAL, null, null, null, 1L))));

		mockMvc.perform(get("/api/trips/1/budget-items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				// 경비 탭이 쓰는 여행 정보 (B의 여행 기본 정보)
				.andExpect(jsonPath("$.data.tripCurrency").value("JPY"))
				.andExpect(jsonPath("$.data.memberCount").value(4))
				.andExpect(jsonPath("$.data.tripDays").value(4))
				.andExpect(jsonPath("$.data.myMemberId").value(1))
				.andExpect(jsonPath("$.data.myRole").value("OWNER"))
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].itemId").value(2))
				.andExpect(jsonPath("$.data.items[0].scope").value("COMMON"))
				.andExpect(jsonPath("$.data.items[0].calcType").value("TOTAL"))
				.andExpect(jsonPath("$.data.items[0].amount").value(40000))
				.andExpect(jsonPath("$.data.items[0].memo").value("3박, 조식 포함"))
				// BUD-02-03 계산 근거 칸도 함께 나감 (없으면 null)
				.andExpect(jsonPath("$.data.items[0].calcNote").isEmpty())
				.andExpect(jsonPath("$.data.items[0].estimateBasis").isEmpty())
				// 가격 확인 전은 0이 아니라 null
				.andExpect(jsonPath("$.data.items[1].amount").isEmpty())
				// 개인 경비는 계산 방식 없음
				.andExpect(jsonPath("$.data.items[2].scope").value("PERSONAL"))
				.andExpect(jsonPath("$.data.items[2].calcType").isEmpty());
	}

	@Test
	@WithMockUser
	void 경비가_없으면_빈_목록() throws Exception {
		given(budgetService.getBudgetList(9L)).willReturn(list(9L, List.of()));

		mockMvc.perform(get("/api/trips/9/budget-items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 로그인하지_않으면_막힌다() throws Exception {
		mockMvc.perform(get("/api/trips/1/budget-items"))
				.andExpect(status().isUnauthorized());
	}

	private static final String COMMON_LUNCH = """
			{"scope":"COMMON","category":"FOOD","name":"점심","calcType":"PER_PERSON",
			 "amount":2000,"priceUnknown":false,"currency":"JPY","memo":null,"budgetRule":null}
			""";

	private static BudgetItemResponse lunch() {
		return new BudgetItemResponse(14L, Scope.COMMON, Category.FOOD, "점심", CalcType.PER_PERSON,
				new BigDecimal("2000"), "JPY", AmountSource.MANUAL, null, null, null, 1L);
	}

	@Test
	@WithMockUser
	void 합계를_돌려준다() throws Exception {
		CurrencyAmount jpy = new CurrencyAmount("JPY", new BigDecimal("41000"));
		given(budgetSummaryService.getSummary(1L)).willReturn(new BudgetSummaryResponse(1L,
				new BudgetTotal(373100L, List.of(jpy), List.of()),
				new MyShareTotal(93275L, List.of(jpy), List.of(), List.of(jpy), List.of())));

		mockMvc.perform(get("/api/trips/1/budget-summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.commonTotal.krwTotal").value(373100))
				.andExpect(jsonPath("$.data.commonTotal.byCurrency[0].currency").value("JPY"));
	}

	@Test
	@WithMockUser
	void 경비를_추가한다() throws Exception {
		given(budgetService.addBudgetItem(eq(1L), any(BudgetItemRequest.class))).willReturn(lunch());

		mockMvc.perform(post("/api/trips/1/budget-items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(COMMON_LUNCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.itemId").value(14))
				.andExpect(jsonPath("$.data.name").value("점심"));
	}

	@Test
	@WithMockUser
	void 구분_분류가_없으면_INVALID_INPUT이고_서비스를_부르지_않는다() throws Exception {
		mockMvc.perform(post("/api/trips/1/budget-items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"점심\",\"amount\":1,\"currency\":\"KRW\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
		verify(budgetService, never()).addBudgetItem(any(), any());
	}

	@Test
	@WithMockUser
	void 예산_기준_일수가_범위를_넘으면_INVALID_INPUT() throws Exception {
		mockMvc.perform(post("/api/trips/1/budget-items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope":"COMMON","category":"FOOD","name":"식비","calcType":"TOTAL","currency":"JPY",
								 "budgetRule":{"baseAmount":1000,"multiplyPeople":true,"days":8,"times":null}}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
	}

	@Test
	@WithMockUser
	void 서비스가_막으면_기능정의서_문구가_그대로_나간다() throws Exception {
		given(budgetService.addBudgetItem(eq(1L), any(BudgetItemRequest.class)))
				.willThrow(new BusinessException(ErrorCode.BUDGET_COMMON_LIMIT));

		mockMvc.perform(post("/api/trips/1/budget-items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(COMMON_LUNCH))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data.code").value("BUDGET_COMMON_LIMIT"))
				.andExpect(jsonPath("$.message").value("공통 경비는 최대 200개까지 추가할 수 있어요."));
	}

	@Test
	@WithMockUser
	void CSRF_토큰이_없으면_추가를_막는다() throws Exception {
		mockMvc.perform(post("/api/trips/1/budget-items")
						.contentType(MediaType.APPLICATION_JSON).content(COMMON_LUNCH))
				.andExpect(status().isForbidden()); // 본문(CSRF_TOKEN_INVALID)은 A의 SecurityErrorHandler가 만든다(이 시험 범위 밖)
		verify(budgetService, never()).addBudgetItem(any(), any());
	}

	@Test
	@WithMockUser
	void 경비를_수정한다() throws Exception {
		given(budgetService.updateBudgetItem(eq(1L), eq(14L), any(BudgetItemRequest.class))).willReturn(lunch());

		mockMvc.perform(put("/api/trips/1/budget-items/14").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(COMMON_LUNCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.itemId").value(14));
	}

	@Test
	@WithMockUser
	void 경비를_삭제하면_data는_없다() throws Exception {
		mockMvc.perform(delete("/api/trips/1/budget-items/14").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isEmpty());
		verify(budgetService).deleteBudgetItem(1L, 14L);
	}

	@Test
	@WithMockUser
	void 이미_삭제된_경비면_404_문구() throws Exception {
		doThrow(new BusinessException(ErrorCode.BUDGET_ITEM_NOT_FOUND))
				.when(budgetService).deleteBudgetItem(1L, 99L);

		mockMvc.perform(delete("/api/trips/1/budget-items/99").with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("이미 삭제된 경비예요."));
	}

	@Test
	@WithMockUser
	void 메모를_저장한다() throws Exception {
		given(budgetService.updateBudgetMemo(1L, 14L, "3박, 조식 포함")).willReturn(lunch());

		mockMvc.perform(patch("/api/trips/1/budget-items/14/memo").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"3박, 조식 포함\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.itemId").value(14));
		verify(budgetService).updateBudgetMemo(1L, 14L, "3박, 조식 포함");
	}

	@Test
	void 로그인하지_않으면_합계도_막힌다() throws Exception {
		mockMvc.perform(get("/api/trips/1/budget-summary"))
				.andExpect(status().isUnauthorized());
	}
}
