package com.planmate.community.dto;

import java.time.LocalDateTime;

/**
 * PUB-02-04 내 게시글 카드 한 개 (contracts/community.yaml MyPostResponse).
 * "도쿄 3박 4일 가족여행 공개" / "게시 10.20 · 수정 10.28 · 좋아요 12 · 댓글 3"
 */
public record MyPostResponse(
        Long postId,
        Long tripId, // [공개 전환] → 그 여행의 공유하기 흐름(PUB-01-01)
        String title,
        PostShareStatusResponse.Status status, // PUBLIC / PRIVATE / ADMIN_HIDDEN ("공개" / "비공개" / "관리자 비공개")
        LocalDateTime publishedAt,
        LocalDateTime updatedAt, // 고친 적 없으면 null
        int likeCount,
        long commentCount,
        boolean communityWritable) { // 이용 제한 회원이면 false → [비공개 전환]·[공개 전환] 비활성
}
