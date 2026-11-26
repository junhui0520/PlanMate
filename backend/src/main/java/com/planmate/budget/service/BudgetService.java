package com.planmate.budget.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.budget.dto.BudgetItemRequest;
import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.BudgetItem;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.repository.BudgetItemRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 경비 항목 관리 (BUD-01).
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

	static final int NAME_MAX = 50; // BUD-01-02 항목명 1~50자
	static final int MEMO_MAX = 200; // BUD-01-02 메모 0~200자
	static final int ITEM_LIMIT = 200; // BUD-01-02 공통 경비 여행당·개인 경비 회원마다 최대 200개
	static final BigDecimal MAX_AMOUNT_KRW = new BigDecimal("100000000"); // BUD-01-02 금액 최대 1억(원화 기준)
	private static final String KRW = "KRW";

	private final BudgetItemRepository budgetItemRepository;
	private final ExchangeRateService exchangeRateService;

	/**
	 * BUD-01-01 여행 경비 목록 (공통·개인 모두, 추가한 순서).
	 * 개인 경비도 모든 참여자가 볼 수 있다.
	 */
	@Transactional(readOnly = true)
	public List<BudgetItemResponse> getBudgetItems(Long tripId) {
		// TODO(A에 요청): TripAccessChecker로 검사 (pjh/security 머지 후)
		//   - 로그인 상태, 요청 시점에 이 여행의 참여자(소유자·편집·조회), 여행이 휴지통(TRASHED)이 아님
		//   - 위반이면 공통 ErrorCode(LOGIN_REQUIRED, RESOURCE_NOT_ACCESSIBLE 등)로 BusinessException
		return budgetItemRepository.findByTripIdOrderByItemIdAsc(tripId).stream()
				.map(BudgetItemResponse::from)
				.toList();
	}

	/**
	 * BUD-01-02 경비 항목 추가. 화면에서 막는 값도 서버에서 다시 검사한다(AGENTS §6).
	 * TODO(A에 요청): memberId는 로그인 정보, canEdit(소유자·편집 참여자면 true, 조회 참여자면 false)은
	 *                 TripAccessChecker로 저장 직전에 다시 확인하고, 휴지통 여행은 막는다 (pjh/security 머지 후).
	 *                 추가 주소(POST /api/trips/{tripId}/budget-items)는 그때 연다(11/26 결정).
	 * TODO(B에 요청): 저장 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public BudgetItemResponse addBudgetItem(Long tripId, Long memberId, boolean canEdit, BudgetItemRequest req) {
		if (req.scope() == null || req.category() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		boolean common = req.scope() == Scope.COMMON;

		// 권한: 공통은 소유자·편집 참여자, 개인도 소유자·편집 참여자(본인 것만, 조회 참여자 불가)
		if (!canEdit) {
			// 정한 내용(11/26): 개인 경비 문구는 기능정의서에 없어 공통 EDIT_PERMISSION_CHANGED 사용
			throw new BusinessException(common ? ErrorCode.BUDGET_COMMON_EDIT_FORBIDDEN : ErrorCode.EDIT_PERMISSION_CHANGED);
		}

		String name = checkName(req.name());
		String memo = checkMemo(common, req.memo());
		checkCalcType(common, req.calcType());
		String currency = checkCurrency(req.currency());
		BigDecimal amount = checkAmount(req.amount(), req.priceUnknown(), currency);
		checkLimit(tripId, memberId, req.scope());

		BudgetItem saved = budgetItemRepository.save(BudgetItem.create(tripId, req.scope(), req.category(), name,
				common ? req.calcType() : null, amount, currency, BudgetItem.AmountSource.MANUAL, memo, memberId));
		return BudgetItemResponse.from(saved);
	}

	/**
	 * BUD-01-04 경비 항목 수정. 입력 규칙은 추가(BUD-01-02)와 같고, 개수 제한은 보지 않는다.
	 * 동시 수정은 나중에 저장한 값으로 덮어쓴다(COL-06-02).
	 * TODO(A에 요청): memberId·canEdit은 로그인 정보·TripAccessChecker (저장 직전 역할 재확인, 휴지통 여행 막기).
	 *                 수정 주소(PUT /api/trips/{tripId}/budget-items/{itemId})는 그때 연다.
	 * TODO(B에 요청): 저장 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public BudgetItemResponse updateBudgetItem(Long tripId, Long itemId, Long memberId, boolean canEdit,
			BudgetItemRequest req) {
		BudgetItem item = findEditableItem(tripId, itemId, memberId, canEdit);
		// 구분(공통·개인)은 수정으로 바꿀 수 없음
		if (req.scope() != item.getScope() || req.category() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		boolean common = item.getScope() == Scope.COMMON;

		String name = checkName(req.name());
		String memo = checkMemo(common, req.memo());
		checkCalcType(common, req.calcType());
		String currency = checkCurrency(req.currency());
		BigDecimal amount = checkAmount(req.amount(), req.priceUnknown(), currency);

		item.update(req.category(), name, common ? req.calcType() : null, amount, currency, memo);
		return BudgetItemResponse.from(item);
	}

	/**
	 * BUD-01-04 경비 항목 삭제. 교통비로 반영된 항목이면 구간 연결 기록(FARE_EXPENSE_LINK)도
	 * DB의 ON DELETE CASCADE로 함께 삭제된다(RTE-03-02).
	 * TODO(A에 요청): memberId·canEdit은 로그인 정보·TripAccessChecker. 삭제 주소(DELETE ...)는 그때 연다.
	 * TODO(B에 요청): 삭제 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public void deleteBudgetItem(Long tripId, Long itemId, Long memberId, boolean canEdit) {
		budgetItemRepository.delete(findEditableItem(tripId, itemId, memberId, canEdit));
	}

	// BUD-01-04 없거나 다른 여행의 항목이면 "이미 삭제된 경비예요.",
	// 공통은 소유자·편집 참여자만, 개인은 주인 본인만(주인이 소유자·편집 참여자일 때) — 아니면 "이 경비를 고칠 권한이 없어요."
	private BudgetItem findEditableItem(Long tripId, Long itemId, Long memberId, boolean canEdit) {
		BudgetItem item = budgetItemRepository.findById(itemId)
				.filter(found -> found.getTripId().equals(tripId))
				.orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ITEM_NOT_FOUND));
		boolean ownPersonal = item.getScope() == Scope.PERSONAL && memberId.equals(item.getCreatedBy());
		if (!canEdit || (item.getScope() == Scope.PERSONAL && !ownPersonal)) {
			throw new BusinessException(ErrorCode.BUDGET_EDIT_FORBIDDEN); // 소유자도 남의 개인 경비는 불가
		}
		return item;
	}

	// 항목명: 필수, 앞뒤 공백 제거 후 1~50자(코드 포인트, 화면 countChars와 같음), 제어문자 불가
	private String checkName(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.BUDGET_NAME_REQUIRED);
		}
		String name = raw.strip();
		if (hasControlChars(name) || countChars(name) > NAME_MAX) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return name;
	}

	// 메모: 공통 경비만 0~200자, 제어문자 불가. 개인 경비에 메모가 오면 거절(화면을 거치지 않은 요청)
	private String checkMemo(boolean common, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		if (!common) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String memo = raw.strip();
		if (hasControlChars(memo) || countChars(memo) > MEMO_MAX) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return memo;
	}

	// 계산 방식: 공통이면 꼭 채움, 개인이면 비움 (CK_BUDGET_ITEM_02)
	private void checkCalcType(boolean common, BudgetItem.CalcType calcType) {
		if (common != (calcType != null)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	// 통화: 원화 또는 오늘 환율이 있는 통화(환율 API가 지원하는 통화)만
	private String checkCurrency(String raw) {
		if (raw == null || !raw.matches("[A-Z]{3}")) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (!KRW.equals(raw) && !todayKrwPerOne().containsKey(raw)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return raw;
	}

	// 금액: 입력하거나 '가격 확인 전'(둘 중 하나만), 0 이상, 통화 표준 자릿수까지, 원화 기준 1억 이하
	private BigDecimal checkAmount(BigDecimal amount, boolean priceUnknown, String currency) {
		if (priceUnknown) {
			if (amount != null) {
				throw new BusinessException(ErrorCode.INVALID_INPUT); // 금액과 '가격 확인 전'을 함께 보냄
			}
			return null; // 0원으로 저장하지 않음
		}
		if (amount == null) {
			throw new BusinessException(ErrorCode.BUDGET_AMOUNT_REQUIRED);
		}
		if (amount.signum() < 0) {
			throw new BusinessException(ErrorCode.BUDGET_AMOUNT_NEGATIVE);
		}
		if (Math.max(0, amount.stripTrailingZeros().scale()) > BudgetSummaryService.currencyDigits(currency)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		BigDecimal perOne = KRW.equals(currency) ? BigDecimal.ONE : todayKrwPerOne().get(currency);
		if (amount.multiply(perOne).compareTo(MAX_AMOUNT_KRW) > 0) {
			// TODO(D): 1억 초과 안내 문구가 정해지면 D 구역 ErrorCode로 바꾼다 (정한 내용 11/26)
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return amount;
	}

	// 개수: 공통 경비 여행당 200개, 개인 경비 회원마다 200개
	private void checkLimit(Long tripId, Long memberId, Scope scope) {
		if (scope == Scope.COMMON) {
			if (budgetItemRepository.countByTripIdAndScope(tripId, Scope.COMMON) >= ITEM_LIMIT) {
				throw new BusinessException(ErrorCode.BUDGET_COMMON_LIMIT);
			}
		} else if (budgetItemRepository.countByTripIdAndScopeAndCreatedBy(tripId, Scope.PERSONAL, memberId) >= ITEM_LIMIT) {
			throw new BusinessException(ErrorCode.BUDGET_PERSONAL_LIMIT);
		}
	}

	// BUD-04-01 오늘 환율(표)의 1단위당 원화
	private Map<String, BigDecimal> todayKrwPerOne() {
		return exchangeRateService.getTodayRates().stream()
				.collect(Collectors.toMap(ExchangeRateResponse::currency, ExchangeRateResponse::ratePerOne, (a, b) -> a));
	}

	// 글자 수는 코드 포인트(이모지 1자) — 화면 shared/utils/validation.ts countChars와 같음
	static int countChars(String value) {
		return value.codePointCount(0, value.length());
	}

	// 줄바꿈·탭 같은 제어문자 — 화면 validation.ts \p{Cc}와 같음
	static boolean hasControlChars(String value) {
		return value.codePoints().anyMatch(Character::isISOControl);
	}

}
