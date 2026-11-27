package com.planmate.community.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.planmate.community.entity.PostComment;

/**
 * POST_COMMENT (댓글) 넣기·꺼내기 (PUB-08).
 * 목록(PUB-08-02): 작성 시각 오래된 순, 같으면 COMMENT_ID 작은 순 (IX_POST_COMMENT_POST). 마지막 댓글 다음부터 이어 받는다.
 */
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    // PUB-02-04 내 게시글 카드의 "댓글 3"
    long countByPostId(Long postId);

    // PUB-08-02 댓글 첫 묶음
    @Query("""
            select c from PostComment c
            where c.postId = :postId
            order by c.createdAt asc, c.commentId asc
            """)
    List<PostComment> findFirstPage(@Param("postId") Long postId, Limit limit);

    // PUB-08-02 댓글 이어 받기 (마지막 댓글보다 뒤)
    @Query("""
            select c from PostComment c
            where c.postId = :postId
              and (c.createdAt > :createdAt or (c.createdAt = :createdAt and c.commentId > :commentId))
            order by c.createdAt asc, c.commentId asc
            """)
    List<PostComment> findNextPage(@Param("postId") Long postId, @Param("createdAt") LocalDateTime createdAt,
            @Param("commentId") Long commentId, Limit limit);

    // PUB-08-01 같은 회원 10초에 1개: 내가 마지막으로 쓴 댓글의 작성 시각 (없으면 null)
    @Query("select max(c.createdAt) from PostComment c where c.authorId = :authorId")
    LocalDateTime findLastWrittenAt(@Param("authorId") Long authorId);
}
