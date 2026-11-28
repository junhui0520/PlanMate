package com.planmate.auth.dto;

import com.planmate.member.entity.Member;

/**
 * 로그인한 회원 요약 (ACC-02-01 로그인 결과, GET /api/auth/me).
 * systemRole은 "USER" 또는 "ADMIN". 화면은 관리자 메뉴 표시에만 쓰고, 실제 권한 판정은 서버가 한다(ACC-07-03).
 */
public record LoginResponse(Long memberId, String nickname, String systemRole) {

    public static LoginResponse from(Member member) {
        return new LoginResponse(member.getMemberId(), member.getNickname(), member.getSystemRole().name());
    }
}
