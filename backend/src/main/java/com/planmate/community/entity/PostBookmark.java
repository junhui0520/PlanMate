package com.planmate.community.entity;

import java.time.LocalDateTime;

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

import com.planmate.common.util.DateTimeUtil;

/**
 * POST_BOOKMARK (북마크). 회원당 게시물 하나에 1행 (UQ_POST_BOOKMARK_01, PUB-06-01), 회원당 최대 200개.
 * 좋아요·장소 보관함·복제와 다른 동작. 게시물·회원이 지워지면 DB가 함께 지운다(ON DELETE CASCADE).
 */
@Entity
@Table(name = "POST_BOOKMARK")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_POST_BOOKMARK_GENERATOR")
    @SequenceGenerator(name = "SEQ_POST_BOOKMARK_GENERATOR", sequenceName = "SEQ_POST_BOOKMARK", allocationSize = 1)
    @Column(name = "BOOKMARK_ID")
    private Long bookmarkId;

    @Column(name = "POST_ID", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "MEMBER_ID", nullable = false, updatable = false)
    private Long memberId;

    // 담은 시각 (PUB-06-02 내 북마크 최신순)
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PostBookmark create(Long postId, Long memberId) {
        PostBookmark bookmark = new PostBookmark();
        bookmark.postId = postId;
        bookmark.memberId = memberId;
        bookmark.createdAt = DateTimeUtil.now();
        return bookmark;
    }
}
