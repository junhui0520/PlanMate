package com.planmate.member.repository;

import com.planmate.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** MEMBER (회원) 넣기·꺼내기 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일 계정이 이미 있는지 (ACC-01-03·04). 소셜 회원은 EMAIL_NORMALIZED가 비어 있어 걸리지 않는다 */
    boolean existsByEmailNormalized(String emailNormalized);

    /** 이메일 계정 조회 (ACC-02-01 로그인). PROVIDER·비밀번호 해시 검사는 호출하는 쪽에서 한다 */
    Optional<Member> findByEmailNormalized(String emailNormalized);
}
