package com.planmate.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.BudgetItem;
import com.planmate.budget.entity.BudgetItem.AmountSource;
import com.planmate.budget.entity.BudgetItem.CalcType;
import com.planmate.budget.entity.BudgetItem.Category;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.repository.BudgetItemRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;

/**
 * BUD-01-02 경비 항목 추가 서버 검사 확인. DB와 환율은 가짜(Mock)로 둔다.
 * 환율: 100엔 = 910원(1엔 9.1원), 1달러 1343.6원
 */
class BudgetServiceTest {

	private static final Long TRIP = 1L;
	private static final Long ME = 1L;

	private BudgetItemRepository repository;
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
		service = new BudgetService(repository, rates);
	}

	// 공통 경비 기본 입력 (식비, 1인당 30,000원)
	private static BudgetItemRequest common(String name, String amount, String currency) {
		return new BudgetItemRequest(Scope.COMMON, Category.FOOD, name, CalcType.PER_PERSON,
				amount == null ? null : new BigDecimal(amount), false, currency, null);
	}

	private static BudgetItemRequest personal(String name, String amount, String currency) {
		return new BudgetItemRequest(Scope.PERSONAL, Category.TRANSPORT, name, null,
				amount == null ? null : new BigDecimal(amount), false, currency, null);
	}

	private BudgetItemResponse add(BudgetItemRequest req) {
		return service.addBudgetItem(TRIP, ME, true, req);
	}

	private static void assertError(ErrorCode expected, Executable action) {
		BusinessException e = assertThrows(BusinessException.class, action);
		assertEquals(expected, e.getErrorCode());
	}

	// ---- 저장되는 경우 ----

	@Test
	void 공통_경비를_저장한다() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "  저녁 식사  ", CalcType.PER_PERSON,
				new BigDecimal("30000"), false, "KRW", "  3박, 조식 포함 ");

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
				null, true, "JPY", null);

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
		assertError(ErrorCode.BUDGET_COMMON_EDIT_FORBIDDEN,
				() -> service.addBudgetItem(TRIP, ME, false, common("점심", "1000", "JPY")));
	}

	@Test
	void 조회_참여자는_개인_경비도_추가할_수_없다_공통_문구() {
		assertError(ErrorCode.EDIT_PERMISSION_CHANGED,
				() -> service.addBudgetItem(TRIP, ME, false, personal("택시", "1000", "JPY")));
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
				new BigDecimal("100"), false, "KRW", "줄\n바꿈");
		assertError(ErrorCode.INVALID_INPUT, () -> add(memo));
	}

	@Test
	void 메모_201자는_거절한다() {
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", "가".repeat(201));
		assertError(ErrorCode.INVALID_INPUT, () -> add(req));
	}

	@Test
	void 개인_경비에_메모나_계산_방식이_오면_거절한다() {
		BudgetItemRequest withMemo = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", null,
				new BigDecimal("100"), false, "KRW", "메모");
		BudgetItemRequest withCalc = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", null);
		assertError(ErrorCode.INVALID_INPUT, () -> add(withMemo));
		assertError(ErrorCode.INVALID_INPUT, () -> add(withCalc));
	}

	@Test
	void 공통_경비에_계산_방식이_없거나_분류가_없으면_거절한다() {
		BudgetItemRequest noCalc = new BudgetItemRequest(Scope.COMMON, Category.FOOD, "점심", null,
				new BigDecimal("100"), false, "KRW", null);
		BudgetItemRequest noCategory = new BudgetItemRequest(Scope.COMMON, null, "점심", CalcType.TOTAL,
				new BigDecimal("100"), false, "KRW", null);
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
				new BigDecimal("1000"), true, "JPY", null);
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
				new BigDecimal("40000"), false, "JPY", "3박");

		BudgetItemResponse r = service.updateBudgetItem(TRIP, 3L, ME, true, req);

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
		BudgetItemRequest req = new BudgetItemRequest(Scope.PERSONAL, Category.ETC, "기념품", null, null, true, "JPY", null);

		assertNull(service.updateBudgetItem(TRIP, 5L, ME, true, req).amount());
	}

	@Test
	void 교통비로_들어온_항목도_고칠_수_있고_입력_방식은_그대로다() {
		existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE);
		BudgetItemRequest req = new BudgetItemRequest(Scope.COMMON, Category.TRANSPORT, "1일차 교통비", CalcType.PER_PERSON,
				new BigDecimal("500"), false, "JPY", null);

		assertEquals(AmountSource.ROUTE_FARE, service.updateBudgetItem(TRIP, 1L, ME, true, req).amountSource());
	}

	@Test
	void 수정도_추가와_같은_입력_규칙으로_검사한다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_NAME_REQUIRED,
				() -> service.updateBudgetItem(TRIP, 3L, ME, true, common(" ", "100", "KRW")));
		assertError(ErrorCode.BUDGET_AMOUNT_NEGATIVE,
				() -> service.updateBudgetItem(TRIP, 3L, ME, true, common("점심", "-5", "KRW")));
		assertError(ErrorCode.INVALID_INPUT,
				() -> service.updateBudgetItem(TRIP, 3L, ME, true, common("점심", "10989011", "JPY"))); // 1억 초과
	}

	@Test
	void 공통_개인_구분은_수정으로_바꿀_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetItem(TRIP, 3L, ME, true, personal("점심", "100", "KRW")));
	}

	@Test
	void 없거나_다른_여행의_항목이면_이미_삭제된_경비예요() {
		when(repository.findById(99L)).thenReturn(Optional.empty());
		existing(7L, Scope.COMMON, 2L, ME, AmountSource.MANUAL); // 다른 여행(2)의 항목

		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.updateBudgetItem(TRIP, 99L, ME, true, common("점심", "1", "KRW")));
		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.deleteBudgetItem(TRIP, 99L, ME, true));
		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.deleteBudgetItem(TRIP, 7L, ME, true));
		assertEquals("이미 삭제된 경비예요.", ErrorCode.BUDGET_ITEM_NOT_FOUND.getMessage());
	}

	@Test
	void 남의_개인_경비는_소유자도_고치거나_지울_수_없다() {
		existing(7L, Scope.PERSONAL, TRIP, OTHER, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN,
				() -> service.updateBudgetItem(TRIP, 7L, ME, true, personal("유심", "100", "KRW")));
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 7L, ME, true));
		assertEquals("이 경비를 고칠 권한이 없어요.", ErrorCode.BUDGET_EDIT_FORBIDDEN.getMessage());
	}

	@Test
	void 조회_참여자는_공통도_내_개인도_고치거나_지울_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);
		existing(5L, Scope.PERSONAL, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 3L, ME, false));
		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.deleteBudgetItem(TRIP, 5L, ME, false));
	}

	@Test
	void 삭제하면_DB에서_지운다() {
		BudgetItem item = existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE); // 교통비 항목도 삭제 가능

		service.deleteBudgetItem(TRIP, 1L, ME, true);

		verify(repository).delete(item); // 구간 연결 기록은 DB가 함께 지움(ON DELETE CASCADE)
	}

	@Test
	void 권한이_없으면_지우지_않는다() {
		existing(7L, Scope.PERSONAL, TRIP, OTHER, AmountSource.MANUAL);

		assertThrows(BusinessException.class, () -> service.deleteBudgetItem(TRIP, 7L, ME, true));
		verify(repository, never()).delete(any(BudgetItem.class));
	}

	// ==================== BUD-02-03 펼친 칸 메모 ====================

	@Test
	void 메모를_쓰면_앞뒤_공백을_지우고_저장한다() {
		existing(3L, Scope.COMMON, TRIP, OTHER, AmountSource.MANUAL); // 공통 경비는 편집 참여자면 누구 것이든

		BudgetItemResponse r = service.updateBudgetMemo(TRIP, 3L, ME, true, "  3박, 조식 포함 ");

		assertEquals("3박, 조식 포함", r.memo()); // 메모가 생기면 화면 아이콘이 채워짐
	}

	@Test
	void 메모를_비우고_저장하면_삭제된다() {
		BudgetItem item = existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);
		item.updateMemo("예전 메모");

		assertNull(service.updateBudgetMemo(TRIP, 3L, ME, true, "   ").memo()); // 빈 아이콘으로
		assertNull(service.updateBudgetMemo(TRIP, 3L, ME, true, null).memo());
	}

	@Test
	void 메모_200자는_되고_201자와_제어문자는_거절한다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertEquals(200, BudgetService.countChars(service.updateBudgetMemo(TRIP, 3L, ME, true, "가".repeat(199) + "😀").memo()));
		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 3L, ME, true, "가".repeat(201)));
		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 3L, ME, true, "줄\n바꿈"));
	}

	@Test
	void 조회_참여자는_메모를_쓸_수_없다() {
		existing(3L, Scope.COMMON, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.BUDGET_EDIT_FORBIDDEN, () -> service.updateBudgetMemo(TRIP, 3L, ME, false, "메모"));
	}

	@Test
	void 개인_경비에는_메모를_쓸_수_없다() {
		existing(5L, Scope.PERSONAL, TRIP, ME, AmountSource.MANUAL);

		assertError(ErrorCode.INVALID_INPUT, () -> service.updateBudgetMemo(TRIP, 5L, ME, true, "메모"));
	}

	@Test
	void 메모도_없는_항목이면_이미_삭제된_경비예요() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertError(ErrorCode.BUDGET_ITEM_NOT_FOUND, () -> service.updateBudgetMemo(TRIP, 99L, ME, true, "메모"));
	}

	@Test
	void 교통비_항목도_메모는_쓸_수_있고_근거_칸을_함께_준다() {
		BudgetItem item = existing(1L, Scope.COMMON, TRIP, ME, AmountSource.ROUTE_FARE);
		ReflectionTestUtils.setField(item, "calcNote", "¥400 x 4명");

		BudgetItemResponse r = service.updateBudgetMemo(TRIP, 1L, ME, true, "지하철");

		assertEquals(AmountSource.ROUTE_FARE, r.amountSource());
		assertEquals("¥400 x 4명", r.calcNote());
		assertNull(r.estimateBasis());
	}

}
