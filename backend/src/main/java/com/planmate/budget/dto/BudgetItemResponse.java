package com.planmate.budget.dto;

import java.math.BigDecimal;

import com.planmate.budget.entity.BudgetItem;

/**
 * BUD-01-01 경비 항목 한 줄 (contracts/budget.yaml BudgetItemResponse).
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
				item.getMemo(),
				item.getCreatedBy());
	}

}
