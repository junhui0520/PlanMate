package com.planmate.budget.dto;

/**
 * BUD-02-03 펼친 칸 메모 입력값 (contracts/budget.yaml BudgetMemoRequest).
 * 공통 경비만, 0~200자·앞뒤 공백 제거·제어문자 불가는 BudgetService가 검사한다. 비우거나 null이면 메모 삭제.
 */
public record BudgetMemoRequest(
		String memo) {
}
