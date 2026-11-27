package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.CloneRequest;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripCreateResponse;
import com.planmate.trip.dto.TripCreationCheckResponse;
import com.planmate.trip.service.TripService;

/**
 * PUB-07-01·02 내 여행으로 복제 서버 확인. DB·A 체커·B 여행 서비스·같은 요청 확인은 가짜(Mock)로 둔다.
 * B의 여행 기본 정보에 아직 도시 ID가 없어서, 도시 ID가 생긴 뒤의 흐름은 findSourceCityId를 바꿔 끼워 확인한다.
 */
class TripCloneServiceTest {

    private static final Long ME = 1L;
    private static final Long POST = 10L;
    private static final Long SOURCE_TRIP = 50L;
    private static final String KEY = "3f8c2a9e-1b47-4c6d-9a0e-5d2f7b8c1e34";

    private PublicPostRepository posts;
    private PublicPostQueryService query;
    private AccountStatusChecker account;
    private DuplicateRequestChecker duplicate;
    private TripService tripService;
    private PublicPost post;
    private TripCloneService service; // B에 도시 ID가 없는 지금 상태
    private TripCloneService serviceWithCity; // B가 도시 ID를 준 뒤 (도시 3번)

    @BeforeEach
    void setUp() {
        posts = mock(PublicPostRepository.class);
        query = mock(PublicPostQueryService.class);
        account = mock(AccountStatusChecker.class);
        duplicate = mock(DuplicateRequestChecker.class);
        tripService = mock(TripService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        post = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(post, "postId", POST);
        ReflectionTestUtils.setField(post, "tripId", SOURCE_TRIP);
        ReflectionTestUtils.setField(post, "postTitle", "도쿄 3박 4일 가족여행");
        when(posts.findById(POST)).thenReturn(Optional.of(post));
        when(query.isVisible(post)).thenReturn(true);
        // 원래 여행: 도쿄 3박 4일 (11/20~11/23)
        when(tripService.findBasicInfo(SOURCE_TRIP)).thenReturn(Optional.of(
                new TripBasicInfo(4, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23), "JP")));
        when(tripService.create(any(TripCreateRequest.class), eq(ME))).thenReturn(new TripCreateResponse(77L));
        service = new TripCloneService(posts, query, account, duplicate, tripService);
        serviceWithCity = new TripCloneService(posts, query, account, duplicate, tripService) {
            @Override
            Optional<Long> findSourceCityId(Long sourceTripId) {
                return Optional.of(3L);
            }
        };
    }

    private static CloneRequest clone(LocalDate start, Integer people) {
        return new CloneRequest(KEY, start, people);
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // ---- PUB-07-01 복제 창 ----

    @Test
    void 복제_창을_열면_내_여행_n_3() {
        when(tripService.checkCreatable(ME)).thenReturn(new TripCreationCheckResponse(2, 3));

        var r = service.checkClonable(POST);

        assertEquals(2, r.activeTripCount());
        assertEquals(3, r.maxTripCount());
    }

    @Test
    void 이미_3개면_B_문구로_거절() {
        when(tripService.checkCreatable(ME)).thenThrow(new BusinessException(ErrorCode.TRIP_LIMIT_EXCEEDED));

        assertError(ErrorCode.TRIP_LIMIT_EXCEEDED, () -> service.checkClonable(POST));
    }

    @Test
    void 이용_제한_회원은_복제_창도_실행도_불가() {
        doThrow(new BusinessException(ErrorCode.CLONE_RESTRICTED)).when(account).requireCommunityWritable(ME, ErrorCode.CLONE_RESTRICTED);

        assertError(ErrorCode.CLONE_RESTRICTED, () -> service.checkClonable(POST));
        assertError(ErrorCode.CLONE_RESTRICTED, () -> serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 2)));
        verify(tripService, never()).create(any(), anyLong());
        assertEquals("이용 제한 중에는 복제할 수 없어요.", ErrorCode.CLONE_RESTRICTED.getMessage());
    }

    @Test
    void 볼_수_없는_게시물은_복제할_수_없다() {
        when(query.isVisible(post)).thenReturn(false);

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.checkClonable(POST));
        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 2)));
        verify(tripService, never()).create(any(), anyLong());
    }

    // ---- PUB-07-02 복제 실행 ----

    @Test
    void 종료일은_시작일_더하기_공개본_일수_빼기_1() {
        TripBasicInfo fourDays = new TripBasicInfo(4, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23), "JP");
        TripBasicInfo dayTrip = new TripBasicInfo(2, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), "KR");

        assertEquals(LocalDate.of(2026, 12, 4), TripCloneService.endDate(LocalDate.of(2026, 12, 1), fourDays));
        assertEquals(LocalDate.of(2026, 12, 31), TripCloneService.endDate(LocalDate.of(2026, 12, 31), dayTrip));
        assertEquals(LocalDate.of(2027, 1, 3), TripCloneService.endDate(LocalDate.of(2026, 12, 31), fourDays)); // 해 넘김
    }

    @Test
    void B의_여행_생성을_공개용_제목_공개본_도시_새_날짜_인원으로_부른다() {
        var r = serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 2));

        ArgumentCaptor<TripCreateRequest> captor = ArgumentCaptor.forClass(TripCreateRequest.class);
        verify(tripService).create(captor.capture(), eq(ME));
        TripCreateRequest sent = captor.getValue();
        assertEquals("도쿄 3박 4일 가족여행", sent.title()); // 공개용 제목 그대로
        assertEquals(3L, sent.cityId()); // 공개본 도시 그대로
        assertEquals(LocalDate.of(2026, 12, 1), sent.startDate());
        assertEquals(LocalDate.of(2026, 12, 4), sent.endDate());
        assertEquals(2, sent.peopleCount());
        assertEquals(KEY, sent.requestKey()); // 같은 요청 식별값을 B에도 넘김
        assertEquals(77L, r.tripId());
    }

    @Test
    void 같은_요청을_다시_보내면_이전에_만든_여행을_그대로_돌려준다() {
        when(duplicate.findProcessed(KEY, ME)).thenReturn(Optional.of(RequestLog.success(KEY, ME, 77L)));

        assertEquals(77L, serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 2)).tripId());
        verify(tripService, never()).create(any(), anyLong());
    }

    @Test
    void 날짜_범위_3개_제한은_B가_거절하면_그대로_나간다() {
        when(tripService.create(any(TripCreateRequest.class), eq(ME)))
                .thenThrow(new BusinessException(ErrorCode.TRIP_START_DATE_OUT_OF_RANGE));

        assertError(ErrorCode.TRIP_START_DATE_OUT_OF_RANGE, () -> serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2020, 1, 1), 2)));
    }

    @Test
    void 인원이_1_20_밖이거나_시작일이_없으면_INVALID_INPUT() {
        assertError(ErrorCode.INVALID_INPUT, () -> serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 0)));
        assertError(ErrorCode.INVALID_INPUT, () -> serviceWithCity.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 21)));
        assertError(ErrorCode.INVALID_INPUT, () -> serviceWithCity.cloneToMyTrip(POST, clone(null, 2)));
        verify(tripService, never()).create(any(), anyLong());
    }

    @Test
    void B에_원래_여행_도시_ID_조회가_없으면_여행을_만들지_않고_멈춘다() {
        assertThrows(IllegalStateException.class, () -> service.cloneToMyTrip(POST, clone(LocalDate.of(2026, 12, 1), 2)));
        verify(tripService, never()).create(any(), anyLong());
    }
}
