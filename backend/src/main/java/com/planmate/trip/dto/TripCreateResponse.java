package com.planmate.trip.dto;

/**
 * TRP-01-03 만든 여행 (contracts/trip.yaml TripCreateResponse).
 * 화면은 이 tripId로 일정 화면(SCH-01)으로 이동한다.
 */
public record TripCreateResponse(Long tripId) {
}