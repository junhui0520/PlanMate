package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

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
import com.planmate.common.security.AdminAccessChecker;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.community.dto.AdminCommentDeletedResponse;
import com.planmate.community.dto.CommentRequest;
import com.planmate.community.dto.CommentResponse;
import com.planmate.community.entity.PostComment;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;

/**
 * PUB-08-01~05 공개 댓글 서버 확인. DB·A 체커·보이는지 검사는 가짜(Mock)로 둔다.
 */
class PostCommentServiceTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long POST = 10L;

    private PostCommentRepository comments;
    private PublicPostRepository posts;
    private AccountStatusChecker account;
    private PublicPostQueryService query;
    private PostCommentService service;
    private PublicPost post;

    @BeforeEach
    void setUp() {
        comments = mock(PostCommentRepository.class);
        posts = mock(PublicPostRepository.class);
        account = mock(AccountStatusChecker.class);
        query = mock(PublicPostQueryService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        when(account.findLoginMemberId()).thenReturn(Optional.of(ME));
        post = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(post, "postId", POST);
        ReflectionTestUtils.setField(post, "authorId", OTHER); // 남의 게시물
        when(posts.findById(POST)).thenReturn(Optional.of(post));
        when(query.isVisible(post)).thenReturn(true);
        when(comments.save(any(PostComment.class))).thenAnswer(inv -> {
            PostComment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "commentId", 55L);
            return c;
        });
        admin = mock(AdminAccessChecker.class);
        when(admin.requireAdmin()).thenReturn(99L);
        service = new PostCommentService(comments, posts, account, query, admin);
    }

    // 댓글 한 개 (commentId가 클수록 늦게 씀)
    private static PostComment comment(long commentId, Long authorId) {
        PostComment c = PostComment.write(POST, authorId, "댓글 " + commentId);
        ReflectionTestUtils.setField(c, "commentId", commentId);
        ReflectionTestUtils.setField(c, "createdAt", LocalDateTime.of(2026, 11, 1, 0, 0).plusMinutes(commentId));
        return c;
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // ---- PUB-08-01 내용 규칙 ----

    @Test
    void 내용은_줄바꿈만_허용하고_다른_제어문자는_공백_1칸() {
        assertEquals("첫 줄\n둘째 줄 끝", PostCommentService.checkContent("  첫 줄\r\n둘째 줄\t끝 "));
    }

    @Test
    void HTML은_글자_그대로_저장한다() {
        assertEquals("<script>alert(1)</script>", PostCommentService.checkContent("<script>alert(1)</script>"));
    }

    @Test
    void 비었거나_500자를_넘으면_INVALID_INPUT_이모지도_한_글자() {
        assertError(ErrorCode.INVALID_INPUT, () -> PostCommentService.checkContent("  \n "));
        assertError(ErrorCode.INVALID_INPUT, () -> PostCommentService.checkContent(null));
        assertEquals(500, PostCommentService.checkContent("가".repeat(499) + "😀").codePointCount(0, 501));
        assertError(ErrorCode.INVALID_INPUT, () -> PostCommentService.checkContent("가".repeat(501)));
    }

    // ---- PUB-08-01 작성 ----

    @Test
    void 댓글을_쓰면_저장하고_내_댓글로_돌려준다() {
        CommentResponse r = service.write(POST, new CommentRequest(" 저도 다녀왔어요! "));

        assertEquals(55L, r.commentId());
        assertEquals("저도 다녀왔어요!", r.content());
        assertTrue(r.mine());
        assertFalse(r.edited());
        verify(comments).save(any(PostComment.class));
    }

    @Test
    void 볼_수_없는_게시물에는_댓글을_쓸_수_없다() {
        when(query.isVisible(post)).thenReturn(false);

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.write(POST, new CommentRequest("댓글")));
        verify(comments, never()).save(any());
    }

    @Test
    void 이용_제한_회원은_댓글을_쓸_수_없다() {
        doThrow(new BusinessException(ErrorCode.COMMENT_RESTRICTED)).when(account).requireCommunityWritable(ME, ErrorCode.COMMENT_RESTRICTED);

        assertError(ErrorCode.COMMENT_RESTRICTED, () -> service.write(POST, new CommentRequest("댓글")));
        assertEquals("이용 제한 중에는 댓글을 쓸 수 없어요.", ErrorCode.COMMENT_RESTRICTED.getMessage());
    }

    @Test
    void 같은_회원은_10초에_1개_남은_시간을_알려준다() {
        when(comments.findLastWrittenAt(ME)).thenReturn(DateTimeUtil.now().minusSeconds(3));

        BusinessException e = assertThrows(BusinessException.class, () -> service.write(POST, new CommentRequest("또")));

        assertEquals(ErrorCode.COMMENT_TOO_FAST, e.getErrorCode());
        assertNotNull(e.getRetryAfter());
        assertTrue(e.getRetryAfter().compareTo(Duration.ofSeconds(8)) <= 0 && !e.getRetryAfter().isNegative());
        assertEquals("잠시 후 다시 등록해 주세요.", ErrorCode.COMMENT_TOO_FAST.getMessage());
        verify(comments, never()).save(any());
    }

    @Test
    void 십초가_지났으면_다시_쓸_수_있다() {
        when(comments.findLastWrittenAt(ME)).thenReturn(DateTimeUtil.now().minusSeconds(11));

        assertEquals(55L, service.write(POST, new CommentRequest("다시")).commentId());
    }

    // ---- PUB-08-05 새 댓글 알림 ----

    @Test
    void 남의_게시물에_쓴_댓글만_알림_대상() {
        assertTrue(PostCommentService.shouldNotify(post, ME)); // 게시물 작성자 OTHER
        assertFalse(PostCommentService.shouldNotify(post, OTHER)); // 본인 게시물에 본인 댓글
        ReflectionTestUtils.setField(post, "authorId", null);
        assertFalse(PostCommentService.shouldNotify(post, ME)); // 작성자 정보 없음
    }

    // ---- PUB-08-02 목록 ----

    @Test
    void 오래된_순_20개와_다음이_있는지_내_댓글_표시() {
        List<PostComment> rows = LongStream.rangeClosed(1, 21).mapToObj(i -> comment(i, i == 2 ? ME : OTHER)).toList();
        when(comments.findFirstPage(eq(POST), any(Limit.class))).thenReturn(rows);
        when(comments.countByPostId(POST)).thenReturn(30L);

        PageResponse<CommentResponse> res = service.getComments(POST, null, null);

        assertEquals(20, res.content().size());
        assertEquals(1L, res.content().get(0).commentId());
        assertTrue(res.content().get(1).mine());
        assertFalse(res.content().get(0).mine());
        assertTrue(res.hasNext());
        assertEquals(30, res.totalElements());
        assertEquals(2, res.totalPages());
    }

    @Test
    void 이어_받기는_마지막_댓글_다음부터() {
        PostComment last = comment(20, OTHER);
        when(comments.findById(20L)).thenReturn(Optional.of(last));
        when(comments.findNextPage(eq(POST), eq(last.getCreatedAt()), eq(20L), any(Limit.class)))
                .thenReturn(List.of(comment(21, OTHER)));

        PageResponse<CommentResponse> res = service.getComments(POST, 20L, 1);

        assertEquals(21L, res.content().get(0).commentId());
        assertFalse(res.hasNext());
        assertEquals(1, res.page());
    }

    @Test
    void 비회원도_목록을_본다_내_댓글은_없음() {
        when(account.findLoginMemberId()).thenReturn(Optional.empty());
        when(comments.findFirstPage(eq(POST), any(Limit.class))).thenReturn(List.of(comment(1, ME)));

        assertFalse(service.getComments(POST, null, null).content().get(0).mine());
    }

    @Test
    void 볼_수_없는_게시물이면_댓글도_보이지_않는다() {
        when(query.isVisible(post)).thenReturn(false);

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.getComments(POST, null, null));
    }

    @Test
    void 이어_받기_기준이_다른_게시물_댓글이거나_없으면_INVALID_INPUT() {
        PostComment other = comment(5, OTHER);
        ReflectionTestUtils.setField(other, "postId", 99L);
        when(comments.findById(5L)).thenReturn(Optional.of(other));
        when(comments.findById(6L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVALID_INPUT, () -> service.getComments(POST, 5L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getComments(POST, 6L, 1));
        assertError(ErrorCode.INVALID_INPUT, () -> service.getComments(POST, null, -1));
    }

    // ---- PUB-08-03 수정 ----

    @Test
    void 내_댓글을_고치면_수정됨() {
        PostComment mine = comment(7, ME);
        when(comments.findById(7L)).thenReturn(Optional.of(mine));

        CommentResponse r = service.edit(7L, new CommentRequest(" 고쳤어요 "));

        assertEquals("고쳤어요", r.content());
        assertTrue(r.edited());
        assertTrue(mine.getIsEdited());
    }

    @Test
    void 남의_댓글은_고칠_수_없다() {
        when(comments.findById(8L)).thenReturn(Optional.of(comment(8, OTHER)));

        assertError(ErrorCode.COMMENT_EDIT_FORBIDDEN, () -> service.edit(8L, new CommentRequest("바꿈")));
        assertEquals("내가 쓴 댓글만 고칠 수 있어요.", ErrorCode.COMMENT_EDIT_FORBIDDEN.getMessage());
    }

    @Test
    void 볼_수_없는_게시물의_댓글은_고칠_수_없다() {
        when(comments.findById(7L)).thenReturn(Optional.of(comment(7, ME)));
        when(query.isVisible(post)).thenReturn(false);

        assertError(ErrorCode.PUBLIC_POST_NOT_VISIBLE, () -> service.edit(7L, new CommentRequest("바꿈")));
    }

    @Test
    void 이용_제한_회원은_댓글을_고칠_수_없다() {
        doThrow(new BusinessException(ErrorCode.COMMENT_EDIT_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMENT_EDIT_RESTRICTED);

        assertError(ErrorCode.COMMENT_EDIT_RESTRICTED, () -> service.edit(7L, new CommentRequest("바꿈")));
        assertEquals("이용 제한 중에는 댓글을 고칠 수 없어요.", ErrorCode.COMMENT_EDIT_RESTRICTED.getMessage());
    }

    @Test
    void 이미_지워진_댓글은_볼_수_없는_내용() {
        when(comments.findById(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.edit(99L, new CommentRequest("바꿈")));
        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.delete(99L));
    }

    // ---- PUB-08-04 삭제 ----

    @Test
    void 내_댓글을_지운다_게시물이_지금_보이는지는_따지지_않는다() {
        PostComment mine = comment(7, ME);
        when(comments.findById(7L)).thenReturn(Optional.of(mine));
        when(query.isVisible(post)).thenReturn(false);

        service.delete(7L);

        verify(comments).delete(mine);
    }

    @Test
    void 게시물_작성자도_남의_댓글은_지울_수_없다() {
        ReflectionTestUtils.setField(post, "authorId", ME); // 내 게시물
        when(comments.findById(8L)).thenReturn(Optional.of(comment(8, OTHER)));

        assertError(ErrorCode.COMMENT_DELETE_FORBIDDEN, () -> service.delete(8L));
        verify(comments, never()).delete(any());
        assertEquals("내가 쓴 댓글만 지울 수 있어요.", ErrorCode.COMMENT_DELETE_FORBIDDEN.getMessage());
    }

    @Test
    void 이용_제한_회원은_댓글을_지울_수_없다() {
        doThrow(new BusinessException(ErrorCode.COMMUNITY_FEATURE_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);

        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.delete(7L));
    }

    // ---- PUB-09-02 신고된 댓글 관리자 삭제 (A가 부름) ----

    private AdminAccessChecker admin;

    @Test
    void 관리자는_남의_댓글도_바로_지우고_작성자_정보를_돌려준다() {
        PostComment c = comment(8, OTHER);
        when(comments.findById(8L)).thenReturn(Optional.of(c));
        when(query.isVisible(post)).thenReturn(false); // 게시물 상태와 관계없이

        AdminCommentDeletedResponse r = service.deleteByAdmin(8L);

        verify(comments).delete(c);
        assertEquals(8L, r.commentId());
        assertEquals(POST, r.postId());
        assertEquals(OTHER, r.authorId()); // A가 조치 안내 받을 사람으로 씀
        verify(admin).requireAdmin();
    }

    @Test
    void 없는_댓글이면_볼_수_없는_내용_A는_대상_없음으로_종료() {
        when(comments.findById(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.deleteByAdmin(99L));
    }

    @Test
    void 관리자가_아니면_댓글을_지울_수_없다() {
        when(admin.requireAdmin()).thenThrow(new BusinessException(ErrorCode.ADMIN_ONLY));
        when(comments.findById(8L)).thenReturn(Optional.of(comment(8, OTHER)));

        assertError(ErrorCode.ADMIN_ONLY, () -> service.deleteByAdmin(8L));
        verify(comments, never()).delete(any());
    }
}
