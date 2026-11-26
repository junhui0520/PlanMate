package com.planmate.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 나라 코드 → 경비 통화 (BUD-01-02 통화 기본값). 자바 나라별 통화 + 은행 표기 덮어쓰기.
 */
class CountryCurrencyTest {

	@Test
	void 나라별_통화() {
		assertEquals("JPY", CountryCurrency.of("JP"));
		assertEquals("KRW", CountryCurrency.of("KR"));
		assertEquals("USD", CountryCurrency.of("US"));
		assertEquals("EUR", CountryCurrency.of("FR"));
		assertEquals("THB", CountryCurrency.of("TH"));
		assertEquals("VND", CountryCurrency.of("VN")); // 은행 환율 없음 → 합계에서 "환율 없음"
	}

	@Test
	void 은행_표기가_다른_중국은_CNH() {
		assertEquals("CNH", CountryCurrency.of("CN"));
	}

	@Test
	void CHAR_2_칸의_공백과_소문자도_처리() {
		assertEquals("JPY", CountryCurrency.of(" jp"));
	}

	@Test
	void 없거나_알_수_없는_나라는_원화() {
		assertEquals("KRW", CountryCurrency.of(null));
		assertEquals("KRW", CountryCurrency.of("  "));
		assertEquals("KRW", CountryCurrency.of("ZZ"));
		assertEquals("KRW", CountryCurrency.of("AQ")); // 통화가 없는 곳(남극)
	}
}
