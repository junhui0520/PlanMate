package com.planmate.community.dto;

/**
 * PUB-09-02 관리자 댓글 삭제 결과 (A의 관리자 신고 처리 ADM-02-03이 받는 값).
 * 댓글 행은 지워졌고(되돌릴 수 없음), 신고·조치 행의 댓글 ID는 DB가 비운다(ON DELETE SET NULL).
 * A는 이 값으로 처리 이력을 남기고 조치 안내를 받을 사람(댓글 작성자)을 정한다.
 */
public record AdminCommentDeletedResponse(
        Long commentId,
        Long postId,
        Long authorId) { // 댓글 작성자 (탈퇴했으면 null)
}
