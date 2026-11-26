package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.MyBookmarkResponse;
import com.planmate.community.entity.PostBookmark;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostBookmarkRepository;
import com.planmate.community.repository.PublicPostRepository;

/**
 * PUB-06-01 북마크 저장·해제, PUB-06-02 내 북마크 목록 서버 확인. DB·A 체커·보이는지 검사는 가짜(Mock).
 */
class PostBookmarkServiceTest {

    private static final Long ME = 1L;
    private static final Long POST = 10L;

    private PostBookmarkRepository bookmarks;
    private PublicPostRepository posts;
    private AccountStatusChecker account;
    private PublicPostQueryService query;
    private PostBookmarkService service;

    @BeforeEach
    void setUp() {
        bookmarks = mock(PostBookmarkRepository.class);
        posts = mock(PublicPostRepository.class);
        account = mock(AccountStatusChecker.class);
        query = mock(PublicPostQueryService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        PublicPost p = post(POST);
        when(posts.findByIdForUpdate(POST)).thenReturn(Optional.of(p));
        when(query.isVisible(any())).thenReturn(true);
        service = new PostBookmarkService(bookmarks, posts, account, query);
    }

    private static PublicPost post(long postId) {
        PublicPost p = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(p, "postId", postId);
        ReflectionTestUtils.setField(p, "postTitle", "여행 " + postId);
        ReflectionTestUtils.setField(p, "likeCount", 3);
        return p;
    }

    // 북마크 한 개 (bookmarkId가 클수록 늦게 담음)
    private static PostBookmark bookmark(long bookmarkId, long postId, long memberId) {
        PostBookmark b = PostBookmark.create(postId, memberId);
        ReflectionTestUtils.setField(b, "bookmarkId", bookmarkId);
        ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.of(2026, 11, 1, 0, 0).plusMinutes(bookmarkId));
        return b;
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // ---- PUB-06-01 저장·해제 ----

    @Test
    void 북마크하면_저장() {
        assertTrue(service.bookmark(POST).bookmarked());
        verify(bookmarks).save(any(PostBookmark.class));
        verify(posts).findByIdForUpdate(POST); // 같은 게시물 연타는 차례로
    }

    @Test
    void 이미_담았으면_그대로_성공_1개만() {
        when(bookmarks.existsByPostIdAndMemberId(POST, ME)).thenReturn(true);

        assertTrue(service.bookmark(POST).bookmarked());
        verify(bookmarks, never()).save(any());
    }

    @Test
    void 북마크는_회원당_200개까지() {
        when(bookmarks.countByMemberId(ME)).thenReturn(199L);
        assertTrue(service.bookmark(POST).bookmarked());

        when(bookmarks.countByMemberId(ME)).thenReturn(200L);
        assertError(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, () -> service.bookmark(POST));
        assertEquals("북마크는 최대 200개까지 담을 수 있어요.", ErrorCode.BOOKMARK_LIMIT_EXCEEDED.getMessage());
    }

    @Test
    void 볼_수_없는_게시물은_북마크_불가() {
        when(query.isVisible(any())).thenReturn(false);

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.bookmark(POST));
        verify(bookmarks, never()).save(any());
    }

    @Test
    void 이용_제한_회원은_저장도_해제도_불가() {
        doThrow(new BusinessException(ErrorCode.COMMUNITY_FEATURE_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);

        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.bookmark(POST));
        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.unbookmark(POST));
        verify(bookmarks, never()).delete(any());
    }

    @Test
    void 해제하면_지우고_없으면_그대로_성공() {
        PostBookmark b = bookmark(5, POST, ME);
        when(bookmarks.findByPostIdAndMemberId(POST, ME)).thenReturn(Optional.of(b));
        assertFalse(service.unbookmark(POST).bookmarked());
        verify(bookmarks).delete(b);

        assertFalse(service.unbookmark(77L).bookmarked()); // 없는 북마크
        verify(posts, never()).findByIdForUpdate(77L); // 해제는 게시물 상태를 따지지 않음
    }

    // ---- PUB-06-02 내 북마크 목록 ----

    @Test
    void 담은_시각_최신순_20개와_다음이_있는지() {
        List<PostBookmark> rows = IntStream.range(0, 21).mapToObj(i -> bookmark(100 - i, 500 - i, ME)).toList();
        when(bookmarks.findMine(eq(ME), any(Limit.class))).thenReturn(rows);
        when(posts.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            return java.util.stream.StreamSupport.stream(ids.spliterator(), false).map(PostBookmarkServiceTest::post).toList();
        });
        when(bookmarks.countByMemberId(ME)).thenReturn(45L);

        PageResponse<MyBookmarkResponse> res = service.getMyBookmarks(null, null);

        assertEquals(20, res.content().size());
        assertEquals(100L, res.content().get(0).bookmarkId());
        assertEquals("여행 500", res.content().get(0).post().title());
        assertTrue(res.hasNext());
        assertEquals(45, res.totalElements());
        assertEquals(3, res.totalPages());
        assertEquals(20, res.size());
    }

    @Test
    void 볼_수_없게_된_게시물은_카드만_남기고_내용은_비운다() {
        PublicPost hidden = post(501);
        when(bookmarks.findMine(eq(ME), any(Limit.class)))
                .thenReturn(List.of(bookmark(2, 500, ME), bookmark(1, 501, ME), bookmark(0, 502, ME)));
        when(posts.findAllById(any())).thenReturn(List.of(post(500), hidden)); // 502는 영구 삭제
        when(query.isVisible(hidden)).thenReturn(false);

        List<MyBookmarkResponse> content = service.getMyBookmarks(null, null).content();

        assertTrue(content.get(0).visible());
        assertFalse(content.get(1).visible());
        assertNull(content.get(1).post()); // 제목·도시도 숨김
        assertEquals(501L, content.get(1).postId()); // 해제는 가능
        assertFalse(content.get(2).visible());
    }

    @Test
    void 이어_받기는_내_마지막_북마크_다음부터() {
        PostBookmark last = bookmark(81, 481, ME);
        when(bookmarks.findById(81L)).thenReturn(Optional.of(last));
        when(bookmarks.findMineAfter(eq(ME), eq(last.getCreatedAt()), eq(81L), any(Limit.class)))
                .thenReturn(List.of(bookmark(80, 480, ME)));
        when(posts.findAllById(any())).thenReturn(List.of(post(480)));

        PageResponse<MyBookmarkResponse> res = service.getMyBookmarks(81L, 1);

        assertEquals(List.of(80L), res.content().stream().map(MyBookmarkResponse::bookmarkId).toList());
        assertFalse(res.hasNext());
        assertEquals(1, res.page());
    }

    @Test
    void 이어_받기_기준이_없거나_남의_북마크면_INVALID_INPUT() {
        when(bookmarks.findById(5L)).thenReturn(Optional.of(bookmark(5, 1, 2L))); // 다른 회원
        when(bookmarks.findById(6L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyBookmarks(5L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyBookmarks(6L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getMyBookmarks(null, -1));
    }

    @Test
    void 이용_제한_회원도_내_북마크는_볼_수_있다() {
        when(bookmarks.findMine(eq(ME), any(Limit.class))).thenReturn(List.of());
        when(posts.findAllById(any())).thenReturn(List.of());

        assertTrue(service.getMyBookmarks(null, null).content().isEmpty());
        verify(account, never()).requireCommunityWritable(any(), any());
    }
}
