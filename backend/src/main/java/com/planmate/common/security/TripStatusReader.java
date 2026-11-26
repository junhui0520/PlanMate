package com.planmate.common.security;

import java.util.Optional;

/**
 * 여행 상태 조회 (ACC-07-02 TripAccessChecker가 사용).
 * B 파트 TripService(또는 trip/service의 @Component 1개)가 구현한다.
 * A는 TRIP 테이블을 직접 조회하지 않는다(AGENTS §6).
 */
public interface TripStatusReader {

    // TRIP.STATUS 조회. 여행이 없으면 Optional.empty()
    Optional<TripStatus> findStatus(Long tripId);

    enum TripStatus { ACTIVE, TRASHED }
}
