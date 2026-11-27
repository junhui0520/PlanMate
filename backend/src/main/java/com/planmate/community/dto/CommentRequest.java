package com.planmate.community.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUB-08-01·03 댓글 입력값 (contracts/community.yaml CommentRequest).
 * 1~500자(이모지 1글자), 줄바꿈 허용, 다른 제어문자는 공백 1칸으로 — PostCommentService가 검사·정리한다.
 * 빈 내용은 화면에서 [등록]이 꺼져 있어 기능정의서 문구가 없다(INVALID_INPUT).
 */
public record CommentRequest(
        @NotBlank(message = "입력값을 확인해 주세요.") String content) {
}
