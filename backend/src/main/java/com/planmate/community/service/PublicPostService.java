package com.planmate.community.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.duplicate.DuplicateRequestChecker;
import com.planmate.common.duplicate.RequestLog;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripAccessChecker;
import com.planmate.common.security.TripStatusReader;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.community.dto.PostCreateRequest;
import com.planmate.community.dto.MyPostResponse;
import com.planmate.community.dto.PostCreateResponse;
import com.planmate.community.dto.PostDetailResponse;
import com.planmate.community.dto.PostPreviewResponse;
import com.planmate.community.dto.PostShareStatusResponse;
import com.planmate.community.dto.PostUpdateRequest;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PostCommentRepository;
import com.planmate.community.repository.PublicPostRepository;
import com.planmate.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * 공유하기·미리보기·게시·비공개 전환·제목·소개 수정 (PUB-01, PUB-02).
 * 권한: 요청 시점의 여행 소유자, 이용 제한 회원 불가, 여행이 휴지통이 아님.
 * 공개 일정은 따로 복사하지 않고 원래 여행의 현재 일정을 그대로 보여 준다(PUB-01-03).
 */
@Service
@RequiredArgsConstructor
public class PublicPostService {

    static final int TITLE_MAX = 40; // PUB-01-01 공개용 제목 1~40자
    static final int INTRO_MAX = 100; // PUB-01-01 소개 0~100자
    static final int MY_POSTS_PAGE_SIZE = 10; // PUB-02-04 10개씩

    private final PublicPostRepository publicPostRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final TripAccessChecker tripAccessChecker;
    private final TripStatusReader tripStatusReader; // B: TRIP.STATUS
    private final TripService tripService; // B: 여행 일수 (findBasicInfo)
    private final DuplicateRequestChecker duplicateRequestChecker;
    private final PublicPostQueryService publicPostQueryService;
    private final PostCommentRepository postCommentRepository;

    /**
     * 여행의 게시 상태 (권한 검사 없음). PUB-02-03 내 여행 카드 표시용으로 B가 부른다.
     * PUBLIC은 공개이고 관리자 숨김이 아닐 때만(휴지통 여행 카드는 B가 보여 주지 않음).
     */
    @Transactional(readOnly = true)
    public PostShareStatusResponse findShareStatus(Long tripId) {
        return publicPostRepository.findByTripId(tripId)
                .map(PublicPostService::statusOf)
                .orElse(PostShareStatusResponse.NONE);
    }

    /**
     * PUB-01-01 공유하기 창을 열기 전: 버튼 상태와 입력 칸에 넣을 값.
     * 비공개로 바꾼 게시물이면 예전 공개용 제목·소개를 준다(처음이면 두 칸 모두 빈칸).
     * 권한: 소유자(이용 제한 회원도 상태는 볼 수 있고, 게시할 때 막힌다).
     */
    @Transactional(readOnly = true)
    public PostShareStatusResponse getShareStatus(Long tripId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        tripAccessChecker.requireOwner(tripId, memberId, ErrorCode.POST_OWNER_ONLY);
        return findShareStatus(tripId);
    }

