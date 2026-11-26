package com.planmate.community.dto;

import java.time.LocalDateTime;

/**
 * PUB-06-02 내 북마크 카드 한 개 (contracts/community.yaml MyBookmarkResponse).
 * 비공개·관리자 숨김·원래 여행 휴지통이 된 게시물은 카드만 남기고 내용 없이 보낸다(visible = false, post = null).
 */
public record MyBookmarkResponse(
        Long bookmarkId, // 이어 받기 기준 (마지막 북마크)
        Long postId, // 해제할 때 쓰는 게시물 ID
        boolean visible, // false면 "볼 수 없는 게시물" (제목·도시도 숨김)
        PostListResponse post, // 공개 목록 카드와 같은 값, 볼 수 없으면 null
        LocalDateTime bookmarkedAt) { // 담은 시각
}
