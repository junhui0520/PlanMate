package com.planmate.budget.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.planmate.budget.client.KoreaEximClient.KoreaEximRate;

/**
 * BUD-04 환율 정보.
 * rate·unit은 발표 그대로(예: 100엔 = 843.41원), ratePerOne은 1단위당 원화(예: 1엔 = 8.4341원).
 */
public record ExchangeRateResponse(
		String currency, // 통화 코드 (EXCHANGE_RATE.CURRENCY)
		LocalDate baseDate, // 발표 날짜 (EXCHANGE_RATE.BASE_DATE)
		BigDecimal rate, // 단위당 원화 (EXCHANGE_RATE.RATE)
		int unit, // 1 또는 100 (EXCHANGE_RATE.UNIT)
		BigDecimal ratePerOne) { // 1단위당 원화 (저장하지 않고 계산용·확인용)

	// 1단위당 원화는 DB 환율 칸(소수 4자리)보다 넉넉하게 6자리까지
	private static final int RATE_PER_ONE_SCALE = 6;

	public static ExchangeRateResponse of(KoreaEximRate rate, LocalDate baseDate) {
		BigDecimal perOne = rate.rate()
				.divide(BigDecimal.valueOf(rate.unit()), RATE_PER_ONE_SCALE, RoundingMode.HALF_UP)
				.stripTrailingZeros();
		return new ExchangeRateResponse(rate.currency(), baseDate, rate.rate(), rate.unit(), perOne);
	}

}
