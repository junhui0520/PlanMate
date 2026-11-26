package com.planmate.budget.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.budget.dto.BudgetItemRequest;
import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.BudgetListResponse;
import com.planmate.budget.dto.BudgetMemoRequest;
import com.planmate.budget.dto.BudgetSummaryResponse;
import com.planmate.budget.service.BudgetService;
import com.planmate.budget.service.BudgetSummaryService;
import com.planmate.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 경비 요청 받기 (BUD-01~03). 약속: contracts/budget.yaml
 * 로그인·여행 참여자·역할 검사와 규칙 검사는 서비스가 한다(A의 AccountStatusChecker·TripAccessChecker).
 */
@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class BudgetController {

	private final BudgetService budgetService;
	private final BudgetSummaryService budgetSummaryService;

	// BUD-01-01 경비 목록 + 경비 탭이 쓰는 여행 정보
	@GetMapping("/budget-items")
	public ApiResponse<BudgetListResponse> getBudgetList(@PathVariable Long tripId) {
		return ApiResponse.ok(budgetService.getBudgetList(tripId));
	}

	// BUD-03-01·03·04 경비 합계 (공통 경비 합계, 내 1인 경비, 부분 합계)
	@GetMapping("/budget-summary")
	public ApiResponse<BudgetSummaryResponse> getSummary(@PathVariable Long tripId) {
		return ApiResponse.ok(budgetSummaryService.getSummary(tripId));
	}

	// BUD-01-02 경비 항목 추가 (BUD-02-01 예산 기준 계산 포함)
	@PostMapping("/budget-items")
	public ApiResponse<BudgetItemResponse> addBudgetItem(@PathVariable Long tripId,
			@Valid @RequestBody BudgetItemRequest request) {
		return ApiResponse.ok(budgetService.addBudgetItem(tripId, request));
	}

	// BUD-01-04 경비 항목 수정
	@PutMapping("/budget-items/{itemId}")
	public ApiResponse<BudgetItemResponse> updateBudgetItem(@PathVariable Long tripId, @PathVariable Long itemId,
			@Valid @RequestBody BudgetItemRequest request) {
		return ApiResponse.ok(budgetService.updateBudgetItem(tripId, itemId, request));
	}

	// BUD-01-04 경비 항목 삭제 (응답 data 없음)
	@DeleteMapping("/budget-items/{itemId}")
	public ApiResponse<Void> deleteBudgetItem(@PathVariable Long tripId, @PathVariable Long itemId) {
		budgetService.deleteBudgetItem(tripId, itemId);
		return ApiResponse.ok();
	}

	// BUD-02-03 펼친 칸 메모 작성·수정·삭제 (공통 경비만)
	@PatchMapping("/budget-items/{itemId}/memo")
	public ApiResponse<BudgetItemResponse> updateBudgetMemo(@PathVariable Long tripId, @PathVariable Long itemId,
			@RequestBody BudgetMemoRequest request) {
		return ApiResponse.ok(budgetService.updateBudgetMemo(tripId, itemId, request.memo()));
	}
}
