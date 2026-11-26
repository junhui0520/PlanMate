package com.planmate.participant.repository;

import com.planmate.participant.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** TRIP_MEMBER (여행 참여자) 넣기·꺼내기 */
public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    /** 여행의 내 참여 관계 (ACC-07-02 역할 판정) */
    Optional<TripMember> findByTripIdAndMemberId(Long tripId, Long memberId);
}
