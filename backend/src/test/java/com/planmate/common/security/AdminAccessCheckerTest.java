package com.planmate.common.security;

import com.planmate.admin.repository.MemberRestrictionRepository;
import com.planmate.common.exception.ErrorCode;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

import static com.planmate.common.security.AccountStatusCheckerTest.assertDenied;
import static com.planmate.common.security.AccountStatusCheckerTest.login;
import static com.planmate.common.security.AccountStatusCheckerTest.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

/** ACC-07-03 관리자 검사·운영 조치 전 본인 재확인 */
@ExtendWith(MockitoExtension.class)
class AdminAccessCheckerTest {

    private static final Long ADMIN_ID = 100L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberRestrictionRepository memberRestrictionRepository;

    private AdminAccessChecker checker;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        checker = new AdminAccessChecker(new AccountStatusChecker(memberRepository, memberRestrictionRepository));
        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/members/1/restrictions");
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void 비로그인이면_LOGIN_REQUIRED() {
        assertDenied(() -> checker.requireAdmin(), ErrorCode.LOGIN_REQUIRED);
    }

    @Test
    void 일반_회원이_관리자_기능을_부르면_ADMIN_ONLY() {
        login(1L, Member.SystemRole.USER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, Member.SystemRole.USER, Member.Status.ACTIVE)));

        assertDenied(() -> checker.requireAdmin(), ErrorCode.ADMIN_ONLY);
    }

    @Test
    void 세션_권한이_ADMIN이어도_DB가_USER면_ADMIN_ONLY() {
        login(1L, Member.SystemRole.ADMIN);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, Member.SystemRole.USER, Member.Status.ACTIVE)));

        assertDenied(() -> checker.requireAdmin(), ErrorCode.ADMIN_ONLY);
    }

    @Test
    void 관리자면_관리자_ID를_돌려준다() {
        login(ADMIN_ID, Member.SystemRole.ADMIN);
        given(memberRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(member(ADMIN_ID, Member.SystemRole.ADMIN, Member.Status.ACTIVE)));

        assertThat(checker.requireAdmin()).isEqualTo(ADMIN_ID);
    }

    @Test
    void 재확인_4분_전이면_통과() {
        reauthAgo(ADMIN_ID, Duration.ofMinutes(4));
        assertThatCode(() -> checker.requireRecentReauth(ADMIN_ID)).doesNotThrowAnyException();
    }

    @Test
    void 재확인_6분_전이면_ADMIN_REAUTH_REQUIRED() {
        reauthAgo(ADMIN_ID, Duration.ofMinutes(6));
        assertDenied(() -> checker.requireRecentReauth(ADMIN_ID), ErrorCode.ADMIN_REAUTH_REQUIRED);
    }

    @Test
    void 다른_관리자가_재확인했으면_ADMIN_REAUTH_REQUIRED() {
        reauthAgo(999L, Duration.ofMinutes(1));
        assertDenied(() -> checker.requireRecentReauth(ADMIN_ID), ErrorCode.ADMIN_REAUTH_REQUIRED);
    }

    @Test
    void 재확인_기록이_없으면_ADMIN_REAUTH_REQUIRED() {
        assertDenied(() -> checker.requireRecentReauth(ADMIN_ID), ErrorCode.ADMIN_REAUTH_REQUIRED);
    }

    @Test
    void markReauthenticated로_기록하면_바로_통과() {
        checker.markReauthenticated(ADMIN_ID);

        assertThat(session.getAttribute(AdminAccessChecker.REAUTH_ID_ATTRIBUTE)).isEqualTo(ADMIN_ID);
        assertThatCode(() -> checker.requireRecentReauth(ADMIN_ID)).doesNotThrowAnyException();
    }

    private void reauthAgo(Long adminId, Duration ago) {
        session.setAttribute(AdminAccessChecker.REAUTH_ID_ATTRIBUTE, adminId);
        session.setAttribute(AdminAccessChecker.REAUTH_AT_ATTRIBUTE, System.currentTimeMillis() - ago.toMillis());
    }
}
