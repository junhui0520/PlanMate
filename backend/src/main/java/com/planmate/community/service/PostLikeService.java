package com.planmate.community.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.community.dto.LikeResponse;
import com.planmate.community.entity.PostLike;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostLikeRepository;
import com.planmate.community.repository.PublicPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요·취소 (PUB-05). 회원당 게시물 하나에 좋아요 하나.
 * 게시물 행을 잠그고(findByIdForUpdate) "있는지 확인 → 넣기·지우기 → 좋아요 수 바꾸기"를 한 번에 해서,
 * 빠르게 여러 번 눌러도 좋아요 수는 1만 바뀐다.
 * 권한: 로그인한 정상 회원(이용 제한 회원 불가). 내 게시물에도 좋아요 할 수 있다(PUB-04-03).
 * TODO(A에 요청): 탈퇴 절차에서 본인 좋아요 삭제·좋아요 수 감소를 부를 함수는 A와 이름을 맞춘 뒤 만든다.
 */
@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PublicPostRepository publicPostRepository;
    private final PostLikeRepository postLikeRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final PublicPostQueryService publicPostQueryService;

    /**
     * PUB-05-01 좋아요. 대상은 공개 게시물만("볼 수 없는 게시물이에요.").
     * 이미 눌렀으면 그대로 성공(숫자 안 오름).
     */
    @Transactional
    public LikeResponse like(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.LIKE_RESTRICTED);
        PublicPost post = publicPostRepository.findByIdForUpdate(postId)
                .filter(publicPostQueryService::isVisible)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));

        if (!postLikeRepository.existsByPostIdAndMemberId(postId, memberId)) {
            postLikeRepository.save(PostLike.create(postId, memberId));
            post.increaseLikeCount(); // 좋아요순 정렬에 반영(PUB-03-03)
        }
        return new LikeResponse(true, post.getLikeCount());
    }

    /**
     * PUB-05-02 좋아요 취소. 좋아요가 없으면 그대로 성공(숫자 안 바뀜), 좋아요 수는 0 아래로 안 내려감.
     * 정한 내용(11/26): 취소는 게시물이 지금 보이는지 따지지 않는다(저장할 때만 공개 조건).
     * 게시물이 아예 없으면(영구 삭제) "볼 수 없는 게시물이에요."
     */
    @Transactional
    public LikeResponse unlike(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        PublicPost post = publicPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));

        postLikeRepository.findByPostIdAndMemberId(postId, memberId).ifPresent(like -> {
            postLikeRepository.delete(like);
            post.decreaseLikeCount();
        });
        return new LikeResponse(false, post.getLikeCount());
    }
}
