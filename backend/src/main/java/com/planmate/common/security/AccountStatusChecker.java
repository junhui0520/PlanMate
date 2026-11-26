package com.planmate.common.security;

import com.planmate.admin.repository.MemberRestrictionRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 로그인·계정 상태 판정 (ACC-07-01). 다른 파트는 이 클래스를 주입해서 쓴다.
 * - 관리자 여부는 DB의 SYSTEM_ROLE로만 정한다. 요청에 ADMIN을 넣어도 승격되지 않는다.
 * - 이용 제한은 커뮤니티 쓰기(PUB에서 열람 외)에만 적용한다. 여행·일정·AI·경비·보관함·문의는 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusChecker {

    private final MemberRepository memberRepository;
    private final MemberRestrictionRepository memberRestrictionRepository;

    /**
     * 로그인한 정상 회원의 ID.
     * 로그인 안 됨·회원 없음 -> LOGIN_REQUIRED, 탈퇴 처리 중(WITHDRAWN) -> ACCOUNT_UNAVAILABLE
     */
    public Long requireLoginMemberId() {
        return requireActiveMember().getMemberId();
    }

    /** 비회원도 쓰는 화면용. 로그인 안 했거나 탈퇴 처리 중이면 empty */
    public Optional<Long> findLoginMemberId() {
        return currentPrincipalId()
                .flatMap(memberRepository::findById)
                .filter(Member::isActive)
                .map(Member::getMemberId);
    }

    /** 커뮤니티 쓰기 가능 여부. 제한 중이면 COMMUNITY_RESTRICTED */
    public void requireCommunityWritable(Long memberId) {
        requireCommunityWritable(memberId, ErrorCode.COMMUNITY_RESTRICTED);
    }

    /**
     * 커뮤니티 쓰기 가능 여부. 제한 중이면 기능별 문구(denied)로 거부한다.
     * 예: PUB-01 '이용 제한 중에는 게시할 수 없어요.'
     * 종료 시각이 지나면 해제 작업을 기다리지 않고 바로 해제로 판정한다.
     */
    public void requireCommunityWritable(Long memberId, ErrorCode denied) {
        if (memberRestrictionRepository.existsActive(memberId, DateTimeUtil.now())) {
            throw deny(denied, memberId);
        }
    }

    // 로그인한 정상 회원 (AdminAccessChecker가 회원을 두 번 조회하지 않게 같은 패키지에서 사용)
    Member requireActiveMember() {
        Long memberId = currentPrincipalId().orElseThrow(() -> deny(ErrorCode.LOGIN_REQUIRED, null));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> deny(ErrorCode.LOGIN_REQUIRED, memberId));
        if (!member.isActive()) {
            throw deny(ErrorCode.ACCOUNT_UNAVAILABLE, memberId);
        }
        return member;
    }

    private static Optional<Long> currentPrincipalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginMember member) {
            return Optional.ofNullable(member.memberId());
        }
        return Optional.empty();
    }

    private static BusinessException deny(ErrorCode code, Long memberId) {
        log.info("접근 거부: code={}, path={}, memberId={}", code.name(), SecurityErrorHandler.currentRequestPath(), memberId);
        return new BusinessException(code);
    }
}
