package com.planmate.community.dto;

import java.time.LocalDateTime;

import com.planmate.community.entity.PostComment;

/**
 * PUB-08-02 댓글 한 개 (contracts/community.yaml CommentResponse).
 * 한 줄: 닉네임 · 내용 · 작성 시각(오른쪽 끝), 고쳤으면 시각 아래 "수정됨". 본인 댓글이면 [···] 메뉴.
 */
public record CommentResponse(
        Long commentId,
        String content, // 글자 그대로 (화면도 글자로만 표시)
        String authorNickname,
        LocalDateTime createdAt,
        boolean edited,
        boolean mine) { // 로그인 회원 본인 댓글이면 true → [수정] [삭제]

    /**
     * TODO(A에 요청): authorNickname은 MemberService 닉네임 조회가 생기면 채운다. 지금은 null.
     */
    public static CommentResponse of(PostComment comment, Long viewerId) {
        return new CommentResponse(comment.getCommentId(), comment.getContent(), null, comment.getCreatedAt(),
                Boolean.TRUE.equals(comment.getIsEdited()),
                viewerId != null && viewerId.equals(comment.getAuthorId()));
    }
}
