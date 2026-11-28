package com.planmate.auth.repository;

import com.planmate.auth.entity.LoginAttempt;
import com.planmate.auth.entity.LoginAttempt.Result;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** LOGIN_ATTEMPT (로그인 시도 기록) 넣기·꺼내기 (ACC-02-01, A-P06) */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /** 이메일별 마지막 결과 시각 (result에 SUCCESS를 넣어 마지막 성공 시각을 구한다). 없으면 null */
    @Query("""
            select max(a.attemptedAt) from LoginAttempt a
            where a.emailNormalized = :email and a.result = :result
            """)
    LocalDateTime findLastAttemptedAt(@Param("email") String email, @Param("result") Result result);

    /** 이메일별 since 이후 결과 시각 (최신순). 계정 제한 판정에 최근 FAIL 5개를 쓴다. 잠금 조회 아님 */
    @Query("""
            select a.attemptedAt from LoginAttempt a
            where a.emailNormalized = :email and a.result = :result and a.attemptedAt > :since
            order by a.attemptedAt desc
            """)
    List<LocalDateTime> findRecentAttemptedAt(@Param("email") String email, @Param("result") Result result,
                                              @Param("since") LocalDateTime since, Limit limit);

    /** IP 해시 기준 since 이후 시도 수 (SUCCESS·FAIL 모두) */
    long countByIpHashAndAttemptedAtAfter(String ipHash, LocalDateTime since);
}
