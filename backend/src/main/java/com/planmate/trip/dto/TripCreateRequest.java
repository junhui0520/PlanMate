package com.planmate.trip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * TRP-01-03 여행 생성 입력 (contracts/trip.yaml TripCreateRequest).
 * 칸 하나만 보고 알 수 있는 규칙은 여기서 검사한다(@Valid).
 * 날짜끼리 비교·시작일 범위(여행지 시간대 필요)는 TripService에서 검사한다.
 */
public record TripCreateRequest(

        // 같은 요청 두 번 처리 방지용. 길이 검사는 DuplicateRequestChecker가 한다
        String requestKey,

        // TRP-01-01 제목: 앞뒤 공백 제거 후 1~50자, 공백만 불가.
        // 글자 수는 이모지를 1자로 센다(화면 countChars·Oracle 50 CHAR와 맞춤).
        // @Size는 이모지를 2자로 세서 쓰지 않고, 정규식으로 앞뒤 공백을 뺀 글자 수를 센다.
        @NotBlank(message = "여행 제목을 입력해 주세요.")
        @Pattern(regexp = "^\\s*(?s:.{1,50}?)\\s*$", message = "입력값을 확인해 주세요.")
        String title,

        // TRP-01-02에서 고른 도시 ID
        @NotNull(message = "입력값을 확인해 주세요.")
        Long cityId,

        // 현지 날짜, 시간대 변환 없음. 범위 검사는 TripService
        @NotNull(message = "입력값을 확인해 주세요.")
        LocalDate startDate,

        @NotNull(message = "입력값을 확인해 주세요.")
        LocalDate endDate,

        // TRP-01-01 여행 인원: 정수 1~20
        @NotNull(message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        @Min(value = 1, message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        @Max(value = 20, message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        Integer peopleCount
) {
}