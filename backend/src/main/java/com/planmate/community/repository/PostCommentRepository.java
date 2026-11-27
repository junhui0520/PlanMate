package com.planmate.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.planmate.community.entity.PostComment;

/**
 * POST_COMMENT (댓글) 넣기·꺼내기. 댓글 목록·작성은 PUB-08에서 추가한다.
 */
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    // PUB-02-04 내 게시글 카드의 "댓글 3"
    long countByPostId(Long postId);
}
