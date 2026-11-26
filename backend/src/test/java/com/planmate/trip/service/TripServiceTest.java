package com.planmate.trip.service;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripCreateResponse;
import com.planmate.trip.entity.City;
import com.planmate.trip.entity.Trip;
import com.planmate.trip.entity.TripDay;
import com.planmate.trip.repository.TripDayRepository;
import com.planmate.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * TRP-01-01·03 여행 생성 규칙 확인. DB 없이 레포지토리·다른 서비스는 가짜(Mock)로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CITY_ID = 1L;
    private static final Long NEW_TRIP_ID = 12L;
    private static final String REQUEST_KEY = "3f8c2a9e-1b47-4c6d-9a0e-5d2f7b8c1e34";

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripDayRepository tripDayRepository;
    @Mock
    private CityService cityService;
    @Mock
    private DuplicateRequestChecker duplicateRequestChecker;

    @InjectMocks
    private TripService tripService;

    // 서울 도시 (테스트의 '오늘'도 서울 기준으로 맞춘다)
    private final City seoul = City.register("place-seoul", "서울", "Seoul", "KR", "Asia/Seoul",
            new BigDecimal("37.5665000"), new BigDecimal("126.9780000"));
    private final LocalDate today = DateTimeUtil.todayIn("Asia/Seoul");

    @Test
    void 사일짜리_여행이면_여행_1개와_1일차부터_4일차까지_만든다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate start = today.plusDays(10);

        TripCreateResponse response = tripService.create(request("도쿄 여행", start, start.plusDays(3), 4), MEMBER_ID);

        // 만든 여행 번호를 돌려준다
        assertThat(response.tripId()).isEqualTo(NEW_TRIP_ID);

        // 일차 4개: 1일차 = 시작일, 4일차 = 종료일
        List<TripDay> days = savedDays();
        assertThat(days).hasSize(4);
        assertThat(days.get(0).getDayNo()).isEqualTo(1);
        assertThat(days.get(0).getDayDate()).isEqualTo(start);
        assertThat(days.get(3).getDayNo()).isEqualTo(4);
        assertThat(days.get(3).getDayDate()).isEqualTo(start.plusDays(3));
        assertThat(days).allMatch(day -> day.getTripId().equals(NEW_TRIP_ID));

        // 처리 성공을 기록한다 (더블 클릭 방지)
        then(duplicateRequestChecker).should().record(REQUEST_KEY, MEMBER_ID, NEW_TRIP_ID);
    }

    @Test
    void 새_여행은_활성_상태이고_입력한_인원을_그대로_저장한다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate start = today.plusDays(10);

        tripService.create(request("도쿄 여행", start, start.plusDays(1), 4), MEMBER_ID);

        Trip saved = savedTrip();
        assertThat(saved.getStatus()).isEqualTo(Trip.Status.ACTIVE);
        assertThat(saved.getTravelMode()).isEqualTo(Trip.TravelMode.TRANSIT_WALK);
        assertThat(saved.getPeopleCount()).isEqualTo(4);
        assertThat(saved.getCreatedBy()).isEqualTo(MEMBER_ID);
    }

    @Test
    void 당일치기도_만들_수_있고_일차는_1개다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate start = today.plusDays(10);

        tripService.create(request("당일치기", start, start, 1), MEMBER_ID);

        assertThat(savedDays()).hasSize(1);
    }

    @Test
    void 칠일까지는_만들_수_있다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate start = today.plusDays(10);

        tripService.create(request("일주일 여행", start, start.plusDays(6), 2), MEMBER_ID);

        assertThat(savedDays()).hasSize(7);
    }

    @Test
    void 팔일이면_여행_기간_초과_오류() {
        givenNewRequestAndCity();
        LocalDate start = today.plusDays(10);

        assertErrorCode(request("긴 여행", start, start.plusDays(7), 2), ErrorCode.TRIP_PERIOD_TOO_LONG);
        then(tripRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void 종료일이_시작일보다_앞이면_날짜_순서_오류() {
        givenNewRequestAndCity();
        LocalDate start = today.plusDays(10);

        assertErrorCode(request("거꾸로 여행", start, start.minusDays(1), 2), ErrorCode.TRIP_END_DATE_BEFORE_START);
    }

    @Test
    void 시작일이_현지_어제면_시작일_범위_오류() {
        givenNewRequestAndCity();
        LocalDate yesterday = today.minusDays(1);

        assertErrorCode(request("지난 여행", yesterday, yesterday, 1), ErrorCode.TRIP_START_DATE_OUT_OF_RANGE);
    }

    @Test
    void 시작일이_현지_오늘이면_만들_수_있다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();

        tripService.create(request("오늘 출발", today, today, 1), MEMBER_ID);

        assertThat(savedTrip().getStartDate()).isEqualTo(today);
    }

    @Test
    void 시작일이_1년_후_다음날이면_시작일_범위_오류() {
        givenNewRequestAndCity();
        LocalDate tooFar = today.plusYears(1).plusDays(1);

        assertErrorCode(request("먼 여행", tooFar, tooFar, 1), ErrorCode.TRIP_START_DATE_OUT_OF_RANGE);
    }

    @Test
    void 시작일이_딱_1년_후면_만들_수_있다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate oneYearLater = today.plusYears(1);

        tripService.create(request("1년 후 여행", oneYearLater, oneYearLater, 1), MEMBER_ID);

        assertThat(savedTrip().getStartDate()).isEqualTo(oneYearLater);
    }

    @Test
    void 제목의_줄바꿈_탭은_공백_1칸으로_바꾸고_앞뒤_공백은_지운다() {
        givenNewRequestAndCity();
        givenTripSaveReturnsId();
        LocalDate start = today.plusDays(10);

        tripService.create(request("  도쿄\n\n\t여행  ", start, start, 1), MEMBER_ID);

        assertThat(savedTrip().getTitle()).isEqualTo("도쿄 여행");
    }

    @Test
    void 같은_요청_식별값을_이미_처리했으면_새로_만들지_않고_그때_만든_여행을_돌려준다() {
        // 이미 처리한 기록이 있다: 그때 만든 여행은 12번
        given(duplicateRequestChecker.findProcessed(REQUEST_KEY, MEMBER_ID))
                .willReturn(Optional.of(RequestLog.success(REQUEST_KEY, MEMBER_ID, NEW_TRIP_ID)));
        LocalDate start = today.plusDays(10);

        TripCreateResponse response = tripService.create(request("도쿄 여행", start, start, 1), MEMBER_ID);

        assertThat(response.tripId()).isEqualTo(NEW_TRIP_ID);
        then(tripRepository).should(never()).saveAndFlush(any());
        then(tripDayRepository).should(never()).saveAllAndFlush(anyList());
    }

    // ===== 도우미 =====

    // 처음 보는 요청 식별값 + 서울 도시
    private void givenNewRequestAndCity() {
        given(duplicateRequestChecker.findProcessed(REQUEST_KEY, MEMBER_ID)).willReturn(Optional.empty());
        given(cityService.getCity(CITY_ID)).willReturn(seoul);
    }

    // 여행을 저장하면 시퀀스가 12번을 준 것처럼 만든다
    private void givenTripSaveReturnsId() {
        given(tripRepository.saveAndFlush(any(Trip.class))).willAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            ReflectionTestUtils.setField(trip, "tripId", NEW_TRIP_ID);
            return trip;
        });
    }

    private TripCreateRequest request(String title, LocalDate start, LocalDate end, int peopleCount) {
        return new TripCreateRequest(REQUEST_KEY, title, CITY_ID, start, end, peopleCount);
    }

    // 서비스가 저장하려고 넘긴 여행 꺼내 보기
    private Trip savedTrip() {
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        then(tripRepository).should().saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // 서비스가 저장하려고 넘긴 일차 목록 꺼내 보기
    @SuppressWarnings("unchecked")
    private List<TripDay> savedDays() {
        ArgumentCaptor<List<TripDay>> captor = ArgumentCaptor.forClass(List.class);
        then(tripDayRepository).should().saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private void assertErrorCode(TripCreateRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> tripService.create(request, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}