package com.planmate.common.security;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.participant.entity.TripMember;
import com.planmate.participant.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * 여행·개인 자료 접근 검사 (ACC-07-02). 다른 파트는 이 클래스를 주입해서 쓴다.
 * - 로그인 확인은 AccountStatusChecker.requireLoginMemberId()로 먼저 하고, 받은 memberId를 넘긴다.
 * - 저장 직전에 다시 부른다(화면을 연 뒤 역할이 바뀔 수 있음).
 * - 권한을 닉네임으로 판정하지 않는다. TRIP_MEMBER.ROLE만 본다.
 * - 휴지통(TRASHED) 여행은 참여자 접근을 막는다(소유자 복원·영구 삭제는 requireOwnerOfTrashed).
 * - 여행 상태는 B의 TripStatusReader 구현으로 조회한다. 구현이 없어도 앱 시작은 막지 않고, 판정을 부를 때 오류를 낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripAccessChecker {

    private final ObjectProvider<TripStatusReader> tripStatusReaderProvider;
    private final TripMemberRepository tripMemberRepository;

    /** 활성 여행의 참여자인지. 여행 없음·휴지통·비참여 -> RESOURCE_NOT_ACCESSIBLE. 통과하면 역할 */
    public TripMember.Role requireParticipant(Long tripId, Long memberId) {
        TripStatus status = findStatus(tripId)
                .orElseThrow(() -> deny(ErrorCode.RESOURCE_NOT_ACCESSIBLE, tripId, memberId));
        if (status != TripStatus.ACTIVE) {
            throw deny(ErrorCode.RESOURCE_NOT_ACCESSIBLE, tripId, memberId);
        }
        return findRole(tripId, memberId);
    }

    /** 편집 가능(OWNER·EDITOR)인지. 조회 참여자(VIEWER) -> EDIT_PERMISSION_DENIED */
    public void requireEditor(Long tripId, Long memberId) {
        requireEditor(tripId, memberId, ErrorCode.EDIT_PERMISSION_DENIED);
    }

    /** 편집 가능(OWNER·EDITOR)인지. 조회 참여자(VIEWER)면 기능별 문구(denied) */
    public void requireEditor(Long tripId, Long memberId, ErrorCode denied) {
        if (requireParticipant(tripId, memberId) == TripMember.Role.VIEWER) {
            throw deny(denied, tripId, memberId);
        }
    }

    /** 소유자인지. 아니면 기능별 문구(denied). 예: '소유자만 초대할 수 있어요.' */
    public void requireOwner(Long tripId, Long memberId, ErrorCode denied) {
        if (requireParticipant(tripId, memberId) != TripMember.Role.OWNER) {
            throw deny(denied, tripId, memberId);
        }
    }

    /** 휴지통 여행의 소유자인지 (B TRP-05 복원·영구 삭제용). 아니면 RESOURCE_NOT_ACCESSIBLE */
    public void requireOwnerOfTrashed(Long tripId, Long memberId) {
        TripStatus status = findStatus(tripId).orElse(null);
        if (status != TripStatus.TRASHED || findRole(tripId, memberId) != TripMember.Role.OWNER) {
            throw deny(ErrorCode.RESOURCE_NOT_ACCESSIBLE, tripId, memberId);
        }
    }

    /** 본인 자료인지 (집 주소·개인 출발 등). 다르면 RESOURCE_NOT_ACCESSIBLE */
    public void requireSelf(Long ownerMemberId, Long memberId) {
        if (memberId == null || !Objects.equals(ownerMemberId, memberId)) {
            throw deny(ErrorCode.RESOURCE_NOT_ACCESSIBLE, null, memberId);
        }
    }

    private TripMember.Role findRole(Long tripId, Long memberId) {
        return tripMemberRepository.findByTripIdAndMemberId(tripId, memberId)
                .map(TripMember::getRole)
                .orElseThrow(() -> deny(ErrorCode.RESOURCE_NOT_ACCESSIBLE, tripId, memberId));
    }

    private Optional<TripStatus> findStatus(Long tripId) {
        TripStatusReader reader = tripStatusReaderProvider.getIfAvailable();
        if (reader == null) {
            throw new IllegalStateException("TripStatusReader 구현 없음 — B 파트");
        }
        return reader.findStatus(tripId);
    }

    private static BusinessException deny(ErrorCode code, Long tripId, Long memberId) {
        log.info("접근 거부: code={}, path={}, memberId={}, tripId={}",
                code.name(), SecurityErrorHandler.currentRequestPath(), memberId, tripId);
        return new BusinessException(code);
    }
}
