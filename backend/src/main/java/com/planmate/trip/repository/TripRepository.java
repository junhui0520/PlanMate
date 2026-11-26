package com.planmate.trip.repository;

import com.planmate.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

/**
 * TRIP (여행) 넣기·꺼내기 (TRP-01~05).
 */
public interface TripRepository extends JpaRepository<Trip, Long> {

    /*
     * TRP-01-01·03 생성 개수 확인: 주어진 여행들 중 상태가 status인 여행 수.
     * 내가 참여 중인 여행 ID 목록은 A의 ParticipantService에서 받는다(TRIP_MEMBER는 A 테이블이라 직접 조회하지 않음).
     */
    long countByTripIdInAndStatus(Collection<Long> tripIds, Trip.Status status);
}