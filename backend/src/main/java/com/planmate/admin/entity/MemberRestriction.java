package com.planmate.admin.entity;

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

import java.time.LocalDateTime;

/**
 * MEMBER_RESTRICTION (커뮤니티 이용 제한). schema.sql 그대로 전체 칼럼.
 * 만료는 시간으로 판정한다. 일찍 해제하면 ENDS_AT을 지금 시각으로 바꾼다 (ADM-01-03).
 * 생성·해제 메서드는 ADM-01-02·03에서 추가한다.
 */
@Entity
@Table(name = "MEMBER_RESTRICTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MEMBER_RESTRICTION_GENERATOR")
    @SequenceGenerator(name = "SEQ_MEMBER_RESTRICTION_GENERATOR", sequenceName = "SEQ_MEMBER_RESTRICTION", allocationSize = 1)
    @Column(name = "RESTRICTION_ID")
    private Long restrictionId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    // DB설계서에 허용값이 없어 문자열로 둔다. ADM-01-02에서 enum으로 바꾼다
    @Column(name = "REASON_CODE", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "ADMIN_NOTE", length = 1000)
    private String adminNote;

    @Column(name = "STARTS_AT", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ENDS_AT")
    private LocalDateTime endsAt;

    // 관리자 회원 삭제 시 DB가 비움
    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "REVOKED_BY")
    private Long revokedBy;

    @Column(name = "REVOKE_REASON", length = 500)
    private String revokeReason;

    /**
     * now 시각에 제한 중인지 (ACC-07-01). MemberRestrictionRepository.existsActive와 같은 규칙.
     * 시작 시각 이후이고, 종료 시각이 없거나 종료 시각 전이면 제한 중. 종료 시각 정각부터는 해제.
     */
    public boolean isActiveAt(LocalDateTime now) {
        return !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }
}
