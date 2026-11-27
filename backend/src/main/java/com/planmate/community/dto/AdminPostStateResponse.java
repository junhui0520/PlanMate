package com.planmate.community.dto;

/**
 * PUB-09-02 관리자 비공개·해제 결과 (A의 관리자 신고 처리 ADM-02-03·04가 받는 값).
 * A는 이 값으로 처리 이력을 남기고 알림 받을 사람(게시물 작성자)을 정한다.
 * 화면 표시 예: adminHidden=false, isPublic=false → "관리자 비공개 해제 · 비공개 유지"
 */
public record AdminPostStateResponse(
        Long postId,
        Long tripId,
        Long authorId, // 게시물 작성자 (탈퇴했으면 null) → "관리자에 의해 비공개 처리되었습니다." 받는 사람
        boolean isPublic, // 소유자가 정한 공개 여부 (관리자 조치로 바뀌지 않음)
        boolean adminHidden) { // 관리자 비공개
}
