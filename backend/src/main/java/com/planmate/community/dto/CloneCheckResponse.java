package com.planmate.community.dto;

/**
 * PUB-07-01 복제 창 위쪽 "내 여행 n/3" (B의 TRP-01-01 생성 가능 확인과 같음).
 * 이미 3개면 이 응답 대신 TRIP_LIMIT_EXCEEDED(409)가 나간다.
 */
public record CloneCheckResponse(
        int activeTripCount, // 내가 참여 중인 활성 여행 수
        int maxTripCount) { // 최대 개수 (3)
}
