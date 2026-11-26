package com.planmate.trip.service;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripCreateResponse;
import com.planmate.trip.dto.TripCreationCheckResponse;
import com.planmate.trip.entity.City;
import com.planmate.trip.entity.Trip;
import com.planmate.trip.entity.TripDay;
import com.planmate.trip.repository.TripDayRepository;
import com.planmate.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 여행 생성·목록·검색·상세·정보 수정 (TRP-01~03).
 * 지금은 TRP-01 여행 생성만 있다. D 파트 공개 여행 복제(PUB-07)도 create를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class TripService {

    // TRP-01-01 참여 중인 활성 여행 최대 개수 (구독 기능 전까지 3)
    static final int MAX_ACTIVE_TRIPS = 3;
    // TRP-01-01 여행 기간 최대 일수 (11/13 결정)
    static final int MAX_TRIP_DAYS = 7;

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final CityService cityService;
    private final DuplicateRequestChecker duplicateRequestChecker;

    /**
     * TRP-01-01 '새 여행 만들기'를 누르면 생성 화면을 열기 전에 개수를 확인한다.
     * 3개 이상이면 TRIP_LIMIT_EXCEEDED(409).
     */
    @Transactional(readOnly = true)
    public TripCreationCheckResponse checkCreatable(Long memberId) {
        int activeCount = countActiveTrips(memberId);
        if (activeCount >= MAX_ACTIVE_TRIPS) {
            throw new BusinessException(ErrorCode.TRIP_LIMIT_EXCEEDED);
        }
        return new TripCreationCheckResponse(activeCount, MAX_ACTIVE_TRIPS);
    }

    /**
     * TRP-01-03 여행 생성 저장. 모든 단계가 한 트랜잭션이라 중간에 실패하면 전부 취소된다.
     */
    @Transactional
    public TripCreateResponse create(TripCreateRequest req, Long memberId) {
        // 1) 같은 요청을 이미 처리했으면 새로 만들지 않고 그때 만든 여행을 돌려준다 (더블 클릭 방지)
        Optional<RequestLog> done = duplicateRequestChecker.findProcessed(req.requestKey(), memberId);
        if (done.isPresent()) {
            return new TripCreateResponse(done.get().getTripId());
        }

        // 2) 입력값 검사 (칸 하나짜리 규칙은 DTO @Valid가 이미 검사함)
        String title = normalizeTitle(req.title());
        City city = cityService.getCity(req.cityId());
        validatePeriod(req.startDate(), req.endDate(), city.getTimezone());

        // 3) 생성 개수 확인: 활성 여행이 이미 3개면 만들지 않는다
        // TODO(A에 요청): 동시에 두 번 요청해도 4번째가 생기지 않게 회원 단위로 잠그는 방법
        //  (예: ParticipantService에서 내 참여 행을 잠그고 세기). 지금은 개수 확인만 한다
        if (countActiveTrips(memberId) >= MAX_ACTIVE_TRIPS) {
            throw new BusinessException(ErrorCode.TRIP_LIMIT_EXCEEDED);
        }

        // 4) 여행 + 일차 저장
        Trip trip = saveTripWithDays(title, city.getCityId(), req.startDate(), req.endDate(),
                req.peopleCount(), memberId);

        // 5) 만든 사람을 소유자로 등록
        // TODO(A에 요청): participantService.addOwner(trip.getTripId(), memberId)

        // 6) 처리 성공 기록 (같은 트랜잭션 안에서 불러야 함)
        duplicateRequestChecker.record(req.requestKey(), memberId, trip.getTripId());
        return new TripCreateResponse(trip.getTripId());
    }

    /**
     * 여행 기본 정보 (D 파트 경비가 사용). 인원·시작일·종료일·여행 도시의 나라 코드.
     * 권한 검사는 부르는 쪽(D)에서 한다. 여행이 없으면 Optional.empty()
     */
    @Transactional(readOnly = true)
    public Optional<TripBasicInfo> findBasicInfo(Long tripId) {
        return tripRepository.findById(tripId)
                .map(trip -> new TripBasicInfo(
                        trip.getPeopleCount(),
                        trip.getStartDate(),
                        trip.getEndDate(),
                        cityService.getCity(trip.getCityId()).getCountryCode().trim())); // CHAR(2)
    }

    /*
     * TRP-01-01 제목 정리: 제어문자(줄바꿈·탭 등) 묶음은 공백 1칸으로 바꾸고 앞뒤 공백을 지운다.
     * 예) "  도쿄\n\n여행 " -> "도쿄 여행"
     */
    private String normalizeTitle(String rawTitle) {
        String title = rawTitle.replaceAll("\\p{Cc}+", " ").strip();
        // 보이지 않는 제어문자만 있던 제목은 지우고 나면 비어 버린다
        if (title.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return title;
    }

    /*
     * TRP-01-01 기간 검사.
     * - 종료일 >= 시작일 (당일치기 허용)
     * - 일수(종료일 - 시작일 + 1) 1~7일
     * - 시작일: 여행지 현지 오늘 ~ 1년 후(다음 해 같은 날짜)
     */
    private void validatePeriod(LocalDate startDate, LocalDate endDate, String cityTimezone) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.TRIP_END_DATE_BEFORE_START);
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_TRIP_DAYS) {
            throw new BusinessException(ErrorCode.TRIP_PERIOD_TOO_LONG);
        }
        LocalDate localToday = DateTimeUtil.todayIn(cityTimezone);
        if (startDate.isBefore(localToday) || startDate.isAfter(localToday.plusYears(1))) {
            throw new BusinessException(ErrorCode.TRIP_START_DATE_OUT_OF_RANGE);
        }
    }

    /*
     * TRP-01-01 내가 참여 중인 활성 여행 수 (소유·편집·조회 모두, 휴지통·참여 끝난 여행 제외).
     * TRIP_MEMBER는 A 테이블이라 내 여행 ID 목록은 A의 ParticipantService에서 받는다.
     */
    private int countActiveTrips(Long memberId) {
        // TODO(A에 요청): List<Long> myTripIds = participantService.findTripIdsByMember(memberId);
        List<Long> myTripIds = List.of();
        if (myTripIds.isEmpty()) {
            return 0;
        }
        return (int) tripRepository.countByTripIdInAndStatus(myTripIds, Trip.Status.ACTIVE);
    }

    /*
     * TRP-01-03 TRIP 1행 + 일수만큼 TRIP_DAY 저장.
     * 예) 12/20~12/23 -> 1일차 12/20, 2일차 12/21, 3일차 12/22, 4일차 12/23
     * DB 저장이 실패하면 기능정의서 문구(TRIP_CREATE_FAILED)로 바꾼다.
     */
    private Trip saveTripWithDays(String title, Long cityId, LocalDate startDate, LocalDate endDate,
                                  Integer peopleCount, Long memberId) {
        try {
            Trip trip = tripRepository.saveAndFlush(
                    Trip.create(title, cityId, startDate, endDate, peopleCount, memberId));

            List<TripDay> days = new ArrayList<>();
            int dayNo = 1;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                days.add(TripDay.of(trip.getTripId(), dayNo, date));
                dayNo++;
            }
            tripDayRepository.saveAllAndFlush(days);
            return trip;
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
        }
    }
}