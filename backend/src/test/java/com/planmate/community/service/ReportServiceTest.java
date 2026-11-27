package com.planmate.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.ReportRequest;
import com.planmate.community.entity.PostComment;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.entity.Report;
import com.planmate.community.entity.Report.Reason;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.community.repository.ReportRepository;

/**
 * PUB-09-01 신고 접수 서버 확인. DB·A 체커·보이는지 검사는 가짜(Mock)로 둔다.
 */
class ReportServiceTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long POST = 10L;
    private static final Long COMMENT = 20L;

    private ReportRepository reports;
    private PublicPostRepository posts;
    private PostCommentRepository comments;
    private AccountStatusChecker account;
    private PublicPostQueryService query;
    private ReportService service;
    private PublicPost post;
    private PostComment comment;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        posts = mock(PublicPostRepository.class);
        comments = mock(PostCommentRepository.class);
        account = mock(AccountStatusChecker.class);
        query = mock(PublicPostQueryService.class);
        when(account.requireLoginMemberId()).thenReturn(ME);
        post = BeanUtils.instantiateClass(PublicPost.class);
        ReflectionTestUtils.setField(post, "postId", POST);
        ReflectionTestUtils.setField(post, "authorId", OTHER);
        when(posts.findByIdForUpdate(POST)).thenReturn(Optional.of(post));
        when(query.isVisible(post)).thenReturn(true);
        comment = PostComment.write(POST, OTHER, "남의 댓글");
        ReflectionTestUtils.setField(comment, "commentId", COMMENT);
        when(comments.findById(COMMENT)).thenReturn(Optional.of(comment));
        when(reports.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "reportId", 300L);
            return r;
        });
        service = new ReportService(reports, posts, comments, account, query);
    }

    private static void assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    private Report saved() {
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(captor.capture());
        return captor.getValue();
    }

    // ---- 상세 내용 규칙 ----

    @Test
    void 기타는_상세_내용이_필수_1_300자_줄바꿈만_허용() {
        assertEquals("광고 링크가\n반복돼요 계속", ReportService.checkDetail(new ReportRequest(Reason.ETC, " 광고 링크가\r\n반복돼요\t계속 ")));
        assertError(ErrorCode.INVALID_INPUT, () -> ReportService.checkDetail(new ReportRequest(Reason.ETC, "  ")));
        assertError(ErrorCode.INVALID_INPUT, () -> ReportService.checkDetail(new ReportRequest(Reason.ETC, null)));
        assertEquals(300, ReportService.checkDetail(new ReportRequest(Reason.ETC, "가".repeat(299) + "😀")).codePointCount(0, 301));
        assertError(ErrorCode.INVALID_INPUT, () -> ReportService.checkDetail(new ReportRequest(Reason.ETC, "가".repeat(301))));
    }

    @Test
    void 기타가_아니면_상세_내용은_저장하지_않는다() {
        assertNull(ReportService.checkDetail(new ReportRequest(Reason.SPAM, "같이 온 글")));
        assertError(ErrorCode.INVALID_INPUT, () -> ReportService.checkDetail(new ReportRequest(null, null)));
    }

    // ---- 게시물 신고 ----

    @Test
    void 게시물_신고는_접수_상태로_게시물_ID만_채운다() {
        assertEquals(300L, service.reportPost(POST, new ReportRequest(Reason.SPAM, null)).reportId());

        Report r = saved();
        assertEquals(Report.TargetType.POST, r.getTargetType());
        assertEquals(POST, r.getPostId());
        assertNull(r.getCommentId()); // 둘 다 채우지 않음(CK_REPORT_01)
        assertEquals(ME, r.getReporterId());
        assertEquals(Reason.SPAM, r.getReason());
        assertEquals(Report.Status.RECEIVED, r.getStatus());
        verify(posts).findByIdForUpdate(POST); // 게시물 행을 잠그고 처리
    }

    @Test
    void 기타_사유는_상세_내용을_같이_저장한다() {
        service.reportPost(POST, new ReportRequest(Reason.ETC, " 다른 사이트 글을 그대로 옮겼어요 "));

        assertEquals("다른 사이트 글을 그대로 옮겼어요", saved().getDetail());
    }

    @Test
    void 처리_전에_같은_게시물을_다시_신고하면_이미_접수된_신고() {
        when(reports.existsByReporterIdAndPostIdAndStatus(ME, POST, Report.Status.RECEIVED)).thenReturn(true);

        assertError(ErrorCode.REPORT_DUPLICATE, () -> service.reportPost(POST, new ReportRequest(Reason.ABUSE, null)));
        verify(reports, never()).save(any());
        assertEquals("이미 접수된 신고예요.", ErrorCode.REPORT_DUPLICATE.getMessage());
    }

    @Test
    void 관리자가_기각해_처리_완료된_뒤에는_다시_신고할_수_있다() {
        // 처리 완료(DONE) 신고만 있으면 접수 상태 신고가 없으므로 통과
        when(reports.existsByReporterIdAndPostIdAndStatus(ME, POST, Report.Status.RECEIVED)).thenReturn(false);

        assertEquals(300L, service.reportPost(POST, new ReportRequest(Reason.ABUSE, null)).reportId());
    }

    @Test
    void 볼_수_없거나_없는_게시물은_신고할_수_없다() {
        when(query.isVisible(post)).thenReturn(false);
        when(posts.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.reportPost(POST, new ReportRequest(Reason.SPAM, null)));
        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.reportPost(99L, new ReportRequest(Reason.SPAM, null)));
        assertEquals("이미 삭제되었거나 볼 수 없는 내용이에요.", ErrorCode.RESOURCE_NOT_ACCESSIBLE.getMessage());
    }

    @Test
    void 내_게시물은_신고할_수_없다() {
        ReflectionTestUtils.setField(post, "authorId", ME);

        assertError(ErrorCode.INVALID_INPUT, () -> service.reportPost(POST, new ReportRequest(Reason.SPAM, null)));
        verify(reports, never()).save(any());
    }

    @Test
    void 이용_제한_회원은_신고할_수_없다() {
        doThrow(new BusinessException(ErrorCode.COMMUNITY_FEATURE_RESTRICTED)).when(account)
                .requireCommunityWritable(ME, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);

        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.reportPost(POST, new ReportRequest(Reason.SPAM, null)));
        assertError(ErrorCode.COMMUNITY_FEATURE_RESTRICTED, () -> service.reportComment(COMMENT, new ReportRequest(Reason.SPAM, null)));
        verify(reports, never()).save(any());
    }

    @Test
    void 비회원은_로그인_필요() {
        when(account.requireLoginMemberId()).thenThrow(new BusinessException(ErrorCode.LOGIN_REQUIRED));

        assertError(ErrorCode.LOGIN_REQUIRED, () -> service.reportPost(POST, new ReportRequest(Reason.SPAM, null)));
    }

    // ---- 댓글 신고 ----

    @Test
    void 댓글_신고는_댓글_ID만_채운다() {
        service.reportComment(COMMENT, new ReportRequest(Reason.NICKNAME, null));

        Report r = saved();
        assertEquals(Report.TargetType.COMMENT, r.getTargetType());
        assertEquals(COMMENT, r.getCommentId());
        assertNull(r.getPostId());
        assertEquals(Reason.NICKNAME, r.getReason());
        verify(posts).findByIdForUpdate(POST); // 댓글이 달린 게시물을 잠그고 보이는지 확인
    }

    @Test
    void 처리_전에_같은_댓글을_다시_신고하면_이미_접수된_신고() {
        when(reports.existsByReporterIdAndCommentIdAndStatus(ME, COMMENT, Report.Status.RECEIVED)).thenReturn(true);

        assertError(ErrorCode.REPORT_DUPLICATE, () -> service.reportComment(COMMENT, new ReportRequest(Reason.ABUSE, null)));
    }

    @Test
    void 지워진_댓글이나_볼_수_없는_게시물의_댓글은_신고할_수_없다() {
        when(comments.findById(99L)).thenReturn(Optional.empty());
        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.reportComment(99L, new ReportRequest(Reason.SPAM, null)));

        when(query.isVisible(post)).thenReturn(false);
        assertError(ErrorCode.RESOURCE_NOT_ACCESSIBLE, () -> service.reportComment(COMMENT, new ReportRequest(Reason.SPAM, null)));
        verify(reports, never()).save(any());
    }

    @Test
    void 내_댓글은_신고할_수_없다() {
        ReflectionTestUtils.setField(comment, "authorId", ME);

        assertError(ErrorCode.INVALID_INPUT, () -> service.reportComment(COMMENT, new ReportRequest(Reason.SPAM, null)));
    }

    @Test
    void 신고가_쌓여도_게시물을_자동으로_숨기지_않는다() {
        service.reportPost(POST, new ReportRequest(Reason.SPAM, null));

        assertEquals(null, post.getAdminHidden()); // 관리자 숨김 값을 건드리지 않음
    }
}
