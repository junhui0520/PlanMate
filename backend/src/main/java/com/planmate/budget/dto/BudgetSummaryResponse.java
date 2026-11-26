package com.planmate.budget.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * BUD-03 경비 합계 (공통 경비 합계·내 1인 경비·부분 합계). 계산은 서버에서만 한다.
 * 모양은 화면 features/budget/api/budgetApi.ts의 BudgetSummaryResponse와 같다.
 */
public record BudgetSummaryResponse(
		Long tripId,
		BudgetTotal commonTotal, // BUD-03-01 공통 경비 합계
		MyShareTotal myShare) { // BUD-03-03 내 1인 경비

	/** 통화 하나의 합계 (통화 표준 자릿수로 반올림된 값) */
	public record CurrencyAmount(String currency, BigDecimal amount) {
	}

	/** BUD-03-04 제외 사유: 가격 미확인(통화별·원화 모두에서 뺌) / 환율 없음(원화 합산에서만 뺌) */
	public enum ExcludeReason { PRICE_UNKNOWN, NO_RATE }

	/** BUD-03-04 계산에서 빠진 항목 */
	public record ExcludedItem(Long itemId, String name, ExcludeReason reason) {
	}

	/** 합계 한 덩어리 */
	public record BudgetTotal(
			long krwTotal, // BUD-04-02 원화 합산 (원 단위, 마지막에 한 번 반올림)
			List<CurrencyAmount> byCurrency, // 통화별 합계 (원화는 맨 뒤)
			List<ExcludedItem> excluded) { // 빠진 항목 (없으면 빈 목록)
	}

	/** BUD-03-03 내 1인 경비 = 공통 경비 몫 + 내 개인 경비 */
	public record MyShareTotal(
			long krwTotal,
			List<CurrencyAmount> byCurrency,
			List<ExcludedItem> excluded,
			List<CurrencyAmount> commonShare, // 공통 경비 합계 / 여행 인원
			List<CurrencyAmount> personal) { // 내 개인 경비 합계
	}

}
