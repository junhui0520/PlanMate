package com.planmate.trip.service;

import com.planmate.common.security.TripStatusReader;
import com.planmate.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 여행 상태 조회 (ACC-07-02). A의 TripAccessChecker가 참여자 검사할 때 부른다.
 * TRIP은 B 테이블이라 A 대신 B가 TRIP.STATUS를 읽어 준다.
 */
@Component
@RequiredArgsConstructor
public class TripStatusReaderImpl implements TripStatusReader {

    private final TripRepository tripRepository;

    // TRIP.STATUS (ACTIVE / TRASHED). 여행이 없으면 Optional.empty()
    @Override
    @Transactional(readOnly = true)
    public Optional<TripStatus> findStatus(Long tripId) {
        return tripRepository.findById(tripId)
                .map(trip -> TripStatus.valueOf(trip.getStatus().name()));
    }
}