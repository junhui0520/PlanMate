package com.planmate.budget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.service.BudgetService;
import com.planmate.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 경비 요청 받기 (BUD-01~03). 약속: contracts/budget.yaml
 */
@RestController
@RequestMapping("/api/trips/{tripId}/budget-items")
@RequiredArgsConstructor
public class BudgetController {

	private final BudgetService budgetService;

	// BUD-01-01 경비 목록
	@GetMapping
	public ApiResponse<List<BudgetItemResponse>> getBudgetItems(@PathVariable Long tripId) {
		return ApiResponse.ok(budgetService.getBudgetItems(tripId));
	}

}
