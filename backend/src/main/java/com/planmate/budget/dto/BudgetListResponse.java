package com.planmate.budget.dto;

import java.util.List;

import com.planmate.participant.entity.TripMember;

/**
 * BUD-01-01 경비 탭 목록 (contracts/budget.yaml BudgetListResponse).
 * 경비 항목과 함께 화면이 쓰는 여행 정보를 준다: 통화 기본값(BUD-01-02), 1인당 표시(BUD-03), 일수 기본값(BUD-02-01).
 */
public record BudgetListResponse(
		Long tripId,
		String tripCurrency, // 여행 도시 통화 (나라 코드 → 통화, KRW면 국내 여행이라 환율 위젯 숨김)
		int memberCount, // 여행 인원 (TRIP.PEOPLE_COUNT, 참여자 수와 별개)
		int tripDays, // 여행 일수 (시작일~종료일)
		Long myMemberId, // 내 개인 경비 구분
		TripMember.Role myRole, // OWNER / EDITOR / VIEWER (VIEWER면 추가 버튼 없음)
		List<Participant> participants, // 개인 경비 카드의 주인 닉네임
		List<BudgetItemResponse> items) { // 추가한 순서

	/** 참여자 한 명 (개인 경비 카드 제목) */
	public record Participant(Long memberId, String nickname) {
	}
}
