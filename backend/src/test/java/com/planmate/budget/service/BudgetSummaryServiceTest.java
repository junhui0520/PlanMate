package com.planmate.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.budget.dto.BudgetSummaryResponse;
import com.planmate.budget.dto.BudgetSummaryResponse.CurrencyAmount;
import com.planmate.budget.dto.BudgetSummaryResponse.ExcludeReason;
import com.planmate.budget.dto.BudgetSummaryResponse.ExcludedItem;
import com.planmate.budget.entity.BudgetItem;
import com.planmate.budget.entity.BudgetItem.AmountSource;
import com.planmate.budget.entity.BudgetItem.CalcType;
import com.planmate.budget.entity.BudgetItem.Category;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.repository.BudgetItemRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripAccessChecker;

/**
 * BUD-03-01·03·04, BUD-04-02 경비 합계 계산 확인 (DB 없이 계산만).
 * 기준 숫자: 기능정의서·와이어프레임 D-11 (100엔 = 910원, 4명, 내 회원 1)
 */
class BudgetSummaryServiceTest {

	private final BudgetItemRepository repository = mock(BudgetItemRepository.class);
	private final AccountStatusChecker account = mock(AccountStatusChecker.class); // 가짜 체커
	private final TripAccessChecker tripAccess = mock(TripAccessChecker.class);
	private final BudgetSummaryService service =
			new BudgetSummaryService(repository, mock(ExchangeRateService.class), account, tripAccess);

	private static final Map<String, BigDecimal> JPY_910 = Map.of("JPY", new BigDecimal("9.1"));

	private static long nextId = 1;

	private static BudgetItem item(Scope scope, String name, CalcType calc, String amount, String currency, long by) {
		BudgetItem i = BudgetItem.create(1L, scope, Category.ETC, name, calc,
				amount == null ? null : new BigDecimal(amount), currency, AmountSource.MANUAL, null, by);
		ReflectionTestUtils.setField(i, "itemId", nextId++);
		return i;
	}

	// 와이어프레임 D-11 경비 13건 (화면 mockData.ts·DB 시험 데이터와 같음)
	private static List<BudgetItem> d11Items() {
		nextId = 1;
		List<BudgetItem> items = new ArrayList<>();
		items.add(item(Scope.COMMON, "1일차 교통비", CalcType.PER_PERSON, "400", "JPY", 1));
		items.add(item(Scope.COMMON, "숙소", CalcType.TOTAL, "40000", "JPY", 1));
		items.add(item(Scope.COMMON, "저녁 식사", CalcType.PER_PERSON, "30000", "KRW", 2));
		items.add(item(Scope.COMMON, "입장권", CalcType.TOTAL, null, "JPY", 3)); // 가격 확인 전
		items.add(item(Scope.PERSONAL, "기념품", null, "3000", "JPY", 1));
		items.add(item(Scope.PERSONAL, "공항 리무진", null, "52000", "KRW", 1));
		items.add(item(Scope.PERSONAL, "유심", null, "20000", "KRW", 2));
		items.add(item(Scope.PERSONAL, "무료 셔틀버스", null, "0", "KRW", 2));
		items.add(item(Scope.PERSONAL, "편의점 간식", null, "1200", "JPY", 3));
		items.add(item(Scope.PERSONAL, "면세점 화장품", null, "8000", "JPY", 3));
		items.add(item(Scope.PERSONAL, "엽서", null, "600", "JPY", 3));
		items.add(item(Scope.PERSONAL, "교통카드 충전", null, "7400", "JPY", 3));
		items.add(item(Scope.PERSONAL, "공연 티켓", null, "20000", "KRW", 3));
		return items;
	}

	private static String text(List<CurrencyAmount> amounts) {
		return amounts.stream().map(a -> a.currency() + " " + a.amount().toPlainString()).toList().toString();
	}

	@Test
	void 공통_경비_합계는_와이어프레임_숫자와_같다() {
		BudgetSummaryResponse r = service.calculate(1L, d11Items(), 4, 1L, JPY_910);

		// "공통 경비 합계 약 498,560원" / "= ¥41,600 + 120,000원"
		assertEquals(498_560L, r.commonTotal().krwTotal());
		assertEquals("[JPY 41600, KRW 120000]", text(r.commonTotal().byCurrency()));
	}

	@Test
	void 내_1인_경비는_와이어프레임_숫자와_같다() {
		BudgetSummaryResponse r = service.calculate(1L, d11Items(), 4, 1L, JPY_910);

		// "약 203,940원" / "= ¥13,400 + 82,000원" / "공통 경비 몫 ¥10,400 + 30,000원 · 개인 경비 ¥3,000 + 52,000원"
		assertEquals(203_940L, r.myShare().krwTotal());
		assertEquals("[JPY 13400, KRW 82000]", text(r.myShare().byCurrency()));
		assertEquals("[JPY 10400, KRW 30000]", text(r.myShare().commonShare()));
		assertEquals("[JPY 3000, KRW 52000]", text(r.myShare().personal()));
	}

