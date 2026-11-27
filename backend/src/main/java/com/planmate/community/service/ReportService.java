package com.planmate.community.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.ReportRequest;
import com.planmate.community.dto.ReportResponse;
import com.planmate.community.entity.PostComment;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.entity.Report;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.community.repository.ReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 신고 접수·관리자 검토로 전달 (PUB-09). 저장(상태 '접수')하면 A의 관리자 신고 목록(ADM-02)에 나온다.
 * 신고 수만으로 자동 숨김 없음(숨김은 관리자 판단). 1:1 문의와 따로 둔다.
 * 권한: 로그인한 정상 회원(이용 제한 회원 불가). 내 게시물·내 댓글은 신고할 수 없다(화면은 버튼 숨김).
 * TODO(A와 맞추기): 관리자 쪽(신고 목록·상세·처리 완료로 바꾸기)은 A 몫(ADM-02)이라 여기서 만들지 않았다. 필요한 함수는 A와 이름을 맞춘 뒤 더한다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final int DETAIL_MAX = 300; // PUB-09-01 기타 상세 내용 1~300자

    private final ReportRepository reportRepository;
    private final PublicPostRepository publicPostRepository;
    private final PostCommentRepository postCommentRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final PublicPostQueryService publicPostQueryService;

    /**
     * PUB-09-01 게시물 신고. 공개 게시물만(비공개·관리자 숨김·원래 여행 휴지통·없는 게시물이면 "이미 삭제되었거나 볼 수 없는 내용이에요.").
     * 처리 전(접수 상태)에 같은 게시물을 다시 신고하면 "이미 접수된 신고예요." 관리자가 기각(처리 완료)한 뒤에는 다시 할 수 있다.
     * 게시물 행을 잠가 같은 신고가 동시에 와도 1건만 남는다.
     */
    @Transactional
    public ReportResponse reportPost(Long postId, ReportRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        String detail = checkDetail(req);
        PublicPost post = lockVisiblePost(postId);
        if (memberId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 내 게시물 (화면은 [신고] 숨김)
        }
        if (reportRepository.existsByReporterIdAndPostIdAndStatus(memberId, postId, Report.Status.RECEIVED)) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATE);
        }
        Report saved = reportRepository.save(Report.ofPost(postId, memberId, req.reason(), detail));
        return new ReportResponse(saved.getReportId());
    }

    /**
     * PUB-09-01 댓글 신고. 공개 게시물의 댓글만, 규칙은 게시물 신고와 같다.
     * 신고된 댓글은 숨기지 않고 관리자가 바로 삭제한다(PUB-09-02, PostCommentService.deleteByAdmin).
     */
    @Transactional
    public ReportResponse reportComment(Long commentId, ReportRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        String detail = checkDetail(req);
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
        lockVisiblePost(comment.getPostId()); // 댓글이 달린 게시물이 지금 보여야 하고, 동시 신고는 차례로
        if (memberId.equals(comment.getAuthorId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 내 댓글 (화면은 [신고] 숨김)
        }
        if (reportRepository.existsByReporterIdAndCommentIdAndStatus(memberId, commentId, Report.Status.RECEIVED)) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATE);
        }
        Report saved = reportRepository.save(Report.ofComment(commentId, memberId, req.reason(), detail));
        return new ReportResponse(saved.getReportId());
    }

    // 공개 게시물을 잠가서 가져온다. 없거나 볼 수 없으면 "이미 삭제되었거나 볼 수 없는 내용이에요."
    private PublicPost lockVisiblePost(Long postId) {
        return publicPostRepository.findByIdForUpdate(postId)
                .filter(publicPostQueryService::isVisible)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
    }

    /**
     * PUB-09-01 상세 내용: 기타면 필수 1~300자(이모지 1글자), 줄바꿈만 허용하고 다른 제어문자는 공백 1칸, 앞뒤 공백 제거.
     * 기타가 아니면 저장하지 않는다(null). 사유가 없거나 기타인데 비었거나 300자를 넘으면 INVALID_INPUT(화면에서 막는 값).
     */
    static String checkDetail(ReportRequest req) {
        if (req.reason() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (req.reason() != Report.Reason.ETC) {
            return null;
        }
        String detail = req.detail() == null ? "" : req.detail().replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n]]+", " ")
                .strip();
        if (detail.isEmpty() || detail.codePointCount(0, detail.length()) > DETAIL_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return detail;
    }
}