    /**
     * PUB-01-02 공개 미리보기: 원래 여행에서 공개 허용 값만. 열기만 해서는 아무것도 저장하지 않는다.
     * TODO(B에 요청): 저장된 시간표 조회(SCH-01-01)가 생기면 schedule을 채우고,
     *                 방문 일정이 하나도 없으면 "일정을 추가한 뒤 공유할 수 있어요."로 막는다(지금은 검사하지 못함).
     * TODO(B에 요청): 도시 이름·지도 제공자(CITY)는 여행 기본 정보에 없어 아직 null.
     * TODO(A에 요청): 작성자 닉네임(MemberService)은 아직 null.
     */
    @Transactional(readOnly = true)
    public PostPreviewResponse getPreview(Long tripId) {
        checkCanPublish(tripId);
        Integer days = tripService.findBasicInfo(tripId)
                .map(trip -> (int) ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
        return new PostPreviewResponse(null, null, days, null, List.of());
    }

    /**
     * PUB-01-03 게시. 서버가 권한·여행 상태·게시물 개수를 다시 검사한다.
     * - 여행당 게시물 1개. 공개 중이면 "이미 공개한 여행이에요.", 관리자 비공개면 다시 공유 불가.
     * - 비공개로 바꾼 게시물이 있으면 새로 만들지 않고 그 게시물을 다시 공개(좋아요·댓글 유지).
     * - 같은 요청 식별값은 한 번만 처리(두 번 눌러도 게시물 1개).
     * TODO(B에 요청): 방문 일정이 없으면 게시를 막는 검사(getPreview와 같음).
     */
    @Transactional
    public PostCreateResponse publish(Long tripId, PostCreateRequest req) {
        Long memberId = checkCanPublish(tripId);
        Optional<RequestLog> done = duplicateRequestChecker.findProcessed(req.requestKey(), memberId);
        if (done.isPresent()) {
            return publicPostRepository.findByTripId(done.get().getTripId())
                    .map(post -> new PostCreateResponse(post.getPostId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_REQUEST));
        }
        String title = checkTitle(req.title());
        String intro = checkIntro(req.intro());

        Optional<PublicPost> existing = publicPostRepository.findByTripId(tripId);
        PublicPost post;
        if (existing.isPresent()) {
            post = existing.get();
            if (Boolean.TRUE.equals(post.getAdminHidden())) {
                throw new BusinessException(ErrorCode.POST_ADMIN_HIDDEN);
            }
            if (Boolean.TRUE.equals(post.getIsPublic())) {
                throw new BusinessException(ErrorCode.POST_ALREADY_PUBLIC);
            }
            post.republish(memberId, title, intro);
        } else {
            try {
                post = publicPostRepository.saveAndFlush(PublicPost.publish(tripId, memberId, title, intro));
            } catch (DataIntegrityViolationException e) {
                // 같은 여행을 동시에 게시하면 UQ_PUBLIC_POST_01 위반 (AGENTS §6: 맞는 ErrorCode로 바꿈)
                throw new BusinessException(ErrorCode.POST_ALREADY_PUBLIC);
            }
        }
        duplicateRequestChecker.record(req.requestKey(), memberId, tripId);
        return new PostCreateResponse(post.getPostId());
    }

    /**
     * PUB-02-05 공개용 제목·소개 수정. 공개 중인 게시물만, 일정·좋아요·처음 게시 시각은 그대로.
     * 저장하면 수정 시각이 바뀐다. 결과는 고친 뒤의 공개 상세.
     * 정한 내용(11/27): 소유자가 아니면 공통 "편집 권한이 없어요."
     */
    @Transactional
    public PostDetailResponse updateInfo(Long postId, PostUpdateRequest req) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        PublicPost post = publicPostRepository.findById(postId)
                .filter(PublicPost::isOpen)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
        tripAccessChecker.requireOwner(post.getTripId(), memberId, ErrorCode.EDIT_PERMISSION_DENIED);
        post.updateInfo(checkTitle(req.title()), checkIntro(req.intro()));
        return publicPostQueryService.getPublicPostDetail(postId);
    }