	@Test
	void 가격_확인_전은_빼고_부분_합계로_알린다() {
		BudgetSummaryResponse r = service.calculate(1L, d11Items(), 4, 1L, JPY_910);

		// "부분 합계 · 1건 제외" → "입장권 · 가격 미확인" (공통 합계·내 1인 경비 둘 다)
		ExcludedItem ticket = new ExcludedItem(4L, "입장권", ExcludeReason.PRICE_UNKNOWN);
		assertEquals(List.of(ticket), r.commonTotal().excluded());
		assertEquals(List.of(ticket), r.myShare().excluded());
	}

	@Test
	void 기능정의서_예시_공통_28만원_4명_개인_52000원이면_122000원() {
		nextId = 1;
		List<BudgetItem> items = List.of(
				item(Scope.COMMON, "숙소", CalcType.TOTAL, "280000", "KRW", 2),
				item(Scope.PERSONAL, "공항 리무진", null, "52000", "KRW", 1));

		BudgetSummaryResponse r = service.calculate(1L, items, 4, 1L, Map.of());

		assertEquals(122_000L, r.myShare().krwTotal()); // BUD-03-03 완료 기준
	}

	@Test
	void 개인_경비를_추가해도_공통_경비_합계는_바뀌지_않는다() {
		List<BudgetItem> items = d11Items();
		long before = service.calculate(1L, items, 4, 1L, JPY_910).commonTotal().krwTotal();
		items.add(item(Scope.PERSONAL, "택시", null, "1500", "JPY", 1));

		long after = service.calculate(1L, items, 4, 1L, JPY_910).commonTotal().krwTotal();

		assertEquals(before, after); // BUD-03-01 완료 기준
	}

	@Test
	void 환율이_없는_통화는_원화_합산에서만_빠진다() {
		BudgetSummaryResponse r = service.calculate(1L, d11Items(), 4, 1L, Map.of()); // 엔화 환율 없음

		assertEquals(120_000L, r.commonTotal().krwTotal()); // 원화 항목만
		assertEquals("[JPY 41600, KRW 120000]", text(r.commonTotal().byCurrency())); // 통화별 합계에는 그대로
		List<ExcludeReason> reasons = r.commonTotal().excluded().stream().map(ExcludedItem::reason).toList();
		assertEquals(List.of(ExcludeReason.PRICE_UNKNOWN, ExcludeReason.NO_RATE, ExcludeReason.NO_RATE), reasons);
	}

	@Test
	void 달러는_소수_둘째_자리로_마지막에_한_번_반올림한다() {
		nextId = 1;
		// 공통 $10.01 x 3명 = $30.03 → 1인 몫 $10.01, 개인 $0.005 + $0.005 = $0.01
		List<BudgetItem> items = List.of(
				item(Scope.COMMON, "투어", CalcType.PER_PERSON, "10.01", "USD", 2),
				item(Scope.PERSONAL, "물", null, "0.005", "USD", 1),
				item(Scope.PERSONAL, "껌", null, "0.005", "USD", 1));

		BudgetSummaryResponse r = service.calculate(1L, items, 3, 1L, Map.of("USD", new BigDecimal("1343.6")));

		assertEquals("[USD 30.03]", text(r.commonTotal().byCurrency()));
		assertEquals("[USD 10.02]", text(r.myShare().byCurrency())); // 10.01 + 0.01 (항목마다 반올림하지 않음)
		assertEquals(13_463L, r.myShare().krwTotal()); // 10.02 x 1343.6 = 13462.87… → 13,463원
	}

	@Test
	void 경비가_없으면_0원이고_빈_목록() {
		BudgetSummaryResponse r = service.calculate(1L, List.of(), 4, 1L, JPY_910);

		assertEquals(0L, r.commonTotal().krwTotal());
		assertTrue(r.commonTotal().byCurrency().isEmpty());
		assertTrue(r.myShare().excluded().isEmpty());
	}

	// ---- ACC-07 공통 접근 제어 (A의 체커) ----

	@Test
	void 내_1인_경비는_로그인한_회원_기준이다() {
		nextId = 1;
		when(account.requireLoginMemberId()).thenReturn(2L);
		when(repository.findByTripIdOrderByItemIdAsc(1L)).thenReturn(List.of(
				item(Scope.PERSONAL, "택시", null, "1000", "KRW", 1),
				item(Scope.PERSONAL, "유심", null, "300", "KRW", 2)));

		BudgetSummaryResponse r = service.getSummary(1L, 4);

		verify(tripAccess).requireParticipant(1L, 2L);
		assertEquals(300L, r.myShare().krwTotal()); // 회원 2의 개인 경비만
	}

	@Test
	void 참여자가_아니거나_휴지통_여행이면_합계를_볼_수_없다() {
		when(account.requireLoginMemberId()).thenReturn(1L);
		doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE)).when(tripAccess).requireParticipant(1L, 1L);

		BusinessException e = assertThrows(BusinessException.class, () -> service.getSummary(1L, 4));

		assertEquals(ErrorCode.RESOURCE_NOT_ACCESSIBLE, e.getErrorCode());
		verify(repository, never()).findByTripIdOrderByItemIdAsc(1L);
	}
}
