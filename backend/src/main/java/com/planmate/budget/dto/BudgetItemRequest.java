package com.planmate.budget.dto;

import java.math.BigDecimal;

import com.planmate.budget.entity.BudgetItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * BUD-01-02 경비 항목 추가 · BUD-01-04 수정 입력값 (contracts/budget.yaml BudgetItemRequest).
 * 만든 사람은 받지 않는다(서버가 로그인 정보로 채움).
 * 검증: 화면이 고르게 하는 값(구분·분류·곱할 값 범위)은 여기서 @Valid로 막는다(INVALID_INPUT, 화면에서 막는 값이라 공통 문구).
 * 항목명·금액 필수 같은 기능정의서 문구가 있는 규칙과 글자 수(이모지 1글자)·1억·통화 자릿수는
 * BudgetService가 검사한다(ErrorCode가 따로 있어 화면이 그 문구를 그대로 보여 줌).
 */
public record BudgetItemRequest(
		@NotNull(message = "입력값을 확인해 주세요.") BudgetItem.Scope scope, // 누른 버튼으로 정해짐 (공통 / 내 개인)
		@NotNull(message = "입력값을 확인해 주세요.") BudgetItem.Category category, // 필수
		String name, // 필수, 앞뒤 공백 제거 후 1~50자
		BudgetItem.CalcType calcType, // 공통만 필수, 개인은 비움
		BigDecimal amount, // 0 이상, 원화 기준 1억 이하, 통화 표준 자릿수까지. 가격 확인 전·예산 기준이면 비움
		boolean priceUnknown, // '가격 확인 전' 선택 (정한 내용 11/26: 금액 빈칸과 구분하려고 둠)
		String currency, // 필수, 원화 또는 환율이 있는 통화
		String memo, // 공통만, 0~200자
		@Valid BudgetRule budgetRule) { // BUD-02-01 예산 기준 계산으로 넣을 때만 (없으면 null)

	/**
	 * BUD-02-01 예산 기준: 기준 금액 x 선택한 값들(여행 인원 / 일수 / 횟수). 금액은 서버가 직접 계산한다.
	 * 정한 내용(11/26): 곱할 값을 하나도 고르지 않으면 기준 금액 그대로.
	 */
	public record BudgetRule(
			BigDecimal baseAmount, // 필수 (없으면 "기준 금액을 입력해 주세요.")
			boolean multiplyPeople, // x 여행 인원 (인원은 여행 정보에서 자동)
			@Min(value = 1, message = "입력값을 확인해 주세요.") @Max(value = 7, message = "입력값을 확인해 주세요.")
			Integer days, // x 일수 1~7 (안 곱하면 null)
			@Min(value = 1, message = "입력값을 확인해 주세요.") @Max(value = 99, message = "입력값을 확인해 주세요.")
			Integer times) { // x 횟수 1~99 (안 곱하면 null)
	}

}
