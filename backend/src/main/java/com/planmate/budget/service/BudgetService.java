package com.planmate.budget.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.budget.dto.BudgetItemRequest;
import com.planmate.budget.dto.BudgetListResponse;
import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.BudgetItem;
import com.planmate.budget.entity.BudgetItem.Scope;
import com.planmate.budget.repository.BudgetItemRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripAccessChecker;
import com.planmate.participant.entity.TripMember;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.service.TripService;

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
	private final AccountStatusChecker accountStatusChecker; // A: 로그인 회원
	private final TripAccessChecker tripAccessChecker; // A: 여행 참여자·역할·휴지통 검사
	private final TripService tripService; // B: 여행 인원·날짜·나라 코드 (findBasicInfo)

	/**
	 * BUD-01-01 여행 경비 목록 (공통·개인 모두, 추가한 순서) + 경비 탭이 쓰는 여행 정보.
	 * 개인 경비도 모든 참여자가 볼 수 있다.
	 * 권한: 로그인한 이 여행의 참여자(소유자·편집·조회), 휴지통 여행은 막음 (ACC-07)
	 * TODO(A에 요청): participants(참여자 닉네임)는 ParticipantService·MemberService가 생기면 채운다. 지금은 빈 목록.
	 */
	@Transactional(readOnly = true)
	public BudgetListResponse getBudgetList(Long tripId) {
		Long memberId = accountStatusChecker.requireLoginMemberId();
		TripMember.Role myRole = tripAccessChecker.requireParticipant(tripId, memberId);
		TripBasicInfo trip = tripInfo(tripId);
		List<BudgetItemResponse> items = budgetItemRepository.findByTripIdOrderByItemIdAsc(tripId).stream()
				.map(BudgetItemResponse::from)
				.toList();
		return new BudgetListResponse(tripId, CountryCurrency.of(trip.countryCode()), trip.peopleCount(),
				tripDays(trip), memberId, myRole, List.of(), items);
	}

	/**
	 * B의 여행 기본 정보 (인원·시작일·종료일·나라 코드). 참여자 검사를 통과한 뒤 부른다.
	 * 그사이 여행이 지워졌으면 참여자 검사와 같은 "볼 수 없는 내용"으로 막는다.
	 */
	TripBasicInfo tripInfo(Long tripId) {
		return tripService.findBasicInfo(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
	}

	// 여행 일수: 시작일~종료일 (당일치기 1일, 최대 7일)
	static int tripDays(TripBasicInfo trip) {
		return (int) ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1;
	}

	/**
	 * BUD-01-02 경비 항목 추가. 화면에서 막는 값도 서버에서 다시 검사한다(AGENTS §6).
	 * 권한: 공통·개인 모두 소유자·편집 참여자만(조회 참여자 불가), 저장 직전에 역할을 다시 확인한다(ACC-07).
	 * TODO(B에 요청): 저장 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 * 예산 기준 계산(BUD-02-01)의 여행 인원은 B의 여행 기본 정보에서 읽는다.
	 */
	@Transactional
	public BudgetItemResponse addBudgetItem(Long tripId, BudgetItemRequest req) {
		Long memberId = accountStatusChecker.requireLoginMemberId();
		if (req.scope() == null || req.category() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		boolean common = req.scope() == Scope.COMMON;

		// 권한: 공통은 소유자·편집 참여자, 개인도 소유자·편집 참여자(새 항목은 항상 본인 것, 조회 참여자 불가)
		// 정한 내용(11/26): 개인 경비 문구는 기능정의서에 없어 공통 EDIT_PERMISSION_CHANGED 사용
		tripAccessChecker.requireEditor(tripId, memberId,
				common ? ErrorCode.BUDGET_COMMON_EDIT_FORBIDDEN : ErrorCode.EDIT_PERMISSION_CHANGED);

		String name = checkName(req.name());
		String memo = checkMemo(common, req.memo());
		checkCalcType(common, req.calcType());
		String currency = checkCurrency(req.currency());

		// BUD-02-01 예산 기준 계산이면 금액은 서버가 정해진 산식으로만 계산한다 (여행 인원은 B의 여행 정보)
		RuleResult rule = req.budgetRule() == null ? null
				: applyBudgetRule(req, common, currency, tripInfo(tripId).peopleCount());
		BigDecimal amount = rule != null ? rule.total() : checkAmount(req.amount(), req.priceUnknown(), currency);
		checkLimit(tripId, memberId, req.scope());

		BudgetItem.AmountSource source = rule != null ? BudgetItem.AmountSource.BUDGET_RULE : BudgetItem.AmountSource.MANUAL;
		BudgetItem item = BudgetItem.create(tripId, req.scope(), req.category(), name,
				common ? req.calcType() : null, amount, currency, source, memo, memberId);
		if (rule != null) {
			item.recordBasis(rule.calcNote(), rule.estimateBasis());
		}
		return BudgetItemResponse.from(budgetItemRepository.save(item));
	}

	/** BUD-02-01 계산 결과: 금액, 사람이 읽는 문장(CALC_NOTE), 다시 계산할 수 있는 기준값(ESTIMATE_BASIS) */
	record RuleResult(BigDecimal total, String calcNote, String estimateBasis) {
	}

	static final int RULE_DAYS_MAX = 7; // BUD-02-01 일수 1~7
	static final int RULE_TIMES_MAX = 99; // BUD-02-01 횟수 1~99

	/**
	 * BUD-02-01 예산 기준 계산: 기준 금액 x 선택한 값들(여행 인원 / 일수 / 횟수).
	 * 공통 경비만, 계산 방식은 '전체 금액', 금액 칸은 비워서 보내야 한다(서버가 계산). 금액 규칙은 BUD-01-02와 같다.
	 * TODO(B에 요청): 여행 인원을 곱한 항목은 인원이 바뀌면 다시 계산 (ESTIMATE_BASIS의 people로 찾음, BUD-03-04)
	 */
	private RuleResult applyBudgetRule(BudgetItemRequest req, boolean common, String currency, int peopleCount) {
		BudgetItemRequest.BudgetRule rule = req.budgetRule();
		if (!common || req.calcType() != BudgetItem.CalcType.TOTAL || req.amount() != null || req.priceUnknown()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (rule.baseAmount() == null) {
			throw new BusinessException(ErrorCode.BUDGET_BASE_AMOUNT_REQUIRED);
		}
		if (rule.baseAmount().signum() < 0) {
			throw new BusinessException(ErrorCode.BUDGET_AMOUNT_NEGATIVE);
		}
		if (Math.max(0, rule.baseAmount().stripTrailingZeros().scale()) > BudgetSummaryService.currencyDigits(currency)
				|| (rule.days() != null && (rule.days() < 1 || rule.days() > RULE_DAYS_MAX))
				|| (rule.times() != null && (rule.times() < 1 || rule.times() > RULE_TIMES_MAX))
				|| (rule.multiplyPeople() && peopleCount < 1)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		BigDecimal total = rule.baseAmount();
		StringBuilder note = new StringBuilder(formatMoney(rule.baseAmount(), currency));
		if (rule.multiplyPeople()) {
			total = total.multiply(BigDecimal.valueOf(peopleCount));
			note.append(" × ").append(peopleCount).append("명");
		}
		if (rule.days() != null) {
			total = total.multiply(BigDecimal.valueOf(rule.days()));
			note.append(" × ").append(rule.days()).append("일");
		}
		if (rule.times() != null) {
			total = total.multiply(BigDecimal.valueOf(rule.times()));
			note.append(" × ").append(rule.times()).append("회");
		}
		checkAmount(total, false, currency); // 결과도 금액 규칙(1억 이하 등)을 지킨다
		note.append(" = ").append(formatMoney(total, currency)); // 예: "30,000원 × 4명 × 3일 = 360,000원"

		// 정한 내용(11/26): 기준값은 인원이 바뀌면 다시 계산할 수 있게 값을 정리한 형태로 저장
		String basis = "{\"baseAmount\":%s,\"currency\":\"%s\",\"people\":%s,\"days\":%s,\"times\":%s}".formatted(
				rule.baseAmount().stripTrailingZeros().toPlainString(), currency,
				rule.multiplyPeople() ? peopleCount : "null", rule.days(), rule.times());
		return new RuleResult(total, note.toString(), basis);
	}

	// 화면 shared/utils/money.ts formatMoney와 같은 모양: 30,000원 / ¥30,000 / $12.50 / €12.50
	static String formatMoney(BigDecimal amount, String currency) {
		int digits = BudgetSummaryService.currencyDigits(currency);
		DecimalFormat format = new DecimalFormat(digits == 0 ? "#,##0" : "#,##0." + "0".repeat(digits));
		String number = format.format(BudgetSummaryService.round(amount, currency));
		return switch (currency) {
			case KRW -> number + "원";
			case "JPY" -> "¥" + number;
			case "USD" -> "$" + number;
			case "EUR" -> "€" + number;
			default -> number + " " + currency;
		};
	}

	/**
	 * BUD-01-04 경비 항목 수정. 입력 규칙은 추가(BUD-01-02)와 같고, 개수 제한은 보지 않는다.
	 * 동시 수정은 나중에 저장한 값으로 덮어쓴다(COL-06-02).
	 * TODO(B에 요청): 저장 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public BudgetItemResponse updateBudgetItem(Long tripId, Long itemId, BudgetItemRequest req) {
		BudgetItem item = findEditableItem(tripId, itemId);
		// 구분(공통·개인)은 수정으로 바꿀 수 없음. 예산 기준 계산은 추가 창에서만(정한 내용 11/26)
		if (req.scope() != item.getScope() || req.category() == null || req.budgetRule() != null) {
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
	 * TODO(B에 요청): 삭제 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public void deleteBudgetItem(Long tripId, Long itemId) {
		budgetItemRepository.delete(findEditableItem(tripId, itemId));
	}

	/**
	 * BUD-02-03 펼친 칸에서 메모 작성·수정·삭제 (공통 경비만, 비우면 삭제).
	 * 규칙은 BUD-01-02 메모와 같다: 0~200자, 앞뒤 공백 제거, 제어문자 불가. 동시 수정은 나중 값으로 덮어씀.
	 * 정한 내용(11/26): 권한 없음은 BUD-01-04 문구 "이 경비를 고칠 권한이 없어요."를 같이 쓴다.
	 * TODO(B에 요청): 저장 후 다른 참여자 화면 반영은 SyncService (COL-06-01)
	 */
	@Transactional
	public BudgetItemResponse updateBudgetMemo(Long tripId, Long itemId, String memo) {
		BudgetItem item = findEditableItem(tripId, itemId);
		if (item.getScope() != Scope.COMMON) {
			throw new BusinessException(ErrorCode.INVALID_INPUT); // 개인 경비 항목에는 메모 아이콘 없음
		}
		item.updateMemo(checkMemo(true, memo));
		return BudgetItemResponse.from(item);
	}

	// BUD-01-04 고칠 수 있는 항목 찾기 (저장 직전에 역할을 다시 확인, ACC-07)
	// 1) 로그인 2) 이 여행의 소유자·편집 참여자(조회 참여자·휴지통 여행 막음) 3) 없거나 다른 여행의 항목이면 "이미 삭제된 경비예요."
	// 4) 개인 경비는 주인 본인만 — 소유자도 남의 개인 경비는 불가, "이 경비를 고칠 권한이 없어요."
	//    (정한 내용 11/26: requireSelf 대신 직접 비교해서 BUD-01-04 문구를 그대로 씀)
	private BudgetItem findEditableItem(Long tripId, Long itemId) {
		Long memberId = accountStatusChecker.requireLoginMemberId();
		tripAccessChecker.requireEditor(tripId, memberId, ErrorCode.BUDGET_EDIT_FORBIDDEN);
		BudgetItem item = budgetItemRepository.findById(itemId)
				.filter(found -> found.getTripId().equals(tripId))
				.orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ITEM_NOT_FOUND));
		if (item.getScope() == Scope.PERSONAL && !memberId.equals(item.getCreatedBy())) {
			throw new BusinessException(ErrorCode.BUDGET_EDIT_FORBIDDEN);
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
