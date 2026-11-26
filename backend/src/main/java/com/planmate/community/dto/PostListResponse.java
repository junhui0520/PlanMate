package com.planmate.community.dto;

import java.time.LocalDateTime;

import com.planmate.community.entity.PublicPost;

/**
 * PUB-03-01 공개 목록 카드 한 개 (contracts/community.yaml PostListResponse).
 * 카드: 사진 / 제목 / "도쿄 · 3일" / "지은 · 좋아요 12 · 수정 10.28"
 */
public record PostListResponse(
        Long postId,
        String title,
        String imageUrl, // null이면 화면이 기본 배경
        String cityName,
        Integer days,
        String authorNickname,
        Integer likeCount,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) { // 고친 적 없으면 null

    /**
     * TODO(B에 요청): cityName·days는 원래 여행(TRIP·CITY)에서 읽는다. TripService에 요청한 여행 기본 정보가 생기면 채운다.
     * TODO(A에 요청): authorNickname은 MemberService 닉네임 조회가 생기면 채운다.
     * 그때까지는 null로 보낸다(주소를 열기 전이라 화면 영향 없음).
     */
    public static PostListResponse from(PublicPost post) {
        return new PostListResponse(post.getPostId(), post.getPostTitle(), post.getPostImageUrl(),
                null, null, null, post.getLikeCount(), post.getPublishedAt(), post.getUpdatedAt());
    }
}
