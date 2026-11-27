package com.planmate.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * REPORT (신고). schema.sql 그대로 전체 칼럼. D가 주인이고, A의 관리자 신고 검토(ADM-02)가 같은 표를 읽는다.
 * 게시물 ID와 댓글 ID는 둘 다 채우지 않는다(CK_REPORT_01). 대상이 지워지면 DB가 그 ID를 비운다(대상 종류는 남음).
 * 상태: RECEIVED(접수) → DONE(처리 완료, ADM-02에서). 처리 결과·이력은 A의 REPORT_ACTION.
 */
@Entity
@Table(name = "REPORT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    /** 대상 종류 (DB설계서 허용값) */
    public enum TargetType { POST, COMMENT }

    /** 사유: 스팸·광고 / 욕설·괴롭힘 / 개인정보 노출 / 부적절한 닉네임 / 기타(직접 입력) */
    public enum Reason { SPAM, ABUSE, PRIVACY, NICKNAME, ETC }

    /** 상태: 접수 / 처리 완료 */
    public enum Status { RECEIVED, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_REPORT_GENERATOR")
    @SequenceGenerator(name = "SEQ_REPORT_GENERATOR", sequenceName = "SEQ_REPORT", allocationSize = 1)
    @Column(name = "REPORT_ID")
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TARGET_TYPE", nullable = false, length = 10)
    private TargetType targetType;

    // 게시물 신고일 때 (게시물이 지워지면 비움)
    @Column(name = "POST_ID")
    private Long postId;

    // 댓글 신고일 때 (댓글이 지워지면 비움)
    @Column(name = "COMMENT_ID")
    private Long commentId;

    // 신고자 (탈퇴하면 비워질 수 있음). 관리자 전용 정보
    @Column(name = "REPORTER_ID")
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REASON", nullable = false, length = 10)
    private Reason reason;

    // 기타일 때만 1~300자
    @Column(name = "DETAIL", length = 300)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private Status status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** PUB-09-01 게시물 신고 접수 */
    public static Report ofPost(Long postId, Long reporterId, Reason reason, String detail) {
        Report report = receive(TargetType.POST, reporterId, reason, detail);
        report.postId = postId;
        return report;
    }

    /** PUB-09-01 댓글 신고 접수 */
    public static Report ofComment(Long commentId, Long reporterId, Reason reason, String detail) {
        Report report = receive(TargetType.COMMENT, reporterId, reason, detail);
        report.commentId = commentId;
        return report;
    }

    private static Report receive(TargetType targetType, Long reporterId, Reason reason, String detail) {
        Report report = new Report();
        report.targetType = targetType;
        report.reporterId = reporterId;
        report.reason = reason;
        report.detail = detail;
        report.status = Status.RECEIVED;
        report.createdAt = DateTimeUtil.now();
        return report;
    }
}
