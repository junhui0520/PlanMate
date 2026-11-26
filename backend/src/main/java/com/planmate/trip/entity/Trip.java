package com.planmate.trip.entity;

import com.planmate.common.util.DateTimeUtil;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * TRIP (여행) — 여행 기본 정보·상태 (TRP-01~05).
 * 도시는 같은 B 파트지만 단순하게 ID 값(cityId)으로 들고 있는다.
 */
@Entity
@Table(name = "TRIP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    /** 여행 상태 (DB설계서 허용값: ACTIVE, TRASHED) */
    public enum Status {
        ACTIVE, TRASHED
    }

    /** 기본 이동수단 (DB설계서 허용값: TRANSIT_WALK, CAR). 처음은 TRANSIT_WALK (RTE-01-01) */
    public enum TravelMode {
        TRANSIT_WALK, CAR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TRIP_GEN")
    @SequenceGenerator(name = "SEQ_TRIP_GEN", sequenceName = "SEQ_TRIP", allocationSize = 1)
    @Column(name = "TRIP_ID")
    private Long tripId;

    // TRP-01-01 제목 1~50자, 제어문자 불가
    @Column(name = "TITLE", nullable = false, length = 50)
    private String title;

    // 생성 후 변경 불가 (TRP-01-02)
    @Column(name = "CITY_ID", nullable = false, updatable = false)
    private Long cityId;

    // 현지 날짜, 시간대 변환 없음
    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    // 여행 인원 1~20, 참여자 수와 별개
    @Column(name = "PEOPLE_COUNT", nullable = false)
    private Integer peopleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRAVEL_MODE", nullable = false, length = 15)
    private TravelMode travelMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private Status status;

    // 만든 사람. 회원 삭제 시 DB가 비울 수 있어 null 허용
    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    // JPA가 NULL을 넣으면 DB 기본값(SYSTIMESTAMP)이 적용되지 않으므로 코드에서 채운다
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    // 휴지통으로 옮긴 시각 (TRP-05-01)
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    private Trip(String title, Long cityId, LocalDate startDate, LocalDate endDate,
                 Integer peopleCount, Long createdBy) {
        this.title = title;
        this.cityId = cityId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.peopleCount = peopleCount;
        this.travelMode = TravelMode.TRANSIT_WALK;
        this.status = Status.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = DateTimeUtil.now();
    }

    /** TRP-01-03 새 여행 (상태 ACTIVE, 이동수단 TRANSIT_WALK). 값 검사는 서비스에서 끝낸 뒤 부른다 */
    public static Trip create(String title, Long cityId, LocalDate startDate, LocalDate endDate,
                              Integer peopleCount, Long createdBy) {
        return new Trip(title, cityId, startDate, endDate, peopleCount, createdBy);
    }
}