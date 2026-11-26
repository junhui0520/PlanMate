package com.planmate.budget.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.planmate.budget.entity.ExchangeRate;

/**
 * EXCHANGE_RATE (환율) 넣기·꺼내기.
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRate.Key> {

	// BUD-04-01 통화별 가장 최근 발표 환율 ('오늘 환율')
	@Query("""
			select e from ExchangeRate e
			where e.baseDate = (select max(e2.baseDate) from ExchangeRate e2 where e2.currency = e.currency)
			""")
	List<ExchangeRate> findLatestPerCurrency();

}
