package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.LikeResponse;
import com.planmate.community.entity.PostLike;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostLikeRepository;
import com.planmate.community.repository.PublicPostRepository;

/**
 * PUB-05-01·02 좋아요·취소 서버 확인. DB·A 체커·보이는지 검사는 가짜(Mock)로 둔다.
 */
class PostLikeServiceTest {

    private static final Long ME = 1L;
    private static final Long POST = 10L;

    private PublicPostRepository posts;
    private PostLikeRepository likes;
    private AccountStatusChecker account;
    private PublicPostQueryService query;
    private PostLikeService service;
    private PublicPost post;

    @BeforeEach
    void setUp() {
        posts = mock(PublicPostRepository.class);
        likes = mock(PostLikeRepository.class);
        account = mock(AccountStatusChecker.class);
        query = mock(PublicPostQueryService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        post = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(post, "postId", POST);
        ReflectionTestUtils.setField(post, "likeCount", 12);
        when(posts.findByIdForUpdate(POST)).thenReturn(Optional.of(post));
        when(query.isVisible(post)).thenReturn(true);
        service = new PostLikeService(posts, likes, account, query);
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    @Test
    void 좋아요하면_1행_저장하고_좋아요_수_더하기_1() {
        LikeResponse r = service.like(POST);

        assertTrue(r.liked());
        assertEquals(13, r.likeCount());
        verify(likes).save(any(PostLike.class));
        verify(posts).findByIdForUpdate(POST); // 게시물 행을 잠그고 처리
    }

    @Test
    void 이미_눌렀으면_그대로_성공하고_숫자는_안_오른다() {
        when(likes.existsByPostIdAndMemberId(POST, ME)).thenReturn(true);

        LikeResponse r = service.like(POST);

        assertTrue(r.liked());
        assertEquals(12, r.likeCount());
        verify(likes, never()).save(any());
    }

    @Test
    void 볼_수_없는_게시물에는_좋아요_할_수_없다() {
        when(query.isVisible(post)).thenReturn(false);
        when(posts.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.like(POST));
        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.like(99L));
        verify(likes, never()).save(any());
        assertEquals("볼 수 없는 게시물이에요.", ErrorCode.PUBLIC_POST_NOT_VISIBLE.getMessage());
    }

    @Test
    void 이용_제한_회원은_좋아요_불가_기능정의서_문구() {
        doThrow(new BusinessException(ErrorCode.LIKE_RESTRICTED)).when(account).requireCommunityWritable(ME, ErrorCode.LIKE_RESTRICTED);

        assertError(ErrorCode.LIKE_RESTRICTED, () -> service.like(POST));
        verify(posts, never()).findByIdForUpdate(any());
        assertEquals("이용 제한 중에는 좋아요를 누를 수 없어요.", ErrorCode.LIKE_RESTRICTED.getMessage());
    }

    @Test
    void 비회원은_로그인_필요() {
        when(account.requireLoginMemberId()).thenThrow(new BusinessException(ErrorCode.LOGIN_REQUIRED));

        assertError(ErrorCode.LOGIN_REQUIRED, () -> service.like(POST));
        assertError(ErrorCode.LOGIN_REQUIRED, () -> service.unlike(POST));
    }

    @Test
    void 취소하면_행을_지우고_좋아요_수_빼기_1() {
        PostLike like = PostLike.create(POST, ME);
        when(likes.findByPostIdAndMemberId(POST, ME)).thenReturn(Optional.of(like));

        LikeResponse r = service.unlike(POST);

        assertFalse(r.liked());
        assertEquals(11, r.likeCount());
        verify(likes).delete(like);
    }

    @Test
    void 좋아요가_없는데_취소하면_그대로_성공하고_숫자는_안_바뀐다() {
        LikeResponse r = service.unlike(POST);

        assertFalse(r.liked());
        assertEquals(12, r.likeCount());
        verify(likes, never()).delete(any());
    }

    @Test
    void 좋아요_수는_0_아래로_내려가지_않는다() {
        ReflectionTestUtils.setField(post, "likeCount", 0);
        when(likes.findByPostIdAndMemberId(POST, ME)).thenReturn(Optional.of(PostLike.create(POST, ME)));

        assertEquals(0, service.unlike(POST).likeCount());
    }

    @Test
    void 취소는_게시물이_지금_보이는지_따지지_않는다() {
        when(query.isVisible(post)).thenReturn(false); // 비공개로 바뀐 게시물
        when(likes.findByPostIdAndMemberId(POST, ME)).thenReturn(Optional.of(PostLike.create(POST, ME)));

        assertEquals(11, service.unlike(POST).likeCount());
    }

    @Test
    void 이용_제한_회원은_취소도_불가_커뮤니티_기능_문구() {
        doThrow(new BusinessException(ErrorCode.COMMUNITY_FEATURE_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);

        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.unlike(POST));
        verify(likes, never()).delete(any());
        assertEquals("이용 제한 중에는 커뮤니티 기능을 쓸 수 없어요.", ErrorCode.COMMUNITY_FEATURE_RESTRICTED.getMessage());
    }
}
