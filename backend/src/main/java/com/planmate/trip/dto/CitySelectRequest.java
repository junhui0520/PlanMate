package com.planmate.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TRP-01-02 도시 선택 입력 (contracts/trip.yaml CitySelectRequest).
 * 검색 결과에서 고른 도시의 구글 장소 ID만 받는다(직접 타이핑한 글자는 도시로 저장하지 않음).
 */
public record CitySelectRequest(
        @NotBlank(message = "입력값을 확인해 주세요.")
        @Size(max = 300, message = "입력값을 확인해 주세요.")
        String googlePlaceId) {
}