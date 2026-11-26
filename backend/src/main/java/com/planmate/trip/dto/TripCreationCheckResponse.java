package com.planmate.trip.dto;

/**
 * TRP-01-01 여행 생성 가능 확인 결과 (contracts/trip.yaml TripCreationCheckResponse).
 * 3개 이상이면 이 응답 대신 TRIP_LIMIT_EXCEEDED(409)가 나간다.
 */
public record TripCreationCheckResponse(
        int activeTripCount, // 내가 참여 중인 활성 여행 수
        int maxTripCount) {   // 최대 개수 (구독 기능 전까지 3)
}