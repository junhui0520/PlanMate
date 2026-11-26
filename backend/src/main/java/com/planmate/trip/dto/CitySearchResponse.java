package com.planmate.trip.dto;

/**
 * TRP-01-02 도시 검색 결과 한 줄 (contracts/trip.yaml CitySearchResponse).
 * 화면 표시 "도쿄 · 일본". DB에 저장하지 않는다.
 */
public record CitySearchResponse(
        String googlePlaceId, // 도시를 고를 때 POST /api/cities로 다시 보낸다
        String cityName,      // 도시 이름 (한국어)
        String countryName) { // 국가 이름 (한국어)
}