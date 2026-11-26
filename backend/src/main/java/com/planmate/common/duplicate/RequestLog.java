package com.planmate.common.duplicate;

import com.planmate.common.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * REQUEST_LOG (요청 기록) — 같은 요청 두 번 처리 방지 (TRP-01-03·SCH-06-01·PUB-01-03·PUB-07-02·AIT-06-01).
 * PK는 화면이 만든 요청 식별값(시퀀스 없음). 다른 파트 엔티티와 묶지 않고 ID 값만 들고 있는다.
 * Persistable을 구현해 save가 SELECT 없이 바로 INSERT하게 한다.
 */
@Entity
@Table(name = "REQUEST_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestLog implements Persistable<String> {

    /** 처리 결과. DB설계서에 허용값이 없어 SUCCESS로 확정 */
    public enum Result {
        SUCCESS
    }

    @Id
    @Column(name = "REQUEST_KEY", length = 64)
    private String requestKey;

    // 회원 삭제 시 DB가 비움(ON DELETE SET NULL)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    // 여행 삭제 시 DB가 비움(ON DELETE SET NULL)
    @Column(name = "TRIP_ID")
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT", nullable = false)
    private Result result;

    // JPA가 NULL을 명시적으로 넣으면 DB 기본값(SYSTIMESTAMP)이 적용되지 않으므로 코드에서 채운다
    @Column(name = "PROCESSED_AT", nullable = false)
    private LocalDateTime processedAt;

    @Transient
    private boolean isNew = true;

    private RequestLog(String requestKey, Long memberId, Long tripId, Result result, LocalDateTime processedAt) {
        this.requestKey = requestKey;
        this.memberId = memberId;
        this.tripId = tripId;
        this.result = result;
        this.processedAt = processedAt;
    }

    /** 처리 성공 기록 */
    public static RequestLog success(String requestKey, Long memberId, Long tripId) {
        return new RequestLog(requestKey, memberId, tripId, Result.SUCCESS, DateTimeUtil.now());
    }

    @Override
    public String getId() {
        return requestKey;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
