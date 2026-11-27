package com.planmate.community.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.CloneCheckResponse;
import com.planmate.community.dto.CloneRequest;
import com.planmate.community.dto.CloneResponse;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripCreationCheckResponse;
import com.planmate.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * 공개 일정의 내 여행 복제 (PUB-07). D는 복제용 데이터만 준비하고, 새 여행은 B의 공통 여행 생성(TRP-01-03)으로 만든다.
 * - 복사: 일차별 장소·방문 순서·방문시간·체류시간·기본 이동수단(장소는 공동 후보로도), 제목은 공개용 제목
 * - 복사 안 함: 날짜·경비·메모·참여자·좋아요·댓글·고정·필수 표시·체크리스트·AI 조건
 * - 복제 원본 게시물 ID는 저장하지 않는다(11/23 결정, PUB-07-02·03). 복제 수도 세지 않는다.
 * 권한: 로그인한 정상 회원(이용 제한 회원 불가).
 */
@Service
@RequiredArgsConstructor
public class TripCloneService {

    static final int PEOPLE_MIN = 1; // TRP-01-01 여행 인원 1~20
    static final int PEOPLE_MAX = 20;

    private final PublicPostRepository publicPostRepository;
    private final PublicPostQueryService publicPostQueryService;
    private final AccountStatusChecker accountStatusChecker;
    private final DuplicateRequestChecker duplicateRequestChecker;
    private final TripService tripService; // B: 여행 생성·생성 가능 확인·여행 기본 정보

    /**
     * PUB-07-01 복제 창을 열 때: 게시물이 지금 보이는지 + "내 여행 n/3".
     * 이미 3개면 B가 TRIP_LIMIT_EXCEEDED(409, TRP-01-01 문구)로 거절한다.
     */
    @Transactional(readOnly = true)
    public CloneCheckResponse checkClonable(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.CLONE_RESTRICTED);
        visiblePost(postId);
        TripCreationCheckResponse check = tripService.checkCreatable(memberId);
        return new CloneCheckResponse(check.activeTripCount(), check.maxTripCount());
    }

    /**
     * PUB-07-02 복제 실행. 모든 단계가 한 트랜잭션이라 중간에 실패하면 새 여행도 만들어지지 않는다.
     * 같은 요청 식별값은 한 번만 처리(두 번 눌러도 여행 1개) — 같은 값을 B의 여행 생성에도 넘긴다.
     * 누르는 시점에 게시물이 보이는지(비공개·관리자 숨김·원래 여행 휴지통) 서버가 다시 확인한다.
     * 날짜 범위(여행지 오늘 ~ 1년 후)·3개 제한은 B의 여행 생성이 검사한다(B 문구 그대로).
     */
    @Transactional
    public CloneResponse cloneToMyTrip(Long postId, CloneRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.CLONE_RESTRICTED);
        Optional<RequestLog> done = duplicateRequestChecker.findProcessed(req.requestKey(), memberId);
        if (done.isPresent()) {
            return new CloneResponse(done.get().getTripId());
        }
        checkInput(req);
        PublicPost post = visiblePost(postId);

        // 복제용 데이터: 제목·도시는 공개본 그대로, 종료일 = 시작일 + 공개본 일수 - 1
        TripBasicInfo source = tripService.findBasicInfo(post.getTripId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
        LocalDate endDate = endDate(req.startDate(), source);
        Long cityId = findSourceCityId(post.getTripId())
                .orElseThrow(() -> new IllegalStateException("원래 여행 도시 ID 조회 없음 — B 파트"));

        // B의 공통 여행 생성: 새 비공개 여행, 복제한 회원이 소유자
        // TODO(B에 요청): 기본 이동수단(TRAVEL_MODE)도 공개본 그대로 넘길 칸 (TripCreateRequest에 없음)
        Long newTripId = tripService.create(new TripCreateRequest(req.requestKey(), post.getPostTitle(), cityId,
                req.startDate(), endDate, req.peopleCount()), memberId).tripId();

        // TODO(B에 요청): 원래 여행의 저장된 시간표(공개 허용 값: N일차·순서·방문시간·체류시간·장소)를 읽어
        //   1) 공개본 장소를 새 여행의 공동 후보로 추가(같은 장소는 후보 1개, PLC-05-01 CandidateService)
        //   2) 각 방문을 같은 N일차·순서·시간·체류시간으로 시간표에 배치(SCH-06-01 ScheduleSaveService)
        // TODO(C에 요청): 새 여행 모든 구간 경로 조회 요청(RTE-02-01 RouteService)
        return new CloneResponse(newTripId);
    }

    /**
     * 원래 여행의 도시 ID. B의 여행 기본 정보(findBasicInfo)에 아직 도시 ID가 없다.
     * TODO(B에 요청): TripBasicInfo에 cityId(·travelMode)를 더해 주면 여기서 읽는다. 그 전까지는 비어 있어 복제가 멈춘다.
     */
    Optional<Long> findSourceCityId(Long sourceTripId) {
        return Optional.empty();
    }

    // PUB-07-01 종료일 = 시작일 + 공개본 일수 - 1 (직접 고를 수 없음)
    static LocalDate endDate(LocalDate startDate, TripBasicInfo source) {
        long days = ChronoUnit.DAYS.between(source.startDate(), source.endDate()) + 1;
        return startDate.plusDays(days - 1);
    }

    // 화면에서 막는 값도 다시 검사 (B의 여행 생성은 화면 요청이 아니라 @Valid를 거치지 않음)
    private static void checkInput(CloneRequest req) {
        if (req.startDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (req.peopleCount() == null || req.peopleCount() < PEOPLE_MIN || req.peopleCount() > PEOPLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 공개 게시물(공개·관리자 숨김 아님·원래 여행 휴지통 아님)만 복제할 수 있다
    private PublicPost visiblePost(Long postId) {
        return publicPostRepository.findById(postId)
                .filter(publicPostQueryService::isVisible)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
    }
}
