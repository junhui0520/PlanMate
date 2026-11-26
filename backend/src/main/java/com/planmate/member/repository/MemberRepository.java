package com.planmate.member.repository;

import com.planmate.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/** MEMBER (회원) 넣기·꺼내기 */
public interface MemberRepository extends JpaRepository<Member, Long> {
}
