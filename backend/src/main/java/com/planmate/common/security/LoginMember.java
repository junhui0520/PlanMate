package com.planmate.common.security;

import com.planmate.member.entity.Member;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.List;

/**
 * 로그인 주체 (ACC-07-01). 서버 세션(Spring Session JDBC)에 직렬화되어 저장된다.
 * getName()이 회원 번호라서 SPRING_SESSION.PRINCIPAL_NAME에 MEMBER_ID가 들어간다
 * (비밀번호 변경·재설정 때 그 회원의 모든 세션 종료용, ACC-04).
 * 비밀번호·토큰 같은 민감정보는 담지 않는다.
 */
public record LoginMember(Long memberId, Member.SystemRole systemRole) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }

    public boolean isAdmin() {
        return systemRole == Member.SystemRole.ADMIN;
    }

    /** 권한 목록: ROLE_ADMIN 또는 ROLE_USER */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"));
    }

    public static LoginMember from(Member member) {
        return new LoginMember(member.getMemberId(), member.getSystemRole());
    }

    /** 인증된 Authentication. pjh/login의 LoginService가 SecurityContext에 넣는다 (ACC-02-01) */
    public Authentication toAuthentication() {
        return UsernamePasswordAuthenticationToken.authenticated(this, null, authorities());
    }
}
