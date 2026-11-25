package com.planmate.budget.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.entity.BudgetItem.AmountSource;
import com.planmate.budget.entity.BudgetItem.CalcType;
import com.planmate.budget.entity.BudgetItem.Category;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.service.BudgetService;

/**
 * BUD-01-01 경비 목록 API 응답 모양 확인 (contracts/budget.yaml). DB 없이 서비스는 가짜로 둔다.
 */
@WebMvcTest(BudgetController.class)
class BudgetControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BudgetService budgetService;

	@Test
	@WithMockUser
	void 경비_목록을_공통_응답_모양으로_돌려준다() throws Exception {
		given(budgetService.getBudgetItems(1L)).willReturn(List.of(
				new BudgetItemResponse(2L, Scope.COMMON, Category.LODGING, "숙소", CalcType.TOTAL,
						new BigDecimal("40000"), "JPY", AmountSource.MANUAL, "3박, 조식 포함", 1L),
				new BudgetItemResponse(4L, Scope.COMMON, Category.SIGHTSEEING, "입장권", CalcType.TOTAL,
						null, "JPY", AmountSource.MANUAL, null, 3L),
				new BudgetItemResponse(5L, Scope.PERSONAL, Category.ETC, "기념품", null,
						new BigDecimal("3000"), "JPY", AmountSource.MANUAL, null, 1L)));

		mockMvc.perform(get("/api/trips/1/budget-items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].itemId").value(2))
				.andExpect(jsonPath("$.data[0].scope").value("COMMON"))
				.andExpect(jsonPath("$.data[0].calcType").value("TOTAL"))
				.andExpect(jsonPath("$.data[0].amount").value(40000))
				.andExpect(jsonPath("$.data[0].memo").value("3박, 조식 포함"))
				// 가격 확인 전은 0이 아니라 null
				.andExpect(jsonPath("$.data[1].amount").isEmpty())
				// 개인 경비는 계산 방식 없음
				.andExpect(jsonPath("$.data[2].scope").value("PERSONAL"))
				.andExpect(jsonPath("$.data[2].calcType").isEmpty());
	}

	@Test
	@WithMockUser
	void 경비가_없으면_빈_목록() throws Exception {
		given(budgetService.getBudgetItems(9L)).willReturn(List.of());

		mockMvc.perform(get("/api/trips/9/budget-items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 로그인하지_않으면_막힌다() throws Exception {
		mockMvc.perform(get("/api/trips/1/budget-items"))
				.andExpect(status().isUnauthorized());
	}

}
