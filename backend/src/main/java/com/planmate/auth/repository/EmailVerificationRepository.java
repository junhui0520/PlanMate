package com.planmate.auth.repository;

import com.planmate.auth.entity.EmailVerification;
import com.planmate.auth.entity.EmailVerification.Purpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * EMAIL_VERIFICATION (이메일 인증) 넣기·꺼내기.
 * ⚠ Oracle은 FETCH FIRST(최신 1건)와 FOR UPDATE를 같이 쓸 수 없다(ORA-02014).
 * 최신 행을 잠글 때는 findLatestIds로 ID만 잠금 없이 찾고 findByIdForUpdate로 PK 잠금 조회한다.
 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 같은 이메일+용도의 가장 최근 행 (잠금 없음, 재발송 간격 검사용 ACC-01-02) */
    Optional<EmailVerification> findFirstByEmailAndPurposeOrderByCreatedAtDescRequestIdDesc(String email, Purpose purpose);

    /** 같은 이메일+용도의 최근 행 ID (최신순). 엔티티를 미리 읽지 않아 잠금 조회 때 옛 상태가 남지 않는다 */
    @Query("""
            select v.requestId from EmailVerification v
            where v.email = :email and v.purpose = :purpose
            order by v.createdAt desc, v.requestId desc
            """)
    List<Long> findLatestIds(@Param("email") String email, @Param("purpose") Purpose purpose, Limit limit);

    /** 이메일+용도 기준 since 이후 발급 개수와 가장 오래된 발급 시각 (시간당 5회, A-P04) */
    @Query("""
            select count(v) as count, min(v.createdAt) as oldest from EmailVerification v
            where v.email = :email and v.purpose = :purpose and v.createdAt > :since
            """)
    WindowStats countByEmailSince(@Param("email") String email, @Param("purpose") Purpose purpose,
                                  @Param("since") LocalDateTime since);

    /** 요청 IP 해시 기준 since 이후 발급 개수와 가장 오래된 발급 시각 (시간당 30회, A-P04) */
    @Query("""
            select count(v) as count, min(v.createdAt) as oldest from EmailVerification v
            where v.requestIpHash = :ipHash and v.createdAt > :since
            """)
    WindowStats countByIpSince(@Param("ipHash") String ipHash, @Param("since") LocalDateTime since);

    /** 같은 이메일+용도에서 아직 폐기·소비되지 않은 행을 모두 무효화 (재발송 시 이전 번호 무효, ACC-01-02) */
    @Modifying(flushAutomatically = true)
    @Query("""
            update EmailVerification v set v.consumedAt = :now
            where v.email = :email and v.purpose = :purpose and v.consumedAt is null
            """)
    int invalidateActive(@Param("email") String email, @Param("purpose") Purpose purpose,
                         @Param("now") LocalDateTime now);

    /** PK로 잠금 조회 (동시 요청으로 같은 번호·확인값을 두 번 쓰지 않게, ACC-01-03 J) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.requestId = :requestId")
    Optional<EmailVerification> findByIdForUpdate(@Param("requestId") Long requestId);

    /** 기간 안 발급 개수·가장 오래된 시각 */
    interface WindowStats {
        long getCount();

        LocalDateTime getOldest();
    }
}
