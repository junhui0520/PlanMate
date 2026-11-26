package com.planmate.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.planmate.community.entity.PostLike;

/**
 * POST_LIKE (좋아요) 넣기·꺼내기 (PUB-05).
 */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    // PUB-04-03 내가 좋아요 했는지 (채워진 [좋아요])
    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    Optional<PostLike> findByPostIdAndMemberId(Long postId, Long memberId);
}
