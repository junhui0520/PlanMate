package com.planmate.common.security;

import com.planmate.admin.entity.MemberRestriction;
import com.planmate.admin.repository.MemberRestrictionRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** ACC-07-01 로그인·계정 상태·커뮤니티 이용 제한 판정 */
@ExtendWith(MockitoExtension.class)
class AccountStatusCheckerTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberRestrictionRepository memberRestrictionRepository;

    @InjectMocks
    private AccountStatusChecker checker;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 비로그인이면_LOGIN_REQUIRED_그리고_findLoginMemberId는_empty() {
        assertDenied(() -> checker.requireLoginMemberId(), ErrorCode.LOGIN_REQUIRED);
        assertThat(checker.findLoginMemberId()).isEmpty();
    }

    @Test
    void 세션의_회원이_DB에_없으면_LOGIN_REQUIRED() {
        login(1L, Member.SystemRole.USER);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertDenied(() -> checker.requireLoginMemberId(), ErrorCode.LOGIN_REQUIRED);
    }

    @Test
    void 탈퇴_처리_중이면_ACCOUNT_UNAVAILABLE_그리고_findLoginMemberId는_empty() {
        login(1L, Member.SystemRole.USER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, Member.SystemRole.USER, Member.Status.WITHDRAWN)));

        assertDenied(() -> checker.requireLoginMemberId(), ErrorCode.ACCOUNT_UNAVAILABLE);
        assertThat(checker.findLoginMemberId()).isEmpty();
    }

    @Test
    void 정상_회원이면_회원_ID를_돌려준다() {
        login(1L, Member.SystemRole.USER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, Member.SystemRole.USER, Member.Status.ACTIVE)));

        assertThat(checker.requireLoginMemberId()).isEqualTo(1L);
        assertThat(checker.findLoginMemberId()).contains(1L);
    }

    @Test
    void 제한_중이면_COMMUNITY_RESTRICTED_그리고_지금_시각으로_조회한다() {
        given(memberRestrictionRepository.existsActive(eq(1L), any(LocalDateTime.class))).willReturn(true);

        LocalDateTime before = LocalDateTime.now();
        assertDenied(() -> checker.requireCommunityWritable(1L), ErrorCode.COMMUNITY_RESTRICTED);

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberRestrictionRepository).existsActive(eq(1L), now.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void 제한_중이면_기능별_문구로_거부할_수_있다() {
        given(memberRestrictionRepository.existsActive(eq(1L), any(LocalDateTime.class))).willReturn(true);

        assertDenied(() -> checker.requireCommunityWritable(1L, ErrorCode.EDIT_PERMISSION_DENIED),
                ErrorCode.EDIT_PERMISSION_DENIED);
    }

    @Test
    void 제한이_없으면_통과() {
        given(memberRestrictionRepository.existsActive(eq(1L), any(LocalDateTime.class))).willReturn(false);

        assertThatCode(() -> checker.requireCommunityWritable(1L)).doesNotThrowAnyException();
    }

    @Test
    void 제한_종료_경계_1초전은_제한_정각은_해제_종료없음은_제한() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 15, 0, 0);
        LocalDateTime startsAt = now.minusDays(1);

        assertThat(restriction(startsAt, now.plusSeconds(1)).isActiveAt(now)).isTrue();   // 종료 1초 전
        assertThat(restriction(startsAt, now).isActiveAt(now)).isFalse();                 // 종료 시각 정각 = 해제
        assertThat(restriction(startsAt, null).isActiveAt(now)).isTrue();                 // 종료 시각 없음
        assertThat(restriction(now.plus(Duration.ofSeconds(1)), null).isActiveAt(now)).isFalse(); // 시작 전
    }

    // ===== 도우미 =====

    static void login(Long memberId, Member.SystemRole role) {
        SecurityContextHolder.getContext().setAuthentication(new LoginMember(memberId, role).toAuthentication());
    }

    static Member member(Long memberId, Member.SystemRole role, Member.Status status) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "memberId", memberId);
        ReflectionTestUtils.setField(member, "systemRole", role);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private static MemberRestriction restriction(LocalDateTime startsAt, LocalDateTime endsAt) {
        MemberRestriction restriction = BeanUtils.instantiateClass(MemberRestriction.class);
        ReflectionTestUtils.setField(restriction, "startsAt", startsAt);
        ReflectionTestUtils.setField(restriction, "endsAt", endsAt);
        return restriction;
    }

    static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
