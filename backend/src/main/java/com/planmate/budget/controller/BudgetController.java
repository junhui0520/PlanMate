package com.planmate.budget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.TempApiResponse;
import com.planmate.budget.service.BudgetService;

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
	// TODO(공통): common/response/ApiResponse 가 생기면 TempApiResponse 대신 쓴다.
	@GetMapping
	public TempApiResponse<List<BudgetItemResponse>> getBudgetItems(@PathVariable Long tripId) {
		return TempApiResponse.ok(budgetService.getBudgetItems(tripId));
	}

}
