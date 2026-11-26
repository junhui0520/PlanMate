package com.planmate.trip.repository;

import com.planmate.trip.entity.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * TRIP_DAY (일차) 넣기·꺼내기 (TRP-01-03, SCH-01-01).
 */
public interface TripDayRepository extends JpaRepository<TripDay, Long> {

    // 여행의 일차 목록 (1일차부터 순서대로)
    List<TripDay> findByTripIdOrderByDayNoAsc(Long tripId);
}