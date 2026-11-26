package com.planmate.budget.dto;

import java.math.BigDecimal;

import com.planmate.budget.entity.BudgetItem;

/**
 * BUD-01-01 경비 항목 한 줄 (contracts/budget.yaml BudgetItemResponse).
 * BUD-02-03 펼친 칸의 계산 근거(금액 계산 설명·자동 계산 기준값)도 함께 준다.
 */
public record BudgetItemResponse(
		Long itemId,
		BudgetItem.Scope scope,
		BudgetItem.Category category,
		String name,
		BudgetItem.CalcType calcType, // 개인 경비는 null
		BigDecimal amount, // 가격 확인 전이면 null (0과 다름)
		String currency,
		BudgetItem.AmountSource amountSource,
		String calcNote, // BUD-02-03 금액 계산 설명 (없으면 null)
		String estimateBasis, // BUD-02-03 자동 계산 기준값 (예산 기준 계산 항목, 없으면 null)
		String memo, // 공통 경비만
		Long createdBy) { // 개인 경비 주인

	public static BudgetItemResponse from(BudgetItem item) {
		return new BudgetItemResponse(
				item.getItemId(),
				item.getScope(),
				item.getCategory(),
				item.getName(),
				item.getCalcType(),
				item.getAmount(),
				item.getCurrency().trim(), // CHAR(3)
				item.getAmountSource(),
				item.getCalcNote(),
				item.getEstimateBasis(),
				item.getMemo(),
				item.getCreatedBy());
	}

}
