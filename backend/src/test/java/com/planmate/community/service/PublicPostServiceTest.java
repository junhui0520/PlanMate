package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripAccessChecker;
import com.planmate.common.security.TripStatusReader;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.community.dto.MyPostResponse;
import com.planmate.community.dto.PostCreateRequest;
import com.planmate.community.dto.PostShareStatusResponse;
import com.planmate.community.dto.PostUpdateRequest;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.trip.dto.TripBasicInfo;
import com.planmate.trip.service.TripService;

/**
 * PUB-01 공유하기·미리보기·게시, PUB-02-02 비공개 전환, PUB-02-05 제목·소개 수정 서버 확인.
 * DB·A 체커·B 여행 상태·같은 요청 확인은 가짜(Mock)로 둔다.
 */
class PublicPostServiceTest {

    private static final Long ME = 1L;
    private static final Long TRIP = 50L;
    private static final String KEY = "3f8c2a9e-1b47-4c6d-9a0e-5d2f7b8c1e34";

    private PublicPostRepository posts;
    private AccountStatusChecker account;
    private TripAccessChecker tripAccess;
    private TripStatusReader tripStatus;
    private TripService tripService;
    private DuplicateRequestChecker duplicate;
    private PublicPostQueryService query;
    private PostCommentRepository comments;
    private PublicPostService service;

    @BeforeEach
    void setUp() {
        posts = mock(PublicPostRepository.class);
        account = mock(AccountStatusChecker.class);
        tripAccess = mock(TripAccessChecker.class); // 기본: 소유자 검사 통과
        tripStatus = mock(TripStatusReader.class);
        tripService = mock(TripService.class);
        duplicate = mock(DuplicateRequestChecker.class);
        query = mock(PublicPostQueryService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        when(tripStatus.findStatus(TRIP)).thenReturn(Optional.of(TripStatus.ACTIVE));
        when(posts.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(posts.saveAndFlush(any(PublicPost.class))).thenAnswer(inv -> {
            PublicPost p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 7L);
            return p;
        });
        comments = mock(PostCommentRepository.class);
        when(query.isCommunityWritable(ME)).thenReturn(true);
        service = new PublicPostService(posts, account, tripAccess, tripStatus, tripService, duplicate, query, comments);
    }

    // 이미 있는 게시물 (likeCount 12, 처음 게시 10.20)
    private static PublicPost existing(long postId, boolean isPublic, boolean adminHidden) {
        PublicPost p = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(p, "postId", postId);
        ReflectionTestUtils.setField(p, "tripId", TRIP);
        ReflectionTestUtils.setField(p, "authorId", ME);
        ReflectionTestUtils.setField(p, "postTitle", "예전 제목");
        ReflectionTestUtils.setField(p, "postIntro", "예전 소개");
        ReflectionTestUtils.setField(p, "isPublic", isPublic);
        ReflectionTestUtils.setField(p, "adminHidden", adminHidden);
        ReflectionTestUtils.setField(p, "likeCount", 12);
        ReflectionTestUtils.setField(p, "publishedAt", LocalDate.of(2026, 10, 20).atStartOfDay());
        return p;
    }

    private static PostCreateRequest create(String title, String intro) {
        return new PostCreateRequest(KEY, title, intro);
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // ---- PUB-01-01 공개용 제목·소개 규칙 ----

    @Test
    void 제목은_앞뒤_공백을_지우고_제어문자는_공백_1칸() {
        assertEquals("도쿄 가족 여행", PublicPostService.checkTitle("  도쿄\n\n가족\t여행 "));
    }

    @Test
    void 제목이_비거나_공백뿐이면_공개용_제목을_입력해_주세요() {
        assertError(ErrorCode.POST_TITLE_REQUIRED, () -> PublicPostService.checkTitle(null));
        assertError(ErrorCode.POST_TITLE_REQUIRED, () -> PublicPostService.checkTitle("   "));
        assertEquals("공개용 제목을 입력해 주세요.", ErrorCode.POST_TITLE_REQUIRED.getMessage());
    }

    @Test
    void 제목은_40자까지_이모지도_한_글자() {
        assertEquals(40, PublicPostService.checkTitle("가".repeat(39) + "😀").codePointCount(0, 41));
        assertError(ErrorCode.INVALID_INPUT, () -> PublicPostService.checkTitle("가".repeat(41)));
    }

    @Test
    void 소개는_줄바꿈만_허용하고_비면_null() {
        assertEquals("오전엔 관광\n오후엔 공원", PublicPostService.checkIntro(" 오전엔 관광\r\n오후엔 공원 "));
        assertEquals("줄 바꿈 아님", PublicPostService.checkIntro("줄\t바꿈 아님"));
        assertNull(PublicPostService.checkIntro("  \n "));
        assertNull(PublicPostService.checkIntro(null));
        assertError(ErrorCode.INVALID_INPUT, () -> PublicPostService.checkIntro("가".repeat(101)));
    }

    // ---- PUB-01-01 공유하기 상태 ----

    @Test
    void 공유한_적_없으면_NONE() {
        assertEquals(PostShareStatusResponse.Status.NONE, service.getShareStatus(TRIP).status());
        verify(tripAccess).requireOwner(TRIP, ME, ErrorCode.POST_OWNER_ONLY);
    }

    @Test
    void 비공개로_바꾼_게시물이면_PRIVATE와_예전_제목_소개() {
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, false, false)));

