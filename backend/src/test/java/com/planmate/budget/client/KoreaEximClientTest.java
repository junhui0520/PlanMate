package com.planmate.budget.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.planmate.budget.client.KoreaEximClient.KoreaEximRate;
import com.planmate.budget.client.KoreaEximClient.RawRate;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.service.ExchangeRateService;

/**
 * BUD-04-01 한국수출입은행 환율 받아 오기 확인.
 * 실제 API 호출 테스트는 키(EXCHANGE_API_KEY 환경 변수 또는 backend/.env)가 있을 때만 돈다.
 * 키 값은 출력하지 않는다.
 */
class KoreaEximClientTest {

	// ---- 키 없이도 도는 테스트: 받은 글자 해석 ----

	@Test
	void 엔화는_100단위로_읽는다() {
		KoreaEximRate rate = KoreaEximClient.toRate(new RawRate(1, "JPY(100)", "843.41", "일본 옌"));
		assertEquals("JPY", rate.currency());
		assertEquals(100, rate.unit());
		assertEquals(new BigDecimal("843.41"), rate.rate());
	}

	@Test
	void 쉼표가_있는_금액을_숫자로_읽는다() {
		KoreaEximRate rate = KoreaEximClient.toRate(new RawRate(1, "USD", "1,343.6", "미국 달러"));
		assertEquals("USD", rate.currency());
		assertEquals(1, rate.unit());
		assertEquals(new BigDecimal("1343.6"), rate.rate());
	}

	@Test
	void 엔화_1엔_값을_계산한다() {
		KoreaEximRate rate = KoreaEximClient.toRate(new RawRate(1, "JPY(100)", "843.41", "일본 옌"));
		ExchangeRateResponse response = ExchangeRateResponse.of(rate, LocalDate.of(2026, 11, 25));
		assertEquals(0, new BigDecimal("8.4341").compareTo(response.ratePerOne()));
		assertEquals(0, new BigDecimal("843.41").compareTo(response.rate())); // 저장용 값은 발표 그대로
	}

	// ---- 실제 API 호출 (키가 있을 때만) ----

	@Test
	void 실제_환율을_받아_엔화와_달러를_보여준다() {
		ExchangeRateService service = new ExchangeRateService(new KoreaEximClient(readApiKey()));

		List<ExchangeRateResponse> rates = service.fetchLatestRates();

		assertFalse(rates.isEmpty());
		printRate(rates, "JPY");
		printRate(rates, "USD");
		ExchangeRateResponse jpy = find(rates, "JPY");
		assertEquals(100, jpy.unit());
	}

	@Test
	void 주말이면_직전_평일_환율을_찾는다() {
		ExchangeRateService service = new ExchangeRateService(new KoreaEximClient(readApiKey()));
		LocalDate sunday = LocalDate.of(2026, 11, 22);

		List<ExchangeRateResponse> rates = service.fetchLatestRates(sunday);

		LocalDate baseDate = rates.getFirst().baseDate();
		System.out.println("[확인] 일요일(" + sunday + ")에 찾은 발표 날짜: " + baseDate + " (" + baseDate.getDayOfWeek() + ")");
		assertEquals(LocalDate.of(2026, 11, 20), baseDate); // 금요일
		assertTrue(baseDate.getDayOfWeek() != DayOfWeek.SATURDAY && baseDate.getDayOfWeek() != DayOfWeek.SUNDAY);
	}

	// ---- 도움 함수 ----

	private static void printRate(List<ExchangeRateResponse> rates, String currency) {
		ExchangeRateResponse r = find(rates, currency);
		System.out.println("[확인] " + r.currency() + " 발표 " + r.baseDate()
				+ " | " + r.unit() + " " + r.currency() + " = " + r.rate().toPlainString() + "원"
				+ " | 1 " + r.currency() + " = " + r.ratePerOne().toPlainString() + "원");
	}

	private static ExchangeRateResponse find(List<ExchangeRateResponse> rates, String currency) {
		return rates.stream().filter(r -> r.currency().equals(currency)).findFirst().orElseThrow();
	}

	// 환경 변수가 없으면 backend/.env에서 읽는다. 둘 다 없으면 이 테스트는 건너뜀.
	private static String readApiKey() {
		String key = System.getenv("EXCHANGE_API_KEY");
		if (key == null || key.isBlank()) {
			key = readFromDotEnv(Path.of(".env"));
		}
		assumeTrue(key != null && !key.isBlank(), "EXCHANGE_API_KEY가 없어 실제 호출 테스트를 건너뜁니다.");
		return key;
	}

	private static String readFromDotEnv(Path path) {
		if (!Files.exists(path)) {
			return null;
		}
		try {
			return Files.readAllLines(path).stream()
					.map(String::trim)
					.filter(line -> line.startsWith("EXCHANGE_API_KEY="))
					.map(line -> line.substring("EXCHANGE_API_KEY=".length()).replace("\"", "").trim())
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

}
