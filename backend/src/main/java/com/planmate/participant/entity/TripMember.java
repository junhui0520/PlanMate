package com.planmate.participant.entity;

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

/**
 * TRIP_MEMBER (여행 참여자). schema.sql 그대로 전체 칼럼.
 * TRIP·MEMBER와 @ManyToOne으로 묶지 않고 ID 값만 들고 있는다 (AGENTS §6).
 * 생성·역할 변경 메서드는 COL-02·03·05에서 추가한다.
 */
@Entity
@Table(name = "TRIP_MEMBER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMember {

    /** 여행 역할. 초대 참여 시 VIEWER, OWNER는 여행당 1명 */
    public enum Role { OWNER, EDITOR, VIEWER }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TRIP_MEMBER_GENERATOR")
    @SequenceGenerator(name = "SEQ_TRIP_MEMBER_GENERATOR", sequenceName = "SEQ_TRIP_MEMBER", allocationSize = 1)
    @Column(name = "TRIP_MEMBER_ID")
    private Long tripMemberId;

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 10)
    private Role role;
}
