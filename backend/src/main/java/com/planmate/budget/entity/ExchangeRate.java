package com.planmate.budget.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * EXCHANGE_RATE (환율) 테이블. BUD-04-01.
 * 통화 + 발표 날짜당 1행(복합 키, 시퀀스 없음). RATE는 단위(1 또는 100)당 원화. 예: JPY, 100, 843.41
 */
@Entity
@Table(name = "EXCHANGE_RATE")
@IdClass(ExchangeRate.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

	@Id
	@Column(name = "CURRENCY", nullable = false)
	private String currency;

	@Id
	@Column(name = "BASE_DATE", nullable = false)
	private LocalDate baseDate;

	@Column(name = "RATE", nullable = false)
	private BigDecimal rate;

	@Column(name = "UNIT", nullable = false)
	private Integer unit;

	public ExchangeRate(String currency, LocalDate baseDate, BigDecimal rate, int unit) {
		this.currency = currency;
		this.baseDate = baseDate;
		this.rate = rate;
		this.unit = unit;
	}

	/** 복합 키 (통화 + 발표 날짜) */
	@NoArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {

		private String currency;
		private LocalDate baseDate;

		public Key(String currency, LocalDate baseDate) {
			this.currency = currency;
			this.baseDate = baseDate;
		}

	}

}
