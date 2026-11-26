package com.planmate.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.budget.dto.BudgetItemRequest;
import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.BudgetListResponse;
import com.planmate.budget.dto.ExchangeRateResponse;
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
import com.planmate.participant.entity.TripMember;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.service.TripService;

/**
 * BUD-01-02 경비 항목 추가 서버 검사 확인. DB와 환율은 가짜(Mock)로 둔다.
 * 환율: 100엔 = 910원(1엔 9.1원), 1달러 1343.6원
 */
class BudgetServiceTest {

	private static final Long TRIP = 1L;
	private static final Long ME = 1L;

	private BudgetItemRepository repository;
	private TripAccessChecker tripAccess;
	private TripService tripService;
	private BudgetService service;

	@BeforeEach
	void setUp() {
		repository = mock(BudgetItemRepository.class);
		ExchangeRateService rates = mock(ExchangeRateService.class);
		when(rates.getTodayRates()).thenReturn(List.of(
				new ExchangeRateResponse("JPY", LocalDate.of(2026, 11, 24), new BigDecimal("910"), 100, new BigDecimal("9.1")),
				new ExchangeRateResponse("USD", LocalDate.of(2026, 11, 25), new BigDecimal("1343.6"), 1, new BigDecimal("1343.6"))));
		when(repository.save(any(BudgetItem.class))).thenAnswer(inv -> {
			BudgetItem item = inv.getArgument(0);
			ReflectionTestUtils.setField(item, "itemId", 14L);
			return item;
		});
		// 가짜 체커: 로그인 회원은 ME, 여행 검사는 기본으로 통과(소유자·편집 참여자)
		AccountStatusChecker account = mock(AccountStatusChecker.class);
		when(account.requireLoginMemberId()).thenReturn(ME);
		tripAccess = mock(TripAccessChecker.class);
		// 가짜 B: 도쿄 3박 4일(11/20~11/23), 여행 인원 4명
		tripService = mock(TripService.class);
		when(tripService.findBasicInfo(TRIP)).thenReturn(Optional.of(
				new TripBasicInfo(4, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23), "JP")));
		service = new BudgetService(repository, rates, account, tripAccess, tripService);
	}

	// 나를 조회 참여자로: requireEditor가 넘겨받은 기능별 문구로 거부한다 (진짜 TripAccessChecker와 같은 동작)
	private void asViewer() {
		for (ErrorCode denied : ErrorCode.values()) {
			doThrow(new BusinessException(denied)).when(tripAccess).requireEditor(TRIP, ME, denied);
		}
	}

	// 공통 경비 기본 입력 (식비, 1인당 30,000원)
	private static BudgetItemRequest common(String name, String amount, String currency) {
		return new BudgetItemRequest(Scope.COMMON, Category.FOOD, name, CalcType.PER_PERSON,
				amount == null ? null : new BigDecimal(amount), false, currency, null, null);
	}

	private static BudgetItemRequest personal(String name, String amount, String currency) {
		return new BudgetItemRequest(Scope.PERSONAL, Category.TRANSPORT, name, null,
				amount == null ? null : new BigDecimal(amount), false, currency, null, null);
	}

	private BudgetItemResponse add(BudgetItemRequest req) {
		return service.addBudgetItem(TRIP, req);
	}

	private static void assertError(ErrorCode expected, Executable action) {
		BusinessException e = assertThrows(BusinessException.class, action);
		assertEquals(expected, e.getErrorCode());
	}

	// ---- 저장되는 경우 ----

	@Test
	void 공통_경비를_저장한다() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "  저녁 식사  ", CalcType.PER_PERSON,
				new BigDecimal("30000"), false, "KRW", "  3박, 조식 포함 ", null);

		BudgetItemResponse r = add(req);

		assertEquals(14L, r.itemId());
		assertEquals("저녁 식사", r.name()); // 앞뒤 공백 제거
		assertEquals("3박, 조식 포함", r.memo());
		assertEquals(CalcType.PER_PERSON, r.calcType());
		assertEquals(AmountSource.MANUAL, r.amountSource());
		assertEquals(ME, r.createdBy());
	}

	@Test
	void 개인_경비는_계산_방식_없이_내_것으로_저장한다() {
		BudgetItemResponse r = add(personal("택시", "1500", "JPY"));

		assertEquals(Scope.PERSONAL, r.scope());
		assertNull(r.calcType());
		assertEquals(ME, r.createdBy()); // 입력한 사람의 개인 경비로만 들어감 (BUD-01-02 완료 기준)
	}

	@Test
	void 가격_확인_전이면_금액을_비워_저장한다_0원이_아님() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.SIGHTSEEING, "입장권", CalcType.TOTAL,
				null, true, "JPY", null, null);

		assertNull(add(req).amount());
	}

	@Test
	void 항목명_50자는_이모지도_1자로_센다() {
		String name = "가".repeat(49) + "😀"; // 코드 포인트 50자 (UTF-16으로는 51)

		assertEquals(name, add(personal(name, "100", "KRW")).name());
	}

	@Test
	void 원화_기준_딱_1억까지는_된다() {
		add(personal("호텔", "100000000", "KRW"));
		add(personal("호텔", "10989010", "JPY")); // x 9.1 = 99,999,991원
		add(personal("호텔", "12.34", "USD"));
	}

	// ---- 기능정의서 오류 문구 (ErrorCode D 구역) ----

	@Test
	void 항목명이_비면_항목명을_입력해_주세요() {
		assertError(ErrorCode.BUDGET_NAME_REQUIRED, () -> add(personal("   ", "100", "KRW")));
		assertEquals("항목명을 입력해 주세요.", ErrorCode.BUDGET_NAME_REQUIRED.getMessage());
	}

	@Test
	void 금액도_가격_확인_전도_없으면_금액을_입력하거나() {
		assertError(ErrorCode.BUDGET_AMOUNT_REQUIRED, () -> add(personal("택시", null, "KRW")));
	}

	@Test
	void 음수는_0_이상으로_입력해_주세요() {
		assertError(ErrorCode.BUDGET_AMOUNT_NEGATIVE, () -> add(personal("택시", "-1", "KRW")));
	}

	@Test
	void 공통_경비가_200개면_추가할_수_없다() {
		when(repository.countByTripIdAndScope(TRIP, Scope.COMMON)).thenReturn(200L);

		assertError(ErrorCode.BUDGET_COMMON_LIMIT, () -> add(common("점심", "1000", "JPY")));
		verify(repository, never()).save(any());
	}

	@Test
	void 내_개인_경비가_200개면_추가할_수_없다() {
		when(repository.countByTripIdAndScopeAndCreatedBy(TRIP, Scope.PERSONAL, ME)).thenReturn(200L);

		assertError(ErrorCode.BUDGET_PERSONAL_LIMIT, () -> add(personal("택시", "1000", "JPY")));
	}

	@Test
	void 조회_참여자는_공통_경비를_추가할_수_없다() {
		asViewer();
		assertError(ErrorCode.BUDGET_COMMON_EDIT_FORBIDDEN,
				() -> service.addBudgetItem(TRIP, common("점심", "1000", "JPY")));
	}

	@Test
	void 조회_참여자는_개인_경비도_추가할_수_없다_공통_문구() {
		asViewer();
		assertError(ErrorCode.EDIT_PERMISSION_CHANGED,
				() -> service.addBudgetItem(TRIP, personal("택시", "1000", "JPY")));
	}

	// ---- 화면이 먼저 막는 값 (서버도 다시 검사, 공통 INVALID_INPUT) ----

	@Test
	void 항목명_51자는_거절한다() {
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("가".repeat(51), "100", "KRW")));
	}

	@Test
	void 항목명_메모에_제어문자가_있으면_거절한다() {
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("점심\t라멘", "100", "KRW")));
		BudgetItemRequest memo = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", "줄\n바꿈", null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(memo));
	}

	@Test
	void 메모_201자는_거절한다() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", "가".repeat(201), null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(req));
	}

	@Test
	void 개인_경비에_메모나_계산_방식이_오면_거절한다() {
		BudgetItemRequest withMemo = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", null,
				new BigDecimal("100"), false, "KRW", "메모", null);
		BudgetItemRequest withCalc = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", null, null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(withMemo));
		assertError(ErrorCode.INVALID_INPUT, () -> add(withCalc));
	}

	@Test
	void 공통_경비에_계산_방식이_없거나_분류가_없으면_거절한다() {
		BudgetItemRequest noCalc = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", null,
				new BigDecimal("100"), false, "KRW", null, null);
		BudgetItemRequest noCategory = new BudgetItemRequest(Scope.COMMON, null, "점심", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", null, null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(noCalc));
		assertError(ErrorCode.INVALID_INPUT, () -> add(noCategory));
	}

	@Test
	void 통화_자릿수를_넘으면_거절한다() {
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("라멘", "100.5", "JPY"))); // 엔은 정수
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("커피", "12.345", "USD"))); // 달러는 소수 2자리
	}

	@Test
	void 원화_기준_1억을_넘으면_거절한다() {
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("호텔", "100000001", "KRW")));
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("호텔", "10989011", "JPY"))); // x 9.1 = 100,000,000.1원
	}

	@Test
	void 환율이_없는_통화나_잘못된_통화_코드는_거절한다() {
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("마사지", "500", "THB"))); // 오늘 환율 없음
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("라멘", "500", "jpy")));
		assertError(ErrorCode.INVALID_INPUT, () -> add(personal("라멘", "500", null)));
	}

	@Test
	void 금액과_가격_확인_전을_함께_보내면_거절한다() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.SIGHTSEEING, "입장권", CalcType.TOTAL,
				new BigDecimal("1000"), true, "JPY", null, null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(req));
	}

	@Test
	void 검사에_걸리면_저장하지_않는다() {
		assertThrows(BusinessException.class, () -> add(personal("", "100", "KRW")));
		verify(repository, never()).save(any());
		verify(repository, never()).countByTripIdAndScopeAndCreatedBy(anyLong(), eq(Scope.PERSONAL), anyLong());
	}

	@Test
	void 저장되는_값을_확인한다() {
		add(common("점심", "2000", "JPY"));

		ArgumentCaptor<BudgetItem> captor = ArgumentCaptor.forClass(BudgetItem.class);
		verify(repository).save(captor.capture());
		BudgetItem saved = captor.getValue();
		assertEquals(TRIP, saved.getTripId());
		assertEquals("JPY", saved.getCurrency());
		assertEquals(0, new BigDecimal("2000").compareTo(saved.getAmount()));
	}

	// ==================== BUD-01-04 수정·삭제 ====================

	private static final Long OTHER = 2L; // 다른 참여자

	// DB에 이미 있는 항목 (가짜)
	private BudgetItem existing(long itemId, Scope scope, Long tripId, Long owner, AmountSource source) {
		BudgetItem item = BudgetItem.create(tripId, scope, Category.FOOD, "저녁 식사",
				scope == Scope.COMMON ? CalcType.PER_PERSON : null, new BigDecimal("30000"), "KRW", source, null, owner);
		ReflectionTestUtils.setField(item, "itemId", itemId);
		when(repository.findById(itemId)).thenReturn(Optional.of(item));
		return item;
	}

	@Test
	void 공통_경비를_수정하면_고친_값으로_바뀐다() {
		existing(3L, Scope.COMMON, TRIP, OTHER, AmountSource.MANUAL); // 다른 사람이 만든 공통 경비도 편집 참여자면 수정 가능
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.LODGING, " 숙소 ", CalcType.TOTAL,
				new BigDecimal("40000"), false, "JPY", "3박", null);

		BudgetItemResponse r = service.updateBudgetItem(TRIP, 3L, req);

		assertEquals("숙소", r.name());
		assertEquals(Category.LODGING, r.category());
		assertEquals(CalcType.TOTAL, r.calcType());
		assertEquals("JPY", r.currency());
		assertEquals("3박", r.memo());
		assertEquals(OTHER, r.createdBy()); // 만든 사람은 그대로
		verify(repository, never()).countByTripIdAndScope(anyLong(), any()); // 수정은 개수 제한을 보지 않음
	}

	@Test
	void 내_개인_경비를_가격_확인_전으로_고칠_수_있다() {
		existing(5L, Scope.PERSONAL, TRIP, ME, AmountSource.MANUAL);
		BudgetItemRequest req = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", null, null, true, "JPY", null, null);

		assertNull(service.updateBudgetItem(TRIP, 5L, req).amount());
	}

	@Test
	void 교통비로_들어온_항목도_고칠_수_있고_입력_방식은_그대로다() {
		existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE);
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.TRANSPORT, "1일차 교통비", CalcType.PER_PERSON,
				new BigDecimal("500"), false, "JPY", null, null);

		assertEquals(AmountSource.ROUTE_FARE, service.updateBudgetItem(TRIP, 1L, req).amountSource());
	}

	@Test
	void 수정도_추가와_같은_입력_규칙으로_검사한다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_NAME_REQUIRED,
				() -> service.updateBudgetItem(TRIP, 3L, common(" ", "100", "KRW")));
		assertError(ErrorCode.BUDGET_AMOUNT_NEGATIVE,
				() -> service.updateBudgetItem(TRIP, 3L, common("점심", "-5", "KRW")));
		assertError(ErrorCode.INVALID_INPUT,
				() -> service.updateBudgetItem(TRIP, 3L, common("점심", "10989011", "JPY"))); // 1억 초과
	}

	@Test
	void 공통_개인_구분은_수정으로_바꿀_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetItem(TRIP, 3L, personal("점심", "100", "KRW")));
	}

	@Test
	void 없거나_다른_여행의_항목이면_이미_삭제된_경비예요() {
		when(repository.findById(99L)).thenReturn(Optional.empty());
		existing(7L, Scope.COMMON, 2L, ME, AmountSource.MANUAL); // 다른 여행(2)의 항목

		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.updateBudgetItem(TRIP, 99L, common("점심", "1", "KRW")));
		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.deleteBudgetItem(TRIP, 99L));
		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.deleteBudgetItem(TRIP, 7L));
		assertEquals("이미 삭제된 경비예요.", ErrorCode.BUDGET_ITEM_NOT_FOUND.getMessage());
	}

	@Test
	void 남의_개인_경비는_소유자도_고치거나_지울_수_없다() {
		existing(7L, Scope.PERSONAL, TRIP, OTHER, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN,
				() -> service.updateBudgetItem(TRIP, 7L, personal("유심", "100", "KRW")));
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 7L));
		assertEquals("이 경비를 고칠 권한이 없어요.", ErrorCode.BUDGET_EDIT_FORBIDDEN.getMessage());
	}

	@Test
	void 조회_참여자는_공통도_내_개인도_고치거나_지울_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);
		existing(5L, Scope.PERSONAL, TRIP, ME, AmountSource.MANUAL);

		asViewer();
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 3L));
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 5L));
		verify(repository, never()).delete(any(BudgetItem.class));
	}

	@Test
	void 삭제하면_DB에서_지운다() {
		BudgetItem item = existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE); // 교통비 항목도 삭제 가능

		service.deleteBudgetItem(TRIP, 1L);

		verify(repository).delete(item); // 구간 연결 기록은 DB가 함께 지움(ON DELETE CASCADE)
	}

	@Test
	void 권한이_없으면_지우지_않는다() {
		existing(7L, Scope.PERSONAL, TRIP, OTHER, AmountSource.MANUAL);

		assertThrows(BusinessException.class, () -> service.deleteBudgetItem(TRIP, 7L));
		verify(repository, never()).delete(any(BudgetItem.class));
	}

	// ==================== BUD-02-03 펼친 칸 메모 ====================

	@Test
	void 메모를_쓰면_앞뒤_공백을_지우고_저장한다() {
		existing(3L, Scope.COMMON, TRIP, OTHER, AmountSource.MANUAL); // 공통 경비는 편집 참여자면 누구 것이든

		BudgetItemResponse r = service.updateBudgetMemo(TRIP, 3L, "  3박, 조식 포함 ");

		assertEquals("3박, 조식 포함", r.memo()); // 메모가 생기면 화면 아이콘이 채워짐
	}

	@Test
	void 메모를_비우고_저장하면_삭제된다() {
		BudgetItem item = existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);
		item.updateMemo("예전 메모");

		assertNull(service.updateBudgetMemo(TRIP, 3L, "   ").memo()); // 빈 아이콘으로
		assertNull(service.updateBudgetMemo(TRIP, 3L, null).memo());
	}

	@Test
	void 메모_200자는_되고_201자와_제어문자는_거절한다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertEquals(200, BudgetService.countChars(service.updateBudgetMemo(TRIP, 3L, "가".repeat(199) + "😀").memo()));
		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 3L, "가".repeat(201)));
		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 3L, "줄\n바꿈"));
	}

	@Test
	void 조회_참여자는_메모를_쓸_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		asViewer();
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.updateBudgetMemo(TRIP, 3L, "메모"));
	}

	@Test
	void 개인_경비에는_메모를_쓸_수_없다() {
		existing(5L, Scope.PERSONAL, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 5L, "메모"));
	}

	@Test
	void 메모도_없는_항목이면_이미_삭제된_경비예요() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.updateBudgetMemo(TRIP, 99L, "메모"));
	}

	@Test
	void 교통비_항목도_메모는_쓸_수_있고_근거_칸을_함께_준다() {
		BudgetItem item = existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE);
		ReflectionTestUtils.setField(item, "calcNote", "¥400 x 4명");

		BudgetItemResponse r = service.updateBudgetMemo(TRIP, 1L, "지하철");

		assertEquals(AmountSource.ROUTE_FARE, r.amountSource());
		assertEquals("¥400 x 4명", r.calcNote());
		assertNull(r.estimateBasis());
	}

	// ==================== BUD-02-01 예산 기준 계산 ====================

	// 공통 경비 + 예산 기준 (금액 칸은 비우고 보냄, 서버가 계산)
	private static BudgetItemRequest rule(String base, String currency, boolean people, Integer days, Integer times) {
		return new BudgetItemRequest(Scope.COMMON, Category.FOOD, "저녁 식사", CalcType.TOTAL, null, false, currency, null,
				new BudgetItemRequest.BudgetRule(base == null ? null : new BigDecimal(base), people, days, times));
	}

	@Test
	void 기준_금액_x_인원_x_일수로_계산해_예산_기준_항목을_만든다() {
		BudgetItemResponse r = add(rule("30000", "KRW", true, 3, null));

		// 기능정의서 예시 "30,000원 x 4명 x 3일 = 360,000원"
		assertEquals(0, new BigDecimal("360000").compareTo(r.amount()));
		assertEquals(CalcType.TOTAL, r.calcType()); // 계산 방식 '전체 금액'
		assertEquals(AmountSource.BUDGET_RULE, r.amountSource()); // 근거 '예산 기준'
		assertEquals("30,000원 × 4명 × 3일 = 360,000원", r.calcNote());
		assertEquals("{\"baseAmount\":30000,\"currency\":\"KRW\",\"people\":4,\"days\":3,\"times\":null}",
				r.estimateBasis()); // 나중에 인원이 바뀌면 다시 계산할 수 있는 기준값
	}

	@Test
	void 횟수만_곱하거나_아무것도_안_곱할_수_있다() {
		assertEquals("¥1,200 × 3회 = ¥3,600", add(rule("1200", "JPY", false, null, 3)).calcNote());
		// 정한 내용(11/26): 곱할 값을 하나도 고르지 않으면 기준 금액 그대로
		assertEquals(0, new BigDecimal("50000").compareTo(add(rule("50000", "KRW", false, null, null)).amount()));
	}

	@Test
	void 달러는_소수_둘째_자리로_적는다() {
		assertEquals("$12.50 × 4명 = $50.00", add(rule("12.5", "USD", true, null, null)).calcNote());
	}

	@Test
	void 기준_금액이_없으면_기준_금액을_입력해_주세요() {
		assertError(ErrorCode.BUDGET_BASE_AMOUNT_REQUIRED, () -> add(rule(null, "KRW", true, 3, null)));
		assertEquals("기준 금액을 입력해 주세요.", ErrorCode.BUDGET_BASE_AMOUNT_REQUIRED.getMessage());
	}

	@Test
	void 일수는_1에서_7_횟수는_1에서_99() {
		add(rule("1000", "KRW", false, 7, 99));
		assertError(ErrorCode.INVALID_INPUT, () -> add(rule("1000", "KRW", false, 8, null)));
		assertError(ErrorCode.INVALID_INPUT, () -> add(rule("1000", "KRW", false, 0, null)));
		assertError(ErrorCode.INVALID_INPUT, () -> add(rule("1000", "KRW", false, null, 100)));
	}

	@Test
	void 계산_결과도_금액_규칙을_지킨다() {
		assertError(ErrorCode.BUDGET_AMOUNT_NEGATIVE, () -> add(rule("-1", "KRW", true, null, null)));
		// 30,000,000원 x 4명 = 1억 2천만원 → 원화 기준 1억 초과
		assertError(ErrorCode.INVALID_INPUT, () -> add(rule("30000000", "KRW", true, null, null)));
		assertError(ErrorCode.INVALID_INPUT, () -> add(rule("100.5", "JPY", false, null, null))); // 엔은 정수
	}

	@Test
	void 예산_기준은_공통_경비_전체_금액으로만_금액_칸은_비워서() {
		BudgetItemRequest personalRule = new BudgetItemRequest(Scope.PERSONAL, Category.FOOD, "점심", null, null, false,
				"KRW", null, new BudgetItemRequest.BudgetRule(new BigDecimal("1000"), false, null, null));
		BudgetItemRequest perPerson = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", CalcType.PER_PERSON, null,
				false, "KRW", null, new BudgetItemRequest.BudgetRule(new BigDecimal("1000"), true, null, null));
		BudgetItemRequest withAmount = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", CalcType.TOTAL,
				new BigDecimal("5000"), false, "KRW", null, new BudgetItemRequest.BudgetRule(new BigDecimal("1000"), true, null, null));
		assertError(ErrorCode.INVALID_INPUT, () -> add(personalRule));
		assertError(ErrorCode.INVALID_INPUT, () -> add(perPerson));
		assertError(ErrorCode.INVALID_INPUT, () -> add(withAmount)); // 금액은 서버가 계산 (화면 값을 믿지 않음)
	}

	@Test
	void 수정에서는_예산_기준_계산을_쓰지_않는다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetItem(TRIP, 3L, rule("1000", "KRW", true, null, null)));
	}

	// ---- ACC-07 공통 접근 제어 (A의 체커) ----

	@Test
	void 목록은_참여자_검사를_통과해야_보인다() {
		when(repository.findByTripIdOrderByItemIdAsc(TRIP)).thenReturn(List.of());

		service.getBudgetList(TRIP);

		verify(tripAccess).requireParticipant(TRIP, ME);
	}

	@Test
	void 참여자가_아니거나_휴지통_여행이면_목록도_추가도_막는다() {
		doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE)).when(tripAccess).requireParticipant(TRIP, ME);
		doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE)).when(tripAccess)
				.requireEditor(eq(TRIP), eq(ME), any(ErrorCode.class));

		assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.getBudgetList(TRIP));
		assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> add(common("점심", "1000", "JPY")));
		verify(repository, never()).save(any(BudgetItem.class));
	}

	@Test
	void 수정은_항목을_찾기_전에_역할을_다시_확인한다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);
		asViewer();

		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN,
				() -> service.updateBudgetItem(TRIP, 3L, common("점심", "100", "KRW")));
		verify(repository, never()).findById(anyLong());
	}

	// ---- B의 여행 기본 정보 (findBasicInfo) ----

	@Test
	void 목록에_여행_통화_인원_일수_내_역할을_함께_준다() {
		when(tripAccess.requireParticipant(TRIP, ME)).thenReturn(TripMember.Role.EDITOR);
		when(repository.findByTripIdOrderByItemIdAsc(TRIP)).thenReturn(List.of());

		BudgetListResponse r = service.getBudgetList(TRIP);

		assertEquals(TRIP, r.tripId());
		assertEquals("JPY", r.tripCurrency()); // JP → JPY
		assertEquals(4, r.memberCount());
		assertEquals(4, r.tripDays()); // 11/20~11/23
		assertEquals(ME, r.myMemberId());
		assertEquals(TripMember.Role.EDITOR, r.myRole());
		assertEquals(List.of(), r.participants()); // TODO(A) 닉네임
	}

	@Test
	void 당일치기는_1일() {
		assertEquals(1, BudgetService.tripDays(new TripBasicInfo(2, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1), "KR")));
	}

	@Test
	void 예산_기준_계산의_여행_인원은_B의_여행_정보에서_읽는다() {
		when(tripService.findBasicInfo(TRIP)).thenReturn(Optional.of(
				new TripBasicInfo(3, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), "JP")));

		BudgetItemResponse r = add(rule("1000", "JPY", true, null, null));

		assertEquals(0, new BigDecimal("3000").compareTo(r.amount())); // 1000 x 3명
	}

	@Test
	void 그사이_여행이_지워졌으면_볼_수_없는_내용() {
		when(tripService.findBasicInfo(TRIP)).thenReturn(Optional.empty());

		assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.getBudgetList(TRIP));
	}
}
