package com.planmate.budget.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.budget.dto.BudgetSummaryResponse;
import com.planmate.budget.dto.BudgetSummaryResponse.BudgetTotal;
import com.planmate.budget.dto.BudgetSummaryResponse.CurrencyAmount;
import com.planmate.budget.dto.BudgetSummaryResponse.ExcludeReason;
import com.planmate.budget.dto.BudgetSummaryResponse.ExcludedItem;
import com.planmate.budget.dto.BudgetSummaryResponse.MyShareTotal;
import com.planmate.budget.dto.ExchangeRateResponse;
import com.planmate.budget.entity.BudgetItem;
import com.planmate.budget.repository.BudgetItemRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripAccessChecker;
import com.planmate.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * BUD-03-01·03·04, BUD-04-02 경비 합계 계산 (계산은 서버에서만).
 * - 공통 경비: 전체 금액 그대로 / 1인당 금액 x 여행 인원, 같은 통화끼리만 더함
 * - 내 1인 경비 = 공통 경비 합계 / 여행 인원 + 내 개인 경비 (통화별)
 * - 가격 확인 전은 빼고 계산(0으로 바꾸지 않음), 환율 없는 통화는 원화 합산에서만 뺌 → 부분 합계
 * - 반올림은 계산을 다 끝낸 뒤 마지막에 한 번: 통화별은 표준 자릿수(엔·원 0, 달러·유로 2), 원화 합산은 원 단위
 */
@Service
@RequiredArgsConstructor
public class BudgetSummaryService {

	private static final String KRW = "KRW";
	private static final MathContext CALC = MathContext.DECIMAL128; // 중간 계산은 반올림하지 않을 만큼 넉넉하게

	private final BudgetItemRepository budgetItemRepository;
	private final ExchangeRateService exchangeRateService;
	private final AccountStatusChecker accountStatusChecker; // A: 로그인 회원
	private final TripAccessChecker tripAccessChecker; // A: 여행 참여자·휴지통 검사
	private final TripService tripService; // B: 여행 인원 (findBasicInfo)

