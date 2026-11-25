package com.planmate.budget.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.budget.dto.BudgetItemResponse;
import com.planmate.budget.repository.BudgetItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 경비 항목 관리 (BUD-01).
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

	private final BudgetItemRepository budgetItemRepository;

	/**
	 * BUD-01-01 여행 경비 목록 (공통·개인 모두, 추가한 순서).
	 * 개인 경비도 모든 참여자가 볼 수 있다.
	 */
	@Transactional(readOnly = true)
	public List<BudgetItemResponse> getBudgetItems(Long tripId) {
		// TODO(A에 요청): TripAccessChecker로 검사
		//   - 로그인 상태, 요청 시점에 이 여행의 참여자(소유자·편집·조회), 여행이 휴지통(TRASHED)이 아님
		//   - 위반이면 BusinessException(ErrorCode.XXX) — TODO(공통): common/exception 이 생기면
		return budgetItemRepository.findByTripIdOrderByItemIdAsc(tripId).stream()
				.map(BudgetItemResponse::from)
				.toList();
	}

}
