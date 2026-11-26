package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripStatusReader;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.community.dto.PostDetailResponse;
import com.planmate.community.dto.PostListResponse;
import com.planmate.community.dto.PostSearchRequest;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PublicPostRepository;

/**
 * PUB-03 공개 목록·검색·정렬, PUB-04 공개 상세 서버 확인.
 * DB·A 체커·B 여행 상태는 가짜(Mock)로 둔다. 쿼리 문법은 서버를 켤 때 Hibernate가 검사한다.
 */
class PublicPostQueryServiceTest {

    private static final Long ME = 1L;

    private PublicPostRepository repository;
    private AccountStatusChecker account;
    private ObjectProvider<TripStatusReader> readerProvider;
    private final Set<Long> trashedTrips = new HashSet<>(); // 휴지통에 있는 원래 여행
    private PublicPostQueryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(PublicPostRepository.class);
        account = mock(AccountStatusChecker.class);
        when(account.findLoginMemberId()).thenReturn(Optional.empty()); // 기본은 비회원
        // 가짜 B: trashedTrips에 있으면 TRASHED, 0 이하는 없는 여행, 나머지는 ACTIVE
        TripStatusReader reader = tripId -> tripId <= 0 ? Optional.empty()
                : Optional.of(trashedTrips.contains(tripId) ? TripStatus.TRASHED : TripStatus.ACTIVE);
        readerProvider = mock(ObjectProvider.class);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        service = new PublicPostQueryService(repository, account, readerProvider);
    }

    // 게시물 한 개 (여행 ID = postId + 1000, 게시 시각은 postId가 클수록 늦게)
    private static PublicPost post(long postId, int likes, boolean isPublic, boolean adminHidden, Long authorId) {
        PublicPost p = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(p, "postId", postId);
        ReflectionTestUtils.setField(p, "tripId", postId + 1000);
        ReflectionTestUtils.setField(p, "authorId", authorId);
        ReflectionTestUtils.setField(p, "postTitle", "여행 " + postId);
        ReflectionTestUtils.setField(p, "postIntro", "소개 " + postId);
        ReflectionTestUtils.setField(p, "isPublic", isPublic);
        ReflectionTestUtils.setField(p, "adminHidden", adminHidden);
        ReflectionTestUtils.setField(p, "likeCount", likes);
        ReflectionTestUtils.setField(p, "publishedAt", LocalDateTime.of(2026, 11, 1, 0, 0).plusHours(postId));
        return p;
    }

    private static PublicPost open(long postId) {
        return post(postId, 0, true, false, 99L);
    }

    // postId from부터 하나씩 줄어드는 공개 게시물 n개 (최신순 순서)
    private static List<PublicPost> openPosts(long from, int n) {
        return IntStream.range(0, n).mapToObj(i -> open(from - i)).toList();
    }

    private static PostSearchRequest search(String sort, String q, Long after) {
        return new PostSearchRequest(sort, q, null, null, after, null);
    }

    private static List<Long> ids(PageResponse<PostListResponse> res) {
        return res.content().stream().map(PostListResponse::postId).toList();
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // ---- PUB-03-02 제목 검색어 ----

    @Test
    void 검색어가_없거나_공백뿐이면_모두_일치() {
        assertEquals("%", PublicPostQueryService.titlePattern(null));
        assertEquals("%", PublicPostQueryService.titlePattern("   "));
    }

    @Test
    void 검색어는_소문자로_일부_일치하고_특수문자는_글자_그대로() {
        assertEquals("%tokyo%", PublicPostQueryService.titlePattern("  TOKYO "));
        assertEquals("%100\\%\\_\\\\%", PublicPostQueryService.titlePattern("100%_\\"));
    }

    @Test
    void 검색어는_50자까지_이모지도_한_글자() {
        assertEquals("%" + "가".repeat(49) + "😀%", PublicPostQueryService.titlePattern("가".repeat(49) + "😀"));
        assertError(ErrorCode.INVALID_INPUT, () -> PublicPostQueryService.titlePattern("가".repeat(51)));
    }

    @Test
    void 정렬_일수_page가_이상하면_INVALID_INPUT() {
        assertError(ErrorCode.INVALID_INPUT, () -> service.getPublicPosts(search("OLD", null, null)));
        assertError(ErrorCode.INVALID_INPUT,
                () -> service.getPublicPosts(new PostSearchRequest(null, null, null, 8, null, null)));
        assertError(ErrorCode.INVALID_INPUT,
                () -> service.getPublicPosts(new PostSearchRequest(null, null, 0L, null, null, null)));
        assertError(ErrorCode.INVALID_INPUT,
                () -> service.getPublicPosts(new PostSearchRequest(null, null, null, null, null, -1)));
    }

    // ---- PUB-03-01·03 목록·이어 받기 ----

    @Test
    void 최신순_첫_10개와_다음이_있는지() {
        when(repository.findLatest(eq("%"), any(Limit.class))).thenReturn(openPosts(30, 11));
        when(repository.findOpenTripIds("%")).thenReturn(IntStream.rangeClosed(1, 24).mapToObj(i -> i + 1000L).toList());

        PageResponse<PostListResponse> res = service.getPublicPosts(search(null, null, null));

        assertEquals(List.of(30L, 29L, 28L, 27L, 26L, 25L, 24L, 23L, 22L, 21L), ids(res));
        assertTrue(res.hasNext());
        assertEquals(24, res.totalElements()); // "검색 결과 24개"
        assertEquals(3, res.totalPages());
        assertEquals(0, res.page());
        assertEquals(10, res.size());
    }

    @Test
    void 마지막_묶음이면_다음이_없다() {
        when(repository.findLatest(eq("%"), any(Limit.class))).thenReturn(openPosts(4, 4));
        when(repository.findOpenTripIds("%")).thenReturn(List.of());

        PageResponse<PostListResponse> res = service.getPublicPosts(search("LATEST", null, null));

        assertEquals(4, res.content().size());
        assertFalse(res.hasNext());
    }

    @Test
    void 이어_받기는_마지막_카드의_정렬_값_다음부터() {
        PublicPost last = post(21, 5, true, false, 99L);
        when(repository.findById(21L)).thenReturn(Optional.of(last));
        when(repository.findMostLikedAfter(eq("%"), eq(5), eq(last.getPublishedAt()), eq(21L), any(Limit.class)))
                .thenReturn(openPosts(20, 3));

        PageResponse<PostListResponse> res = service.getPublicPosts(new PostSearchRequest("LIKES", null, null, null, 21L, 2));

        assertEquals(List.of(20L, 19L, 18L), ids(res));
        assertEquals(2, res.page()); // 화면이 보낸 page를 그대로 돌려줌
        verify(repository, never()).findMostLiked(any(), any());
    }

    @Test
    void 이어_받을_마지막_카드가_없으면_INVALID_INPUT() {
        when(repository.findById(77L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVALID_INPUT, () -> service.getPublicPosts(search(null, null, 77L)));
    }

    @Test
    void 휴지통_여행은_빼고_10개가_찰_때까지_더_읽는다() {
        List<PublicPost> first = openPosts(30, 11); // 30~20
        trashedTrips.add(1029L); // 29번 게시물의 여행
        trashedTrips.add(1025L); // 25번
        when(repository.findLatest(eq("%"), any(Limit.class))).thenReturn(first);
        PublicPost lastOfBatch = first.get(10); // 20번
        when(repository.findLatestAfter(eq("%"), eq(lastOfBatch.getPublishedAt()), eq(20L), any(Limit.class)))
                .thenReturn(openPosts(19, 5));
        when(repository.findOpenTripIds("%")).thenReturn(List.of(1030L, 1029L, 1025L, 1019L, 0L)); // 0 = 없는 여행

        PageResponse<PostListResponse> res = service.getPublicPosts(search(null, null, null));

        assertEquals(List.of(30L, 28L, 27L, 26L, 24L, 23L, 22L, 21L, 20L, 19L), ids(res));
        assertTrue(res.hasNext()); // 18번이 남음
        assertEquals(2, res.totalElements()); // 휴지통·없는 여행 제외
    }

    @Test
    void 제목_검색어를_패턴으로_넘긴다() {
        when(repository.findLatest(eq("%도쿄%"), any(Limit.class))).thenReturn(List.of(open(3)));
        when(repository.findOpenTripIds("%도쿄%")).thenReturn(List.of(1003L));

        PageResponse<PostListResponse> res = service.getPublicPosts(search(null, " 도쿄 ", null));

        assertEquals(List.of(3L), ids(res));
        assertEquals(1, res.totalElements());
    }

    @Test
    void 목록_카드에는_공개_정보만_있고_남의_파트_값은_아직_null() {
        when(repository.findLatest(eq("%"), any(Limit.class))).thenReturn(List.of(open(3)));
        when(repository.findOpenTripIds("%")).thenReturn(List.of(1003L));

        PostListResponse card = service.getPublicPosts(search(null, null, null)).content().get(0);

        assertEquals("여행 3", card.title());
        assertNull(card.cityName()); // TODO(B)
        assertNull(card.days()); // TODO(B)
        assertNull(card.authorNickname()); // TODO(A)
    }

    @Test
    void B의_여행_상태_조회가_없으면_목록을_부를_때_오류() {
        when(readerProvider.getIfAvailable()).thenReturn(null);
        when(repository.findLatest(eq("%"), any(Limit.class))).thenReturn(List.of(open(3)));

        assertThrows(IllegalStateException.class, () -> service.getPublicPosts(search(null, null, null)));
    }

    // ---- PUB-04-01·03 상세 ----

    @Test
    void 비공개_관리자숨김_휴지통_없는_게시물은_볼_수_없다() {
        when(repository.findById(1L)).thenReturn(Optional.of(post(1, 0, false, false, 99L)));
        when(repository.findById(2L)).thenReturn(Optional.of(post(2, 0, true, true, 99L)));
        when(repository.findById(3L)).thenReturn(Optional.of(open(3)));
        trashedTrips.add(1003L);
        when(repository.findById(4L)).thenReturn(Optional.empty());

        for (long id = 1; id <= 4; id++) {
            long postId = id;
            assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.getPublicPostDetail(postId));
        }
        assertEquals("볼 수 없는 게시물이에요.", ErrorCode.PUBLIC_POST_NOT_VISIBLE.getMessage());
    }

    @Test
    void 휴지통에서_복원되면_다시_보인다_IS_PUBLIC은_그대로() {
        PublicPost p = open(3);
        when(repository.findById(3L)).thenReturn(Optional.of(p));
        trashedTrips.add(1003L);
        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.getPublicPostDetail(3L));

        trashedTrips.remove(1003L); // 복원
        assertEquals(3L, service.getPublicPostDetail(3L).postId());
        assertTrue(p.getIsPublic());
    }

    @Test
    void 비회원이_보면_viewer는_모두_false() {
        when(repository.findById(3L)).thenReturn(Optional.of(open(3)));

        PostDetailResponse r = service.getPublicPostDetail(3L);

        assertEquals(PostDetailResponse.Viewer.GUEST, r.viewer());
        assertEquals("소개 3", r.intro());
        assertTrue(r.schedule().isEmpty()); // TODO(B·C) 일정
        verify(account, never()).requireCommunityWritable(anyLong());
    }

    @Test
    void 내_게시물이면_mine() {
        when(account.findLoginMemberId()).thenReturn(Optional.of(ME));
        when(repository.findById(3L)).thenReturn(Optional.of(post(3, 0, true, false, ME)));

        PostDetailResponse.Viewer v = service.getPublicPostDetail(3L).viewer();

        assertTrue(v.loggedIn());
        assertTrue(v.mine());
        assertTrue(v.communityWritable());
    }

    @Test
    void 이용_제한_회원이면_communityWritable이_false() {
        when(account.findLoginMemberId()).thenReturn(Optional.of(ME));
        doThrow(new BusinessException(ErrorCode.COMMUNITY_RESTRICTED)).when(account).requireCommunityWritable(ME);
        when(repository.findById(3L)).thenReturn(Optional.of(open(3)));

        PostDetailResponse.Viewer v = service.getPublicPostDetail(3L).viewer();

        assertTrue(v.loggedIn());
        assertFalse(v.mine());
        assertFalse(v.communityWritable());
    }
}
