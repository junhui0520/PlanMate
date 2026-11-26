package com.planmate.trip.service;

import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.trip.entity.Trip;
import com.planmate.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * ACC-07-02 여행 상태 조회 (A의 TripAccessChecker가 사용). DB 없이 레포지토리는 가짜로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class TripStatusReaderImplTest {

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripStatusReaderImpl tripStatusReader;

    @Test
    void 활성_여행이면_ACTIVE() {
        Trip trip = Trip.create("도쿄 여행", 1L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 12), 2, 1L);
        given(tripRepository.findById(12L)).willReturn(Optional.of(trip));

        assertThat(tripStatusReader.findStatus(12L)).contains(TripStatus.ACTIVE);
    }

    @Test
    void 없는_여행이면_빈_값() {
        given(tripRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(tripStatusReader.findStatus(99L)).isEmpty();
    }
}