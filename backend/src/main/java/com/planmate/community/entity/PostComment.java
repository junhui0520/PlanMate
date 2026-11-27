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

/**
 * POST_COMMENT (공개 댓글). schema.sql 그대로 전체 칼럼. 게시물이 지워지면 DB가 함께 지운다.
 * 지금은 PUB-02-04 내 게시글의 댓글 수를 세는 데만 쓴다. 작성·수정·삭제 메서드는 PUB-08에서 추가한다.
 */
@Entity
@Table(name = "POST_COMMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_POST_COMMENT_GENERATOR")
    @SequenceGenerator(name = "SEQ_POST_COMMENT_GENERATOR", sequenceName = "SEQ_POST_COMMENT", allocationSize = 1)
    @Column(name = "COMMENT_ID")
    private Long commentId;

    @Column(name = "POST_ID", nullable = false, updatable = false)
    private Long postId;

    // 작성자 (탈퇴하면 비워질 수 있음)
    @Column(name = "AUTHOR_ID")
    private Long authorId;

    // 1~500자, 줄바꿈 가능 (PUB-08-01)
    @Column(name = "CONTENT", nullable = false, length = 500)
    private String content;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 고쳤으면 Y ("수정됨", PUB-08-03)
    @Convert(converter = YesNoConverter.class)
    @Column(name = "IS_EDITED", nullable = false, length = 1)
    private Boolean isEdited;
}
