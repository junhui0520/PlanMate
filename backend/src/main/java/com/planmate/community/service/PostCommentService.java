package com.planmate.community.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.community.dto.CommentRequest;
import com.planmate.community.dto.CommentResponse;
import com.planmate.community.entity.PostComment;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공개 댓글 작성·목록·수정·삭제·새 댓글 알림 (PUB-08). 대댓글·멘션 없음.
 * 목록은 비회원도 볼 수 있고, 쓰기·고치기·지우기는 로그인한 정상 회원(이용 제한 회원 불가).
 * TODO(A에 요청): 관리자 댓글 삭제(ADM-02)·탈퇴 때 본인 댓글 삭제를 부를 함수는 A와 이름을 맞춘 뒤 만든다.
 */
@Service
@RequiredArgsConstructor
public class PostCommentService {

    static final int CONTENT_MAX = 500; // PUB-08-01 1~500자
    static final int PAGE_SIZE = 20; // PUB-08-02 20개씩
    static final Duration WRITE_INTERVAL = Duration.ofSeconds(10); // PUB-08-01 같은 회원 10초에 1개

    private final PostCommentRepository postCommentRepository;
    private final PublicPostRepository publicPostRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final PublicPostQueryService publicPostQueryService;

    /**
     * PUB-08-02 댓글 목록: 작성 시각 오래된 순 20개, 마지막 댓글(after) 다음부터 이어 받기.
     * 볼 수 없는 게시물이면 댓글도 보이지 않는다("볼 수 없는 게시물이에요.").
     * page는 화면이 몇 번째로 불렀는지(0부터) 그대로 돌려준다. totalElements는 이 게시물의 댓글 수.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(Long postId, Long after, Integer page) {
        Long viewerId = accountStatusChecker.findLoginMemberId().orElse(null);
        visiblePost(postId);
        if (page != null && page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Limit limit = Limit.of(PAGE_SIZE + 1); // 1개 더 읽어 다음이 있는지 확인
        List<PostComment> rows;
        if (after == null) {
            rows = postCommentRepository.findFirstPage(postId, limit);
        } else {
            // 이어 받기 기준은 이 게시물의 댓글만 (지워졌거나 다른 게시물 댓글이면 INVALID_INPUT)
            PostComment last = postCommentRepository.findById(after)
                    .filter(c -> c.getPostId().equals(postId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
            rows = postCommentRepository.findNextPage(postId, last.getCreatedAt(), last.getCommentId(), limit);
        }
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<CommentResponse> content = rows.stream().limit(PAGE_SIZE)
                .map(c -> CommentResponse.of(c, viewerId))
                .toList();
        long total = postCommentRepository.countByPostId(postId);
        int totalPages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
        return new PageResponse<>(content, page == null ? 0 : page, PAGE_SIZE, total, totalPages, hasNext);
    }

    /**
     * PUB-08-01 댓글 작성. 공개 게시물에만, 같은 회원은 10초에 1개("잠시 후 다시 등록해 주세요.", 남은 시간은 Retry-After).
     * 성공하면 게시물 작성자에게 새 댓글 알림(PUB-08-05, 본인 댓글이면 없음).
     */
    @Transactional
    public CommentResponse write(Long postId, CommentRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMENT_RESTRICTED);
        PublicPost post = visiblePost(postId);
        String content = checkContent(req.content());

        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime last = postCommentRepository.findLastWrittenAt(memberId);
        if (last != null && last.plus(WRITE_INTERVAL).isAfter(now)) {
            throw new BusinessException(ErrorCode.COMMENT_TOO_FAST, Duration.between(now, last.plus(WRITE_INTERVAL)));
        }

        PostComment saved = postCommentRepository.save(PostComment.write(postId, memberId, content));
        if (shouldNotify(post, memberId)) {
            notifyNewComment(post, saved);
        }
        return CommentResponse.of(saved, memberId);
    }

    /**
     * PUB-08-03 본인 댓글 수정 → "수정됨". 볼 수 없는 게시물의 댓글은 고칠 수 없다.
     * 정한 내용(11/27): 이미 지워진 댓글이면 공통 "이미 삭제되었거나 볼 수 없는 내용이에요."
     */
    @Transactional
    public CommentResponse edit(Long commentId, CommentRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMENT_EDIT_RESTRICTED);
        PostComment comment = findComment(commentId);
        if (!memberId.equals(comment.getAuthorId())) {
            throw new BusinessException(ErrorCode.COMMENT_EDIT_FORBIDDEN);
        }
        visiblePost(comment.getPostId());
        comment.edit(checkContent(req.content()));
        return CommentResponse.of(comment, memberId);
    }

    /**
     * PUB-08-04 본인 댓글 삭제(되돌릴 수 없음). 게시물 작성자도 남의 댓글은 못 지운다(신고로 처리).
     * 정한 내용(11/27): 삭제는 게시물이 지금 보이는지 따지지 않는다(북마크 해제와 같음).
     */
    @Transactional
    public void delete(Long commentId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        PostComment comment = findComment(commentId);
        if (!memberId.equals(comment.getAuthorId())) {
            throw new BusinessException(ErrorCode.COMMENT_DELETE_FORBIDDEN);
        }
        postCommentRepository.delete(comment);
    }

    // PUB-08-05 본인 게시물에 본인이 단 댓글이면 알림 없음 (작성자 정보가 없어도 없음)
    static boolean shouldNotify(PublicPost post, Long writerId) {
        return post.getAuthorId() != null && !post.getAuthorId().equals(writerId);
    }

    /**
     * PUB-08-05 새 댓글 알림: 게시물 작성자에게, 대상 POST_COMMENT·댓글 ID만 넘긴다
     * (미리보기 "'{공개용 제목}'에 새 댓글: {앞 30자}..."는 A가 보여 줄 때 원문에서 읽는다).
     * 알림 저장이 실패해도 댓글 등록은 실패시키지 않는다(AGENTS §6).
     * TODO(A에 요청): NotificationService가 생기면 여기서 부른다. 종류 POST_COMMENT, 받는 사람 post.getAuthorId().
     */
    private void notifyNewComment(PublicPost post, PostComment comment) {
        // 아직 A의 NotificationService가 없어 저장하지 않는다
    }

    private PostComment findComment(Long commentId) {
        return postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
    }

    // 공개 게시물(공개·관리자 숨김 아님·원래 여행 휴지통 아님)만
    private PublicPost visiblePost(Long postId) {
        Optional<PublicPost> post = publicPostRepository.findById(postId).filter(publicPostQueryService::isVisible);
        return post.orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
    }

    /**
     * PUB-08-01 댓글 내용: 줄바꿈만 허용하고 다른 제어문자는 공백 1칸, 앞뒤 공백 제거.
     * 비거나 500자(이모지 1글자) 넘으면 INVALID_INPUT(화면에서 막는 값).
     */
    static String checkContent(String raw) {
        String content = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n]]+", " ")
                .strip();
        if (content.isEmpty() || content.codePointCount(0, content.length()) > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return content;
    }
}
