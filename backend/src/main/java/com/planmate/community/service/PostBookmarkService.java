package com.planmate.community.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.BookmarkResponse;
import com.planmate.community.dto.MyBookmarkResponse;
import com.planmate.community.dto.PostListResponse;
import com.planmate.community.entity.PostBookmark;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostBookmarkRepository;
import com.planmate.community.repository.PublicPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 북마크 저장·해제·내 북마크 목록 (PUB-06). 좋아요·장소 보관함·복제와 다른 동작.
 * 저장·해제: 로그인한 정상 회원(이용 제한 회원 불가). 내 북마크 보기는 이용 제한 회원도 가능.
 * TODO(A에 요청): 탈퇴 절차에서 본인 북마크 삭제를 부를 함수는 A와 이름을 맞춘 뒤 만든다.
 */
@Service
@RequiredArgsConstructor
public class PostBookmarkService {

    static final int BOOKMARK_LIMIT = 200; // PUB-06-01 회원당 최대 200개
    static final int PAGE_SIZE = 20; // PUB-06-02 20개씩

    private final PostBookmarkRepository postBookmarkRepository;
    private final PublicPostRepository publicPostRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final PublicPostQueryService publicPostQueryService;

    /**
     * PUB-06-01 북마크 저장. 공개 게시물만("볼 수 없는 게시물이에요."), 같은 게시물은 1개(두 번 눌러도 그대로 성공).
     * 게시물 행을 잠가 같은 게시물 연타가 차례로 처리되게 한다.
     */
    @Transactional
    public BookmarkResponse bookmark(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        publicPostRepository.findByIdForUpdate(postId)
                .filter(publicPostQueryService::isVisible)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));

        if (postBookmarkRepository.existsByPostIdAndMemberId(postId, memberId)) {
            return new BookmarkResponse(true);
        }
        if (postBookmarkRepository.countByMemberId(memberId) >= BOOKMARK_LIMIT) {
            throw new BusinessException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
        }
        postBookmarkRepository.save(PostBookmark.create(postId, memberId));
        return new BookmarkResponse(true);
    }

    /**
     * PUB-06-01 북마크 해제. 없으면 그대로 성공. 원래 게시물·좋아요 수는 바뀌지 않는다.
     * 볼 수 없게 된 게시물도 해제할 수 있다(PUB-06-02).
     */
    @Transactional
    public BookmarkResponse unbookmark(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        postBookmarkRepository.findByPostIdAndMemberId(postId, memberId).ifPresent(postBookmarkRepository::delete);
        return new BookmarkResponse(false);
    }

    /**
     * PUB-06-02 내 북마크 목록: 담은 시각 최신순 20개, 마지막 북마크(after) 다음부터 이어 받기.
     * 볼 수 없게 된 게시물은 카드만 남기고 내용 없이(visible = false) 보낸다.
     * page는 화면이 몇 번째로 불렀는지(0부터) 그대로 돌려준다. totalElements는 내 북마크 전체 수.
     */
    @Transactional(readOnly = true)
    public PageResponse<MyBookmarkResponse> getMyBookmarks(Long after, Integer page) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        if (page != null && page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Limit limit = Limit.of(PAGE_SIZE + 1); // 1개 더 읽어 다음이 있는지 확인
        List<PostBookmark> rows;
        if (after == null) {
            rows = postBookmarkRepository.findMine(memberId, limit);
        } else {
            // 이어 받기 기준은 내 북마크만 (없거나 남의 북마크면 INVALID_INPUT)
            PostBookmark last = postBookmarkRepository.findById(after)
                    .filter(b -> b.getMemberId().equals(memberId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
            rows = postBookmarkRepository.findMineAfter(memberId, last.getCreatedAt(), last.getBookmarkId(), limit);
        }
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<PostBookmark> pageRows = rows.stream().limit(PAGE_SIZE).toList();

        Map<Long, PublicPost> posts = publicPostRepository.findAllById(pageRows.stream().map(PostBookmark::getPostId).toList())
                .stream().collect(Collectors.toMap(PublicPost::getPostId, Function.identity()));
        List<MyBookmarkResponse> content = pageRows.stream().map(b -> {
            PublicPost post = posts.get(b.getPostId());
            boolean visible = post != null && publicPostQueryService.isVisible(post);
            return new MyBookmarkResponse(b.getBookmarkId(), b.getPostId(), visible,
                    visible ? PostListResponse.from(post) : null, b.getCreatedAt());
        }).toList();

        long total = postBookmarkRepository.countByMemberId(memberId);
        int totalPages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
        return new PageResponse<>(content, page == null ? 0 : page, PAGE_SIZE, total, totalPages, hasNext);
    }
}
