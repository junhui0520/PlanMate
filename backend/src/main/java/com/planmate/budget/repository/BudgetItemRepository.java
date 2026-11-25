package com.planmate.budget.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.planmate.budget.entity.BudgetItem;

/**
 * BUDGET_ITEM (경비) 넣기·꺼내기.
 */
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

	// BUD-01-01 여행의 공통·개인 경비 전체, 추가한 순서 (IX_BUDGET_TRIP 사용)
	List<BudgetItem> findByTripIdOrderByItemIdAsc(Long tripId);

}