	/**
	 * 여행의 경비 합계.
	 * 여행 인원(1인당 금액 x 인원, 공통 합계 / 인원)은 B의 여행 기본 정보에서 읽는다.
	 * 권한: 로그인한 이 여행의 참여자(소유자·편집·조회), 휴지통 여행은 막음 (ACC-07). '내 1인 경비'는 로그인 회원 기준.
	 */
	@Transactional(readOnly = true)
	public BudgetSummaryResponse getSummary(Long tripId) {
		Long myMemberId = accountStatusChecker.requireLoginMemberId();
		tripAccessChecker.requireParticipant(tripId, myMemberId);
		int peopleCount = tripService.findBasicInfo(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE))
				.peopleCount();
		List<BudgetItem> items = budgetItemRepository.findByTripIdOrderByItemIdAsc(tripId);
		Map<String, BigDecimal> krwPerOne = new HashMap<>();
		for (ExchangeRateResponse rate : exchangeRateService.getTodayRates()) { // BUD-04-01 오늘 환율(표)
			krwPerOne.put(rate.currency(), rate.ratePerOne());
		}
		return calculate(tripId, items, peopleCount, myMemberId, krwPerOne);
	}

	/** 계산만 한다 (DB·환율 조회 없음). krwPerOne: 통화별 1단위당 원화 */
	public BudgetSummaryResponse calculate(Long tripId, List<BudgetItem> items, int peopleCount, Long myMemberId,
			Map<String, BigDecimal> krwPerOne) {
		BigDecimal people = BigDecimal.valueOf(peopleCount);
		List<BudgetItem> common = items.stream().filter(i -> i.getScope() == BudgetItem.Scope.COMMON).toList();
		List<BudgetItem> mine = items.stream()
				.filter(i -> i.getScope() == BudgetItem.Scope.PERSONAL && myMemberId.equals(i.getCreatedBy()))
				.toList();

		// BUD-03-01 공통 경비 합계 (통화별, 반올림 전)
		Map<String, BigDecimal> commonSums = new LinkedHashMap<>();
		for (BudgetItem i : common) {
			if (i.getAmount() == null) continue; // 가격 확인 전은 뺌
			BigDecimal amount = i.getCalcType() == BudgetItem.CalcType.PER_PERSON
					? i.getAmount().multiply(people)
					: i.getAmount();
			commonSums.merge(currencyOf(i), amount, BigDecimal::add);
		}

		// BUD-03-03 내 1인 경비 = 공통 경비 합계 / 여행 인원 + 내 개인 경비 (반올림 전)
		Map<String, BigDecimal> shareSums = new LinkedHashMap<>();
		commonSums.forEach((currency, sum) -> shareSums.put(currency, sum.divide(people, CALC)));
		Map<String, BigDecimal> personalSums = new LinkedHashMap<>();
		for (BudgetItem i : mine) {
			if (i.getAmount() == null) continue;
			personalSums.merge(currencyOf(i), i.getAmount(), BigDecimal::add);
		}
		Map<String, BigDecimal> mySums = new LinkedHashMap<>(shareSums);
		personalSums.forEach((currency, sum) -> mySums.merge(currency, sum, BigDecimal::add));

		List<BudgetItem> mineAndCommon = new ArrayList<>(common);
		mineAndCommon.addAll(mine);

		BudgetTotal commonTotal = toTotal(commonSums, common, krwPerOne);
		BudgetTotal myTotal = toTotal(mySums, mineAndCommon, krwPerOne);
		MyShareTotal myShare = new MyShareTotal(myTotal.krwTotal(), myTotal.byCurrency(), myTotal.excluded(),
				toAmounts(shareSums), toAmounts(personalSums));
		return new BudgetSummaryResponse(tripId, commonTotal, myShare);
	}

	// 통화별 합계 → 원화 합산 + 제외 목록 (BUD-03-04, BUD-04-02)
	private BudgetTotal toTotal(Map<String, BigDecimal> sums, List<BudgetItem> items, Map<String, BigDecimal> krwPerOne) {
		List<ExcludedItem> excluded = new ArrayList<>();
		items.stream()
				.filter(i -> i.getAmount() == null)
				.forEach(i -> excluded.add(new ExcludedItem(i.getItemId(), i.getName(), ExcludeReason.PRICE_UNKNOWN)));

		BigDecimal krw = BigDecimal.ZERO;
		for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
			BigDecimal rate = KRW.equals(e.getKey()) ? BigDecimal.ONE : krwPerOne.get(e.getKey());
			if (rate == null) { // 환율 없는 통화: 원화 합산에서만 빼고 그 항목들을 알림
				items.stream()
						.filter(i -> i.getAmount() != null && currencyOf(i).equals(e.getKey()))
						.forEach(i -> excluded.add(new ExcludedItem(i.getItemId(), i.getName(), ExcludeReason.NO_RATE)));
				continue;
			}
			krw = krw.add(e.getValue().multiply(rate));
		}
		long krwTotal = krw.setScale(0, RoundingMode.HALF_UP).longValueExact(); // 원 단위, 마지막에 한 번
		return new BudgetTotal(krwTotal, toAmounts(sums), excluded);
	}

	// 통화 표준 자릿수로 마지막에 한 번 반올림, 원화는 맨 뒤 (화면 "¥13,400 + 82,000원" 순서)
	private static List<CurrencyAmount> toAmounts(Map<String, BigDecimal> sums) {
		List<CurrencyAmount> result = new ArrayList<>();
		sums.forEach((currency, sum) -> {
			if (!KRW.equals(currency)) result.add(new CurrencyAmount(currency, round(sum, currency)));
		});
		if (sums.containsKey(KRW)) result.add(new CurrencyAmount(KRW, round(sums.get(KRW), KRW)));
		return result;
	}

	// BUD-04-02 통화 표준 자릿수 (엔·원 0자리, 달러·유로 2자리)
	static BigDecimal round(BigDecimal amount, String currency) {
		return amount.setScale(currencyDigits(currency), RoundingMode.HALF_UP);
	}

	// 통화 표준 자릿수: 엔·원 0, 달러·유로 2 (모르는 통화 코드는 2)
	static int currencyDigits(String currency) {
		try {
			return Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
		} catch (IllegalArgumentException e) {
			return 2;
		}
	}

	private static String currencyOf(BudgetItem item) {
		return item.getCurrency().trim(); // CHAR(3)
	}

}
