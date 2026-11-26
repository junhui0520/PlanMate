package com.planmate.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * MEMBER (회원). schema.sql 그대로 전체 칼럼.
 * 생성 메서드는 가입 기능(ACC-01·03)에서 추가한다.
 */
@Entity
@Table(name = "MEMBER")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    /** 로그인 제공자 (DB설계서 허용값) */
    public enum Provider { EMAIL, KAKAO, NAVER, GOOGLE, APPLE }

    /** 시스템 역할. 관리자는 운영자가 서버에서 미리 부여 (ACC-07-03) */
    public enum SystemRole { ADMIN, USER }

    /** 계정 상태. 탈퇴 확정 시 WITHDRAWN(2주 보관 중) */
    public enum Status { ACTIVE, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MEMBER_GENERATOR")
    @SequenceGenerator(name = "SEQ_MEMBER_GENERATOR", sequenceName = "SEQ_MEMBER", allocationSize = 1)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "NICKNAME", nullable = false, length = 12)
    private String nickname;

    @Column(name = "PROFILE_IMAGE_URL", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "PROVIDER", nullable = false, length = 10)
    private Provider provider;

    @Column(name = "EMAIL_NORMALIZED", length = 254)
    private String emailNormalized;

    @Column(name = "PASSWORD_HASH", length = 255)
    private String passwordHash;

    @Column(name = "PASSWORD_CHANGED_AT")
    private LocalDateTime passwordChangedAt;

    @Column(name = "PROVIDER_USER_ID", length = 100)
    private String providerUserId;

    // 집 주소: 본인만 열람, 관리자도 볼 수 없음 (ACC-05-02)
    @Column(name = "ZIP_CODE", length = 5)
    private String zipCode;

    @Column(name = "ADDRESS", length = 300)
    private String address;

    @Column(name = "ADDRESS_DETAIL", length = 200)
    private String addressDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "SYSTEM_ROLE", nullable = false, length = 10)
    private SystemRole systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private Status status;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 닉네임·프로필 사진 수정 시각이라 자동 기록하지 않는다. ACC-05에서 직접 넣는다
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // 탈퇴 확정 시각. 2주 보관 기한 계산용 (ACC-06-03)
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    /** 정상 계정인지 (ACC-07-01) */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** 관리자인지 (ACC-07-03) */
    public boolean isAdmin() {
        return systemRole == SystemRole.ADMIN;
    }
}
