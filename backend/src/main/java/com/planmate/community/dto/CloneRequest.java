package com.planmate.community.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUB-07-01·02 복제 조건 입력값 (contracts/community.yaml CloneRequest).
 * 종료일은 받지 않는다(시작일 + 공개본 일수 - 1로 서버가 정함). 도시·제목도 공개본 그대로.
 * 시작일 범위(여행지 오늘 ~ 1년 후)는 B의 여행 생성(TRP-01-01·03)이 검사한다.
 */
public record CloneRequest(
        String requestKey, // 같은 요청 두 번 처리 방지 (두 번 눌러도 여행 1개)
        @NotNull(message = "입력값을 확인해 주세요.") LocalDate startDate,
        // TRP-01-01과 같은 규칙·문구
        @NotNull(message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        @Min(value = 1, message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        @Max(value = 20, message = "여행 인원은 1~20 사이의 정수로 입력해 주세요.")
        Integer peopleCount) {
}