    /**
     * PUB-02-02 비공개 전환. 원래 여행·좋아요·댓글·북마크는 그대로, 이미 복제된 여행도 그대로.
     * 관리자 숨김 상태여도 할 수 있고, 이미 비공개면 그대로 성공.
     */
    @Transactional
    public void makePrivate(Long postId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.COMMUNITY_FEATURE_RESTRICTED);
        PublicPost post = publicPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
        tripAccessChecker.requireOwner(post.getTripId(), memberId, ErrorCode.EDIT_PERMISSION_DENIED);
        post.makePrivate();
    }

    /**
     * PUB-02-04 내 게시글: 내가 작성자인 게시물(휴지통 여행 제외), 게시·수정 시각 최신순 10개.
     * 마지막 카드(after) 다음부터 이어 받는다. page는 화면이 몇 번째로 불렀는지(0부터) 그대로 돌려준다.
     * 이용 제한 회원도 목록은 볼 수 있고, 카드의 communityWritable = false로 버튼을 비활성으로 보여 준다.
     */
    @Transactional(readOnly = true)
    public PageResponse<MyPostResponse> getMyPosts(Long after, Integer page) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        if (page != null && page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        // 이어 받기 기준은 내 게시물만 (없거나 남의 게시물이면 INVALID_INPUT)
        PublicPost cursor = after == null ? null
                : publicPostRepository.findById(after)
                        .filter(p -> memberId.equals(p.getAuthorId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        // 10개 + 1개(다음이 있는지 확인용)가 찰 때까지 읽으면서 휴지통 여행은 뺀다
        Limit limit = Limit.of(MY_POSTS_PAGE_SIZE + 1);
        List<PublicPost> picked = new ArrayList<>();
        while (picked.size() <= MY_POSTS_PAGE_SIZE) {
            List<PublicPost> batch = cursor == null
                    ? publicPostRepository.findMine(memberId, limit)
                    : publicPostRepository.findMineAfter(memberId, sortAt(cursor), cursor.getPostId(), limit);
            for (PublicPost post : batch) {
                if (picked.size() > MY_POSTS_PAGE_SIZE) break;
                if (!publicPostQueryService.isTrashed(post.getTripId())) picked.add(post);
            }
            if (batch.size() < MY_POSTS_PAGE_SIZE + 1) break;
            cursor = batch.get(batch.size() - 1);
        }
        boolean hasNext = picked.size() > MY_POSTS_PAGE_SIZE;
        boolean writable = publicPostQueryService.isCommunityWritable(memberId);
        List<MyPostResponse> content = picked.stream().limit(MY_POSTS_PAGE_SIZE)
                .map(post -> new MyPostResponse(post.getPostId(), post.getTripId(), post.getPostTitle(),
                        statusOf(post).status(), post.getPublishedAt(), post.getUpdatedAt(), post.getLikeCount(),
                        postCommentRepository.countByPostId(post.getPostId()), writable))
                .toList();

        long total = publicPostRepository.findTripIdsByAuthorId(memberId).stream()
                .filter(tripId -> !publicPostQueryService.isTrashed(tripId))
                .count();
        int totalPages = (int) ((total + MY_POSTS_PAGE_SIZE - 1) / MY_POSTS_PAGE_SIZE);
        return new PageResponse<>(content, page == null ? 0 : page, MY_POSTS_PAGE_SIZE, total, totalPages, hasNext);
    }

    // PUB-02-04 정렬 기준: 고쳤으면 수정 시각, 아니면 처음 게시 시각
    private static LocalDateTime sortAt(PublicPost post) {
        return post.getUpdatedAt() != null ? post.getUpdatedAt() : post.getPublishedAt();
    }

    // 공유·미리보기·게시 공통 검사: 로그인 → 이용 제한 → 휴지통 → 소유자. 통과하면 로그인 회원 ID
    private Long checkCanPublish(Long tripId) {
        Long memberId = accountStatusChecker.requireLoginMemberId();
        accountStatusChecker.requireCommunityWritable(memberId, ErrorCode.POST_RESTRICTED);
        TripStatus status = tripStatusReader.findStatus(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_ACCESSIBLE));
        if (status == TripStatus.TRASHED) {
            throw new BusinessException(ErrorCode.POST_TRIP_TRASHED);
        }
        tripAccessChecker.requireOwner(tripId, memberId, ErrorCode.POST_OWNER_ONLY);
        return memberId;
    }

    private static PostShareStatusResponse statusOf(PublicPost post) {
        PostShareStatusResponse.Status status = Boolean.TRUE.equals(post.getAdminHidden())
                ? PostShareStatusResponse.Status.ADMIN_HIDDEN
                : Boolean.TRUE.equals(post.getIsPublic())
                        ? PostShareStatusResponse.Status.PUBLIC
                        : PostShareStatusResponse.Status.PRIVATE;
        return new PostShareStatusResponse(status, post.getPostId(), post.getPostTitle(), post.getPostIntro());
    }

    /**
     * PUB-01-01 공개용 제목: 제어문자(줄바꿈·탭 등)는 공백 1칸으로 바꾸고 앞뒤 공백을 지운다(TRP-01-01 규칙과 같음).
     * 비면 "공개용 제목을 입력해 주세요.", 40자(이모지 1글자) 넘으면 INVALID_INPUT(화면에서 막는 값).
     */
    static String checkTitle(String raw) {
        String title = raw == null ? "" : raw.replaceAll("\\p{Cc}+", " ").strip();
        if (title.isEmpty()) {
            throw new BusinessException(ErrorCode.POST_TITLE_REQUIRED);
        }
        if (title.codePointCount(0, title.length()) > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return title;
    }

    /**
     * PUB-01-01 소개: 줄바꿈만 허용하고 다른 제어문자는 공백 1칸으로, 앞뒤 공백 제거, 비면 null.
     * 100자(이모지 1글자) 넘으면 INVALID_INPUT.
     */
    static String checkIntro(String raw) {
        if (raw == null) {
            return null;
        }
        String intro = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n]]+", " ")
                .strip();
        if (intro.isEmpty()) {
            return null;
        }
        if (intro.codePointCount(0, intro.length()) > INTRO_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return intro;
    }
}
