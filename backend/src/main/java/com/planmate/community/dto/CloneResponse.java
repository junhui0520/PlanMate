package com.planmate.community.dto;

/**
 * PUB-07-02 복제 결과. 화면은 새 여행 일정 화면으로 이동하고 재확인 안내(PUB-07-03)를 한 번 보여 준다.
 */
public record CloneResponse(
        Long tripId) { // 새로 만든 내 여행 (비공개, 내가 소유자)
}
