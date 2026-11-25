package com.planmate.budget.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.planmate.budget.client.KoreaEximClient;
import com.planmate.budget.client.KoreaEximClient.KoreaEximRate;
import com.planmate.budget.dto.ExchangeRateResponse;

import lombok.RequiredArgsConstructor;

/**
 * BUD-04-01 환율 조회.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int MAX_LOOKBACK_DAYS = 7; // 연휴가 길어도 일주일 안에는 발표가 있음

	private final KoreaEximClient koreaEximClient;

	/** 한국 날짜 오늘 기준 가장 최근 발표 환율 */
	public List<ExchangeRateResponse> fetchLatestRates() {
		return fetchLatestRates(LocalDate.now(SEOUL));
	}

	/**
	 * from 날짜부터 하루씩 거꾸로 찾아 가장 최근 발표 환율을 돌려준다.
	 * BUD-04-01 주말·공휴일·오전 11시 전에는 발표가 없어 빈 값이 온다.
	 * TODO(D): EXCHANGE_RATE 표가 생기면 기능정의서대로 바꾼다.
	 *          빈 값이면 다시 부르지 말고 저장된 직전 환율을 쓰고, API는 하루 1번만 부른다.
	 */
	public List<ExchangeRateResponse> fetchLatestRates(LocalDate from) {
		for (int i = 0; i < MAX_LOOKBACK_DAYS; i++) {
			LocalDate baseDate = from.minusDays(i);
			List<KoreaEximRate> rates = koreaEximClient.fetchRates(baseDate);
			if (!rates.isEmpty()) {
				return rates.stream().map(rate -> ExchangeRateResponse.of(rate, baseDate)).toList();
			}
		}
		throw new IllegalStateException("최근 " + MAX_LOOKBACK_DAYS + "일 동안 발표된 환율이 없습니다.");
	}

}
