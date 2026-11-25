package com.planmate.budget.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BUDGET_ITEM (경비) 테이블. BUD-01~03.
 * 여행·회원은 다른 파트 엔티티라 묶지 않고 ID 값으로만 들고 있는다.
 * 테이블은 schema.sql로만 만든다(엔티티에 맞춰 테이블을 바꾸지 않음).
 */
@Entity
@Table(name = "BUDGET_ITEM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetItem {

	/** 경비 구분 (공통 / 개인) */
	public enum Scope { COMMON, PERSONAL }

	/** 분류 (교통 / 숙박 / 식비 / 관광 / 기타) */
	public enum Category { TRANSPORT, LODGING, FOOD, SIGHTSEEING, ETC }

	/** 계산 방식, 공통 경비만 (전체 금액 / 1인당 금액 x 여행 인원) */
	public enum CalcType { TOTAL, PER_PERSON }

	/** 금액 입력 방식 (직접 입력 / 예산 기준 계산 BUD-02-01 / 교통비 반영 BUD-02-02) */
	public enum AmountSource { MANUAL, BUDGET_RULE, ROUTE_FARE }

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BUDGET_ITEM")
	@SequenceGenerator(name = "SEQ_BUDGET_ITEM", sequenceName = "SEQ_BUDGET_ITEM", allocationSize = 1)
	@Column(name = "ITEM_ID")
	private Long itemId;

	@Column(name = "TRIP_ID", nullable = false)
	private Long tripId;

	@Enumerated(EnumType.STRING)
	@Column(name = "SCOPE", nullable = false)
	private Scope scope;

	@Enumerated(EnumType.STRING)
	@Column(name = "CATEGORY", nullable = false)
	private Category category;

	@Column(name = "NAME", nullable = false)
	private String name;

	// 개인 경비면 비움, 공통 경비면 꼭 채움 (CK_BUDGET_ITEM_02)
	@Enumerated(EnumType.STRING)
	@Column(name = "CALC_TYPE")
	private CalcType calcType;

	// 가격 확인 전이면 비움 (0원으로 바꾸지 않음)
	@Column(name = "AMOUNT")
	private BigDecimal amount;

	@Column(name = "CURRENCY", nullable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "AMOUNT_SOURCE", nullable = false)
	private AmountSource amountSource;

	@Column(name = "CALC_NOTE")
	private String calcNote;

	@Column(name = "ESTIMATE_BASIS")
	private String estimateBasis;

	@Column(name = "MEMO")
	private String memo;

	// 개인 경비면 꼭 채움 (CK_BUDGET_ITEM_01). 회원은 ID 값만
	@Column(name = "CREATED_BY")
	private Long createdBy;

}
