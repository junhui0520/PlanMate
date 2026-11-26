package com.planmate.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.planmate.budget.client.KoreaEximClient;
import com.planmate.budget.client.KoreaEximClient.KoreaEximRate;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.ExchangeRate;
import com.planmate.budget.repository.ExchangeRateRepository;

/**
 * BUD-04-01 환율 저장 규칙 확인. 은행 API와 DB는 가짜(Mock)로 둔다.
 */
class ExchangeRateServiceTest {

	private KoreaEximClient client;
	private ExchangeRateRepository repository;
	private ExchangeRateService service;

	private static final KoreaEximRate JPY = new KoreaEximRate("JPY", 100, new BigDecimal("843.41"), "일본 옌");
	private static final KoreaEximRate USD = new KoreaEximRate("USD", 1, new BigDecimal("1343.6"), "미국 달러");

	@BeforeEach
	void setUp() {
		client = mock(KoreaEximClient.class);
		repository = mock(ExchangeRateRepository.class);
		service = new ExchangeRateService(client, repository);
	}

	@Test
	void 표가_비어_있으면_거꾸로_찾아_처음_채운다() {
		when(repository.count()).thenReturn(0L);
		when(client.fetchRates(any())).thenReturn(List.of()).thenReturn(List.of(JPY, USD)); // 오늘 빈 값, 어제 발표 있음

		int saved = service.refreshTodayRates();

		assertEquals(2, saved);
		verify(client, times(2)).fetchRates(any());
		verify(repository, times(2)).save(any(ExchangeRate.class));
	}

	@Test
	void 표가_있고_오늘_빈_값이면_저장하지_않고_다시_부르지_않는다() {
		when(repository.count()).thenReturn(23L);
		when(client.fetchRates(any())).thenReturn(List.of()); // 주말·공휴일·11시 전

		int saved = service.refreshTodayRates();

		assertEquals(0, saved);
		verify(client, times(1)).fetchRates(any()); // 하루 1번만 부름
		verify(repository, never()).save(any());
	}

	@Test
	void 오늘_발표가_있으면_없는_통화만_저장한다() {
		when(repository.count()).thenReturn(23L);
		when(client.fetchRates(any())).thenReturn(List.of(JPY, USD));
		when(repository.existsById(any())).thenReturn(true, false); // JPY는 이미 있음, USD는 없음

		int saved = service.refreshTodayRates();

		assertEquals(1, saved);
		ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
		verify(repository).save(captor.capture());
		assertEquals("USD", captor.getValue().getCurrency());
	}

	@Test
	void 엔화는_100엔_단위_그대로_저장한다() {
		when(repository.count()).thenReturn(23L);
		when(client.fetchRates(any())).thenReturn(List.of(JPY));
		when(repository.existsById(any())).thenReturn(false);

		service.refreshTodayRates();

		ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
		verify(repository).save(captor.capture());
		assertEquals(100, captor.getValue().getUnit());
		assertEquals(0, new BigDecimal("843.41").compareTo(captor.getValue().getRate()));
	}

	@Test
	void 조회에_실패하면_예외가_나가고_저장하지_않는다() {
		when(repository.count()).thenReturn(23L);
		when(client.fetchRates(any())).thenThrow(new IllegalStateException("한국수출입은행 환율 API 호출 실패"));

		assertThrows(IllegalStateException.class, () -> service.refreshTodayRates());
		verify(repository, never()).save(any());
	}

	@Test
	void 오늘_환율은_표의_통화별_가장_최근_행이고_1단위_값도_준다() {
		when(repository.findLatestPerCurrency()).thenReturn(List.of(
				new ExchangeRate("JPY", LocalDate.of(2026, 11, 25), new BigDecimal("843.41"), 100)));

		List<ExchangeRateResponse> rates = service.getTodayRates();

		assertEquals(1, rates.size());
		assertEquals(0, new BigDecimal("8.4341").compareTo(rates.getFirst().ratePerOne()));
		verify(client, never()).fetchRates(any()); // 화면이 볼 때는 은행 API를 부르지 않음
	}

}
