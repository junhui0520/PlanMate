package com.planmate.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.type.YesNoConverter;

import com.planmate.common.util.DateTimeUtil;

/**
 * PUBLIC_POST (공개 게시물). schema.sql 그대로 전체 칼럼. 여행당 1개(UQ_PUBLIC_POST_01).
 * TRIP·MEMBER와 @ManyToOne으로 묶지 않고 ID 값만 들고 있는다 (AGENTS §6).
 * 공개 일정은 따로 복사하지 않고 원래 여행의 현재 일정에서 읽는다 (PUB-01-03).
 * 사진(POST_IMAGE_URL)은 형식·크기·저장 위치가 정해지지 않아 아직 쓰지 않는다(항상 비움 → 기본 배경). TODO(팀 결정)
 */
@Entity
@Table(name = "PUBLIC_POST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicPost {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_PUBLIC_POST_GENERATOR")
    @SequenceGenerator(name = "SEQ_PUBLIC_POST_GENERATOR", sequenceName = "SEQ_PUBLIC_POST", allocationSize = 1)
    @Column(name = "POST_ID")
    private Long postId;

    @Column(name = "TRIP_ID", nullable = false, updatable = false)
    private Long tripId;

    // 작성자 (소유권 이전 때 바뀜, 탈퇴하면 비워질 수 있음)
    @Column(name = "AUTHOR_ID")
    private Long authorId;

    // 공개용 제목 1~40자
    @Column(name = "POST_TITLE", nullable = false, length = 40)
    private String postTitle;

    // 공개용 소개 0~100자 (줄바꿈 가능)
    @Column(name = "POST_INTRO", length = 100)
    private String postIntro;

    // 대표 사진 최대 1장 (없으면 화면이 기본 배경)
    @Column(name = "POST_IMAGE_URL", length = 500)
    private String postImageUrl;

    // 소유자가 정한 공개 여부 (PUB-02-02 비공개 전환이면 N)
    @Convert(converter = YesNoConverter.class)
    @Column(name = "IS_PUBLIC", nullable = false, length = 1)
    private Boolean isPublic;

    // 관리자 비공개 (Y면 소유자가 공개로 못 바꿈)
    @Convert(converter = YesNoConverter.class)
    @Column(name = "ADMIN_HIDDEN", nullable = false, length = 1)
    private Boolean adminHidden;

    @Column(name = "LIKE_COUNT", nullable = false)
    private Integer likeCount;

    // 처음 게시 시각 (최신순 기준, 제목을 고쳐도 그대로)
    @Column(name = "PUBLISHED_AT", nullable = false)
    private LocalDateTime publishedAt;

    // 제목·소개·사진을 고친 시각 (PUB-02-05, 고친 적 없으면 null)
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** PUB-01-03 처음 게시: 공개, 관리자 숨김 아님, 좋아요 0, 처음 게시 시각 기록 */
    public static PublicPost publish(Long tripId, Long authorId, String title, String intro) {
        PublicPost post = new PublicPost();
        post.tripId = tripId;
        post.authorId = authorId;
        post.postTitle = title;
        post.postIntro = intro;
        post.isPublic = true;
        post.adminHidden = false;
        post.likeCount = 0;
        post.publishedAt = DateTimeUtil.now();
        return post;
    }

    /**
     * PUB-01-03 비공개로 바꾼 게시물을 다시 공개: 같은 게시물에 새 제목·소개를 저장(좋아요·댓글 유지).
     * 정한 내용(11/27): 처음 게시 시각은 그대로 두고 수정 시각만 기록한다.
     */
    public void republish(Long authorId, String title, String intro) {
        this.authorId = authorId;
        this.postTitle = title;
        this.postIntro = intro;
        this.isPublic = true;
        this.updatedAt = DateTimeUtil.now();
    }

    /** PUB-02-02 비공개 전환 (좋아요·댓글·북마크는 그대로) */
    public void makePrivate() {
        this.isPublic = false;
    }

    /** PUB-02-05 공개용 제목·소개 수정 (일정·좋아요·처음 게시 시각은 그대로) */
    public void updateInfo(String title, String intro) {
        this.postTitle = title;
        this.postIntro = intro;
        this.updatedAt = DateTimeUtil.now();
    }

    /** PUB-05-01 좋아요 수 +1 (게시물 행을 잠근 뒤 부른다) */
    public void increaseLikeCount() {
        likeCount = likeCount + 1;
    }

    /** PUB-05-02 좋아요 수 -1, 0 아래로 내려가지 않음 (게시물 행을 잠근 뒤 부른다) */
    public void decreaseLikeCount() {
        likeCount = Math.max(0, likeCount - 1);
    }

    /** 공개 상태이고 관리자 숨김이 아님 (원래 여행 휴지통 여부는 따로 B에게 묻는다) */
    public boolean isOpen() {
        return Boolean.TRUE.equals(isPublic) && !Boolean.TRUE.equals(adminHidden);
    }
}
