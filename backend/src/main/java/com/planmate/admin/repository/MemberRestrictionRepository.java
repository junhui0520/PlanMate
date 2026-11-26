package com.planmate.admin.repository;

import com.planmate.admin.entity.MemberRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** MEMBER_RESTRICTION (커뮤니티 이용 제한) 넣기·꺼내기 */
public interface MemberRestrictionRepository extends JpaRepository<MemberRestriction, Long> {

    /**
     * 지금 제한 중인지 (ACC-07-01).
     * 종료 시각 이상이면 해제로 판정한다. 해제 배치 작업을 기다리지 않는다. 종료 시각이 없으면 계속 제한.
     */
    @Query("""
            select case when count(r) > 0 then true else false end
            from MemberRestriction r
            where r.memberId = :memberId
              and r.startsAt <= :now
              and (r.endsAt is null or r.endsAt > :now)
            """)
    boolean existsActive(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
