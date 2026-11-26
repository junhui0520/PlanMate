package com.planmate.budget.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.budget.client.KoreaEximClient;
import com.planmate.budget.client.KoreaEximClient.KoreaEximRate;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.ExchangeRate;
import com.planmate.budget.repository.ExchangeRateRepository;
import com.planmate.common.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BUD-04-01 환율 받아 저장하기 · 오늘 환율 꺼내기.
 * 모든 여행이 같은 '오늘 환율'(표의 통화별 가장 최근 행)을 쓴다. 여행별 환율 저장 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

	private static final ZoneId SEOUL = ZoneId.of(DateTimeUtil.SCHEDULER_ZONE);
	private static final int MAX_LOOKBACK_DAYS = 7; // 연휴가 길어도 일주일 안에는 발표가 있음

	private final KoreaEximClient koreaEximClient;
	private final ExchangeRateRepository exchangeRateRepository;

	/**
	 * BUD-04-01 하루 1번: 오늘 발표 환율을 받아 저장한다. 저장한 통화 수를 돌려준다.
	 * - 주말·공휴일·오전 11시 전처럼 빈 값이면 저장하지 않는다 → 직전에 저장한 환율을 그대로 쓴다.
	 * - 조회에 실패하면 예외가 나가고 저장하지 않는다 → 직전 환율을 그대로 쓰고 다음 날 다시 조회.
	 * - 정한 내용(11/26): 표가 완전히 비어 있을 때만 최대 7일 거꾸로 찾아 1번 채운다(직전 환율이 없으므로).
	 */
	@Transactional
	public int refreshTodayRates() {
		LocalDate today = LocalDate.now(SEOUL);
		if (exchangeRateRepository.count() == 0) {
			List<ExchangeRateResponse> latest = fetchLatestRates(today);
			log.info("환율 표가 비어 있어 최근 발표({}) 환율로 처음 채움", latest.getFirst().baseDate());
			return save(latest);
		}
		List<KoreaEximRate> rates = koreaEximClient.fetchRates(today);
		if (rates.isEmpty()) {
			log.info("오늘({}) 발표된 환율이 없어 직전 환율을 그대로 씀", today);
			return 0;
		}
		return save(rates.stream().map(rate -> ExchangeRateResponse.of(rate, today)).toList());
	}

	// 같은 통화·발표 날짜가 이미 있으면 건너뜀 (통화 + 발표 날짜당 1행)
	private int save(List<ExchangeRateResponse> rates) {
		int saved = 0;
		for (ExchangeRateResponse r : rates) {
			if (exchangeRateRepository.existsById(new ExchangeRate.Key(r.currency(), r.baseDate()))) {
				continue;
			}
			exchangeRateRepository.save(new ExchangeRate(r.currency(), r.baseDate(), r.rate(), r.unit()));
			saved++;
		}
		log.info("환율 {}개 저장", saved);
		return saved;
	}

	/** BUD-04-02·03 오늘 환율: 표에서 통화별 가장 최근 행 (은행 API를 부르지 않음) */
	@Transactional(readOnly = true)
	public List<ExchangeRateResponse> getTodayRates() {
		return exchangeRateRepository.findLatestPerCurrency().stream()
				.map(ExchangeRateResponse::from)
				.toList();
	}

	/**
	 * from 날짜부터 하루씩 거꾸로 찾아 가장 최근 발표 환율을 받는다(저장하지 않음).
	 * 표가 비어 있을 때 처음 채우는 데만 쓴다.
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
