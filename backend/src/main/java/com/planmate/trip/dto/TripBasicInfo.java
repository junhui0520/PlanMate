package com.planmate.trip.dto;

import java.time.LocalDate;

/**
 * 여행 기본 정보 (D 파트 경비가 사용, BUD-02·03).
 * 일수 계산과 나라 코드 -> 통화 변환은 D에서 한다.
 */
public record TripBasicInfo(
        Integer peopleCount,  // 여행 인원 (참여자 수와 별개)
        LocalDate startDate,  // 현지 날짜
        LocalDate endDate,
        String countryCode) { // 여행 도시의 나라 코드 (예: JP)
}