        PostShareStatusResponse r = service.getShareStatus(TRIP);

        assertEquals(PostShareStatusResponse.Status.PRIVATE, r.status());
        assertEquals("예전 제목", r.title());
        assertEquals("예전 소개", r.intro());
    }

    @Test
    void 공개_중이면_PUBLIC_관리자_비공개면_ADMIN_HIDDEN() {
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, true, false)));
        assertEquals(PostShareStatusResponse.Status.PUBLIC, service.findShareStatus(TRIP).status());

        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, true, true)));
        assertEquals(PostShareStatusResponse.Status.ADMIN_HIDDEN, service.findShareStatus(TRIP).status());
    }

    @Test
    void 소유자가_아니면_공유_상태도_볼_수_없다() {
        doThrow(new BusinessException(ErrorCode.POST_OWNER_ONLY)).when(tripAccess).requireOwner(TRIP, ME, ErrorCode.POST_OWNER_ONLY);

        assertError(ErrorCode.POST_OWNER_ONLY, () -> service.getShareStatus(TRIP));
        assertEquals("여행 소유자만 공개할 수 있어요.", ErrorCode.POST_OWNER_ONLY.getMessage());
    }

    // ---- PUB-01-02 미리보기 ----

    @Test
    void 미리보기는_일수만_채우고_저장하지_않는다() {
        when(tripService.findBasicInfo(TRIP)).thenReturn(Optional.of(
                new TripBasicInfo(4, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23), "JP")));

        assertEquals(4, service.getPreview(TRIP).days());
        verify(posts, never()).saveAndFlush(any());
    }

    // ---- PUB-01-03 게시 ----

    @Test
    void 처음_게시하면_공개_게시물을_만들고_요청을_기록한다() {
        assertEquals(7L, service.publish(TRIP, create("  도쿄 여행 ", "소개")).postId());

        verify(posts).saveAndFlush(any(PublicPost.class));
        verify(duplicate).record(KEY, ME, TRIP);
    }

    @Test
    void 처음_게시한_게시물은_공개_좋아요_0_처음_게시_시각_있음() {
        PublicPost p = PublicPost.publish(TRIP, ME, "제목", null);

        assertTrue(p.getIsPublic());
        assertFalse(p.getAdminHidden());
        assertEquals(0, p.getLikeCount());
        assertNotNull(p.getPublishedAt());
        assertNull(p.getUpdatedAt());
        assertNull(p.getPostImageUrl()); // 사진은 아직 쓰지 않음
    }

    @Test
    void 비공개였던_게시물은_같은_게시물을_다시_공개한다_좋아요_유지() {
        PublicPost old = existing(7, false, false);
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(old));

        assertEquals(7L, service.publish(TRIP, create("새 제목", "새 소개")).postId());

        assertTrue(old.getIsPublic());
        assertEquals("새 제목", old.getPostTitle());
        assertEquals("새 소개", old.getPostIntro());
        assertEquals(12, old.getLikeCount());
        assertEquals(LocalDate.of(2026, 10, 20).atStartOfDay(), old.getPublishedAt()); // 처음 게시 시각 그대로
        assertNotNull(old.getUpdatedAt());
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    void 이미_공개_중이면_다시_게시할_수_없다() {
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, true, false)));

        assertError(ErrorCode.POST_ALREADY_PUBLIC, () -> service.publish(TRIP, create("제목", null)));
        verify(duplicate, never()).record(any(), any(), any());
        assertEquals("이미 공개한 여행이에요.", ErrorCode.POST_ALREADY_PUBLIC.getMessage());
    }

    @Test
    void 관리자가_비공개로_바꾼_게시물은_다시_공유할_수_없다() {
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, false, true)));

        assertError(ErrorCode.POST_ADMIN_HIDDEN, () -> service.publish(TRIP, create("제목", null)));
    }

    @Test
    void 동시에_같은_여행을_게시해_유일_조건에_걸리면_이미_공개() {
        when(posts.saveAndFlush(any(PublicPost.class))).thenThrow(new DataIntegrityViolationException("UQ_PUBLIC_POST_01"));

        assertError(ErrorCode.POST_ALREADY_PUBLIC, () -> service.publish(TRIP, create("제목", null)));
    }

    @Test
    void 같은_요청을_다시_보내면_이전_게시물을_그대로_돌려준다() {
        RequestLog log = RequestLog.success(KEY, ME, TRIP);
        when(duplicate.findProcessed(KEY, ME)).thenReturn(Optional.of(log));
        when(posts.findByTripId(TRIP)).thenReturn(Optional.of(existing(7, true, false)));

        assertEquals(7L, service.publish(TRIP, create("제목", null)).postId());
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    void 휴지통_여행은_게시할_수_없다() {
        when(tripStatus.findStatus(TRIP)).thenReturn(Optional.of(TripStatus.TRASHED));

        assertError(ErrorCode.POST_TRIP_TRASHED, () -> service.publish(TRIP, create("제목", null)));
        assertError(ErrorCode.POST_TRIP_TRASHED, () -> service.getPreview(TRIP));
        assertEquals("휴지통에 있는 여행은 게시할 수 없어요.", ErrorCode.POST_TRIP_TRASHED.getMessage());
    }

    @Test
    void 이용_제한_회원은_게시할_수_없다() {
        doThrow(new BusinessException(ErrorCode.POST_RESTRICTED)).when(account).requireCommunityWritable(ME, ErrorCode.POST_RESTRICTED);

        assertError(ErrorCode.POST_RESTRICTED, () -> service.publish(TRIP, create("제목", null)));
        assertError(ErrorCode.POST_RESTRICTED, () -> service.getPreview(TRIP));
        assertEquals("이용 제한 중에는 게시할 수 없어요.", ErrorCode.POST_RESTRICTED.getMessage());
    }

    @Test
    void 소유자가_아니면_게시할_수_없다() {
        doThrow(new BusinessException(ErrorCode.POST_OWNER_ONLY)).when(tripAccess).requireOwner(TRIP, ME, ErrorCode.POST_OWNER_ONLY);

        assertError(ErrorCode.POST_OWNER_ONLY, () -> service.publish(TRIP, create("제목", null)));
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    void 제목이_비면_게시할_수_없다() {
        assertError(ErrorCode.POST_TITLE_REQUIRED, () -> service.publish(TRIP, create(" ", null)));
    }

    // ---- PUB-02-05 제목·소개 수정 ----

    @Test
    void 제목_소개만_고치고_수정_시각을_기록한다() {
        PublicPost p = existing(7, true, false);
        when(posts.findById(7L)).thenReturn(Optional.of(p));

        service.updateInfo(7L, new PostUpdateRequest(" 고친 제목 ", null));

        assertEquals("고친 제목", p.getPostTitle());
        assertNull(p.getPostIntro());
        assertNotNull(p.getUpdatedAt());
        assertEquals(12, p.getLikeCount());
        assertEquals(LocalDate.of(2026, 10, 20).atStartOfDay(), p.getPublishedAt());
        verify(tripAccess).requireOwner(TRIP, ME, ErrorCode.EDIT_PERMISSION_DENIED);
        verify(query).getPublicPostDetail(7L); // 고친 뒤의 상세를 돌려줌
    }

    @Test
    void 공개_중이_아니면_수정할_수_없다() {
        when(posts.findById(7L)).thenReturn(Optional.of(existing(7, false, false)));
        when(posts.findById(8L)).thenReturn(Optional.of(existing(8, true, true)));

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.updateInfo(7L, new PostUpdateRequest("제목", null)));
        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.updateInfo(8L, new PostUpdateRequest("제목", null)));
    }

    @Test
    void 소유자가_아니면_편집_권한이_없어요() {
        when(posts.findById(7L)).thenReturn(Optional.of(existing(7, true, false)));
        doThrow(new BusinessException(ErrorCode.EDIT_PERMISSION_DENIED)).when(tripAccess)
                .requireOwner(TRIP, ME, ErrorCode.EDIT_PERMISSION_DENIED);

        assertError(ErrorCode.EDIT_PERMISSION_DENIED, () -> service.updateInfo(7L, new PostUpdateRequest("제목", null)));
        assertError(ErrorCode.EDIT_PERMISSION_DENIED, () -> service.makePrivate(7L));
    }

    @Test
    void 이용_제한_회원은_수정도_비공개_전환도_불가() {
        doThrow(new BusinessException(ErrorCode.COMMUNITY_FEATURE_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);

        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.updateInfo(7L, new PostUpdateRequest("제목", null)));
        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.makePrivate(7L));
        verify(posts, never()).findById(anyLong());
    }

    // ---- PUB-02-02 비공개 전환 ----

    @Test
    void 비공개로_바꿔도_좋아요_제목은_그대로() {
        PublicPost p = existing(7, true, false);
        when(posts.findById(7L)).thenReturn(Optional.of(p));

        service.makePrivate(7L);

        assertFalse(p.getIsPublic());
        assertEquals(12, p.getLikeCount());
        assertEquals("예전 제목", p.getPostTitle());
    }

    @Test
    void 관리자_숨김이어도_비공개_전환은_된다_이미_비공개면_그대로() {
        PublicPost hidden = existing(7, true, true);
        PublicPost already = existing(8, false, false);
        when(posts.findById(7L)).thenReturn(Optional.of(hidden));
        when(posts.findById(8L)).thenReturn(Optional.of(already));

        service.makePrivate(7L);
        service.makePrivate(8L);

        assertFalse(hidden.getIsPublic());
        assertFalse(already.getIsPublic());
    }

    @Test
    void 없는_게시물은_볼_수_없는_게시물() {
        when(posts.findById(eq(99L))).thenReturn(Optional.empty());

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.makePrivate(99L));
    }

    // ---- PUB-02-04 내 게시글 ----

    // 내 게시물 (여행 ID = postId + 1000, 게시 시각은 postId가 클수록 늦게)
    private static PublicPost mine(long postId, boolean isPublic, boolean adminHidden, LocalDateTime updatedAt) {
        PublicPost p = existing(postId, isPublic, adminHidden);
        ReflectionTestUtils.setField(p, "tripId", postId + 1000);
        ReflectionTestUtils.setField(p, "postTitle", "여행 " + postId);
        ReflectionTestUtils.setField(p, "publishedAt", LocalDateTime.of(2026, 11, 1, 0, 0).plusHours(postId));
        ReflectionTestUtils.setField(p, "updatedAt", updatedAt);
        return p;
    }

    @Test
    void 내_게시글은_상태_좋아요_댓글_수와_여행_ID를_준다() {
        when(posts.findMine(eq(ME), any(Limit.class))).thenReturn(List.of(
                mine(3, true, false, LocalDateTime.of(2026, 11, 20, 0, 0)),
                mine(2, false, false, null),
                mine(1, true, true, null)));
        when(posts.findTripIdsByAuthorId(ME)).thenReturn(List.of(1003L, 1002L, 1001L));
        when(comments.countByPostId(3L)).thenReturn(3L);

        PageResponse<MyPostResponse> res = service.getMyPosts(null, null);

        List<MyPostResponse> c = res.content();
        assertEquals(PostShareStatusResponse.Status.PUBLIC, c.get(0).status());
        assertEquals(PostShareStatusResponse.Status.PRIVATE, c.get(1).status());
        assertEquals(PostShareStatusResponse.Status.ADMIN_HIDDEN, c.get(2).status());
        assertEquals(1003L, c.get(0).tripId()); // [공개 전환] → 그 여행의 공유하기
        assertEquals(12, c.get(0).likeCount());
        assertEquals(3L, c.get(0).commentCount());
        assertEquals(0L, c.get(1).commentCount());
        assertTrue(c.get(0).communityWritable());
        assertEquals(3, res.totalElements());
        assertFalse(res.hasNext());
        assertEquals(10, res.size());
    }

    @Test
    void 휴지통_여행의_게시물은_빼고_10개가_찰_때까지_더_읽는다() {
        List<PublicPost> first = LongStream.rangeClosed(1, 11).map(i -> 30 - i)
                .mapToObj(id -> mine(id, true, false, null)).toList(); // 29~19
        when(posts.findMine(eq(ME), any(Limit.class))).thenReturn(first);
        when(query.isTrashed(1028L)).thenReturn(true);
        PublicPost last = first.get(10); // 19번
        when(posts.findMineAfter(eq(ME), eq(last.getPublishedAt()), eq(19L), any(Limit.class)))
                .thenReturn(List.of(mine(18, true, false, null), mine(17, true, false, null)));
        when(posts.findTripIdsByAuthorId(ME)).thenReturn(List.of(1029L, 1028L));

        PageResponse<MyPostResponse> res = service.getMyPosts(null, 0);

        assertEquals(List.of(29L, 27L, 26L, 25L, 24L, 23L, 22L, 21L, 20L, 19L),
                res.content().stream().map(MyPostResponse::postId).toList());
        assertTrue(res.hasNext()); // 18번이 남음
        assertEquals(1, res.totalElements()); // 휴지통 제외
    }

    @Test
    void 이어_받기는_수정_시각_또는_게시_시각_기준() {
        LocalDateTime edited = LocalDateTime.of(2026, 11, 25, 9, 0);
        PublicPost cursor = mine(5, true, false, edited);
        when(posts.findById(5L)).thenReturn(Optional.of(cursor));
        when(posts.findMineAfter(eq(ME), eq(edited), eq(5L), any(Limit.class))).thenReturn(List.of(mine(4, false, false, null)));
        when(posts.findTripIdsByAuthorId(ME)).thenReturn(List.of());

        PageResponse<MyPostResponse> res = service.getMyPosts(5L, 1);

        assertEquals(4L, res.content().get(0).postId());
        assertEquals(1, res.page());
    }

    @Test
    void 이어_받기_기준이_남의_게시물이거나_없으면_INVALID_INPUT() {
        PublicPost others = mine(5, true, false, null);
        ReflectionTestUtils.setField(others, "authorId", 2L);
        when(posts.findById(5L)).thenReturn(Optional.of(others));
        when(posts.findById(6L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyPosts(5L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyPosts(6L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyPosts(null, -1));
    }

    @Test
    void 이용_제한_회원도_목록은_보고_버튼은_비활성() {
        when(query.isCommunityWritable(ME)).thenReturn(false);
        when(posts.findMine(eq(ME), any(Limit.class))).thenReturn(List.of(mine(3, true, false, null)));
        when(posts.findTripIdsByAuthorId(ME)).thenReturn(List.of(1003L));

        assertFalse(service.getMyPosts(null, null).content().get(0).communityWritable());
    }
}
