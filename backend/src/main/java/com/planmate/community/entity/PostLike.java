package com.planmate.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST_LIKE (좋아요). 회원당 게시물 하나에 1행 (UQ_POST_LIKE_01, PUB-05-01).
 * 게시물이 지워지거나 회원이 지워지면 DB가 함께 지운다(ON DELETE CASCADE).
 */
@Entity
@Table(name = "POST_LIKE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_POST_LIKE_GENERATOR")
    @SequenceGenerator(name = "SEQ_POST_LIKE_GENERATOR", sequenceName = "SEQ_POST_LIKE", allocationSize = 1)
    @Column(name = "LIKE_ID")
    private Long likeId;

    @Column(name = "POST_ID", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "MEMBER_ID", nullable = false, updatable = false)
    private Long memberId;

    public static PostLike create(Long postId, Long memberId) {
        PostLike like = new PostLike();
        like.postId = postId;
        like.memberId = memberId;
        return like;
    }
}
