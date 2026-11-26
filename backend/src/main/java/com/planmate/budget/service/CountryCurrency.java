package com.planmate.budget.service;

import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * 여행 도시의 나라 코드(CITY.COUNTRY_CODE, ISO 3166 두 글자) → 경비 통화 코드 (BUD-01-02 통화 기본값).
 * B의 TripService.findBasicInfo가 나라 코드를 주고, 통화로 바꾸는 일은 D가 한다(11/26 B와 정함).
 *
 * 정한 내용(11/26, A안): 도시는 세계 어디든 새로 등록되므로 표를 손으로 다 쓰지 않고
 * 자바에 들어 있는 나라별 통화(java.util.Currency)를 쓴다. 한국수출입은행 표기와 다른 나라만 아래 표로 덮어쓴다.
 * 은행 환율이 없는 통화(예: VND)도 통화 코드는 그대로 쓰고, 합계에서는 "환율 없음"으로 부분 합계 처리된다(BUD-03-04).
 */
final class CountryCurrency {

	static final String DEFAULT = "KRW"; // 나라 코드가 없거나 알 수 없으면 원화

	// 은행 환율 표기와 다른 곳: 중국 위안은 은행이 역외 위안(CNH)으로 발표한다
	private static final Map<String, String> OVERRIDES = Map.of("CN", "CNH");

	private CountryCurrency() {
	}

	static String of(String countryCode) {
		if (countryCode == null || countryCode.isBlank()) {
			return DEFAULT;
		}
		String country = countryCode.strip().toUpperCase(Locale.ROOT);
		String override = OVERRIDES.get(country);
		if (override != null) {
			return override;
		}
		try {
			Currency currency = Currency.getInstance(Locale.of("", country));
			return currency == null ? DEFAULT : currency.getCurrencyCode(); // 남극(AQ)처럼 통화가 없는 곳
		} catch (IllegalArgumentException e) {
			return DEFAULT; // ISO 나라 코드가 아님
		}
	}
}
