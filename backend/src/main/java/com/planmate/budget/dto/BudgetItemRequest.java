package com.planmate.budget.dto;

import java.math.BigDecimal;

import com.planmate.budget.entity.BudgetItem;

/**
 * BUD-01-02 경비 항목 추가 입력값 (contracts/budget.yaml BudgetItemRequest).
 * 만든 사람은 받지 않는다(서버가 로그인 정보로 채움).
 * TODO(D): 추가 주소를 열 때 @Valid 검증 어노테이션(message에 기능정의서 문구)을 붙인다. 지금은 BudgetService에서 검사.
 */
public record BudgetItemRequest(
		BudgetItem.Scope scope, // 누른 버튼으로 정해짐 (공통 / 내 개인)
		BudgetItem.Category category, // 필수
		String name, // 필수, 앞뒤 공백 제거 후 1~50자
		BudgetItem.CalcType calcType, // 공통만 필수, 개인은 비움
		BigDecimal amount, // 0 이상, 원화 기준 1억 이하, 통화 표준 자릿수까지. 가격 확인 전이면 비움
		boolean priceUnknown, // '가격 확인 전' 선택 (정한 내용 11/26: 금액 빈칸과 구분하려고 둠)
		String currency, // 필수, 원화 또는 환율이 있는 통화
		String memo) { // 공통만, 0~200자
}
