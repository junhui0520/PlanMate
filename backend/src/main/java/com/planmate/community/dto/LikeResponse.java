package com.planmate.community.dto;

/**
 * PUB-05-01·02 좋아요·취소 결과 (contracts/community.yaml LikeResponse).
 * 화면은 먼저 바꿔 보여 주고, 이 값으로 맞춘다.
 */
public record LikeResponse(
        boolean liked, // 내가 좋아요 한 상태인지
        int likeCount) { // 게시물 좋아요 수
}
