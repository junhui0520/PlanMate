package com.planmate.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * TRIP_DAY (일차) — 여행 일수만큼 만드는 일차 (TRP-01-03, TRP-03-03, SCH-01-01).
 * 여행을 지우면 DB가 함께 지운다(ON DELETE CASCADE).
 */
@Entity
@Table(name = "TRIP_DAY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDay {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TRIP_DAY_GEN")
    @SequenceGenerator(name = "SEQ_TRIP_DAY_GEN", sequenceName = "SEQ_TRIP_DAY", allocationSize = 1)
    @Column(name = "DAY_ID")
    private Long dayId;

    @Column(name = "TRIP_ID", nullable = false, updatable = false)
    private Long tripId;

    // 1~N (UQ_TRIP_DAY_01: 여행 + 일차 번호)
    @Column(name = "DAY_NO", nullable = false)
    private Integer dayNo;

    // 현지 날짜 (UQ_TRIP_DAY_02: 여행 + 날짜)
    @Column(name = "DAY_DATE", nullable = false)
    private LocalDate dayDate;

    private TripDay(Long tripId, Integer dayNo, LocalDate dayDate) {
        this.tripId = tripId;
        this.dayNo = dayNo;
        this.dayDate = dayDate;
    }

    /** TRP-01-03 일차 1개 (예: 2일차 = 시작일 + 1일) */
    public static TripDay of(Long tripId, Integer dayNo, LocalDate dayDate) {
        return new TripDay(tripId, dayNo, dayDate);
    }
}