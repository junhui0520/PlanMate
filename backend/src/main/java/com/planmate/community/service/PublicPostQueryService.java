package com.planmate.community.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.PageResponse;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.TripStatusReader;
import com.planmate.common.security.TripStatusReader.TripStatus;
import com.planmate.community.dto.PostDetailResponse;
import com.planmate.community.dto.PostListResponse;
import com.planmate.community.dto.PostSearchRequest;
import com.planmate.community.entity.PublicPost;
import com.planmate.community.repository.PublicPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공개 목록·검색·정렬·상세 (PUB-03·04). 로그인 없이 볼 수 있다.
 * 보이는 조건(목록·상세 같음): 공개이고, 관리자 숨김이 아니고, 원래 여행이 휴지통에 있지 않을 때만.
 * - 공개·관리자 숨김은 PUBLIC_POST(D)에서 고른다.
 * - 휴지통 여부는 TRIP(B)이라 B의 TripStatusReader로 묻고 거른다. IS_PUBLIC은 바꾸지 않는다
 *   (11/20 ERD 결정: 휴지통 숨김 칸을 따로 두지 않음, 복원되면 그대로 다시 보임).
 */
@Service
@RequiredArgsConstructor
public class PublicPostQueryService {

    static final int PAGE_SIZE = 10; // PUB-03-01 한 번에 10개
    static final int KEYWORD_MAX = 50; // PUB-03-02 제목 검색어 0~50자
    static final int DAYS_MAX = 7; // PUB-03-02 일수 1~7

    private final PublicPostRepository publicPostRepository;
    private final AccountStatusChecker accountStatusChecker;
    // TODO(B에 요청): TripStatusReader 구현(TRIP.STATUS 조회). 없으면 목록·상세를 부를 때 오류를 낸다(TripAccessChecker와 같음).
    private final ObjectProvider<TripStatusReader> tripStatusReaderProvider;

    private enum Sort { LATEST, LIKES }

    /**
     * PUB-03-01·02·03 공개 목록 10개.
     * 마지막 카드(after)의 정렬 값 다음부터 이어 받아, 그사이 새 게시물이 생겨도 같은 카드가 두 번 나오지 않는다.
     * 휴지통 여행의 게시물은 읽은 뒤 빼므로, 10개가 찰 때까지 다음 묶음을 더 읽는다.
     * TODO(B에 요청): cityId·days 검색은 원래 여행(TRIP·CITY) 값이라 지금은 범위만 검사하고 거르지 않는다.
     *                 여행 기본 정보(도시·시작일·종료일)를 받을 방법이 생기면 거른다.
     */
    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getPublicPosts(PostSearchRequest req) {
        Sort sort = readSort(req.sort());
        String titlePattern = titlePattern(req.q());
        checkRange(req.cityId(), req.days(), req.page());
        int page = req.page() == null ? 0 : req.page();

        // 이어 받기 기준: 마지막 카드 (없는 게시물이면 처음부터 주면 겹칠 수 있어 INVALID_INPUT, 정한 내용 11/26)
        PublicPost cursor = req.after() == null ? null
                : publicPostRepository.findById(req.after())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        // 10개 + 1개(다음이 있는지 확인용)가 찰 때까지 읽으면서 휴지통 여행은 뺀다
        List<PublicPost> picked = new ArrayList<>();
        while (picked.size() <= PAGE_SIZE) {
            List<PublicPost> batch = fetch(sort, titlePattern, cursor);
            for (PublicPost post : batch) {
                if (picked.size() > PAGE_SIZE) break;
                if (!isTrashed(post.getTripId())) picked.add(post);
            }
            if (batch.size() < PAGE_SIZE + 1) break; // 더 읽을 게시물 없음
            cursor = batch.get(batch.size() - 1);
        }
        boolean hasNext = picked.size() > PAGE_SIZE;
        List<PostListResponse> content = picked.stream().limit(PAGE_SIZE).map(PostListResponse::from).toList();

        // "검색 결과 n개": 지금 조건에 맞는 전체 수 (휴지통 여행 제외)
        long total = publicPostRepository.findOpenTripIds(titlePattern).stream()
                .filter(tripId -> !isTrashed(tripId))
                .count();
        int totalPages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
        return new PageResponse<>(content, page, PAGE_SIZE, total, totalPages, hasNext);
    }

    /**
     * PUB-04-01·03 공개 여행 상세.
     * 보이는 조건에 맞지 않거나 없는 게시물이면 "볼 수 없는 게시물이에요."
     * 로그인한 회원이면 viewer에 내 게시물·이용 제한 여부를 채운다(비회원이면 모두 false).
     */
    @Transactional(readOnly = true)
    public PostDetailResponse getPublicPostDetail(Long postId) {
        PublicPost post = publicPostRepository.findById(postId)
                .filter(PublicPost::isOpen)
                .filter(found -> !isTrashed(found.getTripId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_POST_NOT_VISIBLE));
        return PostDetailResponse.of(post, viewer(post));
    }

    // PUB-04-03 보는 사람 기준 표시 값
    private PostDetailResponse.Viewer viewer(PublicPost post) {
        Optional<Long> memberId = accountStatusChecker.findLoginMemberId();
        if (memberId.isEmpty()) {
            return PostDetailResponse.Viewer.GUEST;
        }
        boolean mine = memberId.get().equals(post.getAuthorId());
        // TODO(D): liked·bookmarked는 PUB-05·06(POST_LIKE·POST_BOOKMARK)을 만들 때 채운다. 지금은 false.
        return new PostDetailResponse.Viewer(true, mine, false, false, isCommunityWritable(memberId.get()));
    }

    // 이용 제한 회원이면 false (버튼 모두 비활성, 커뮤니티는 보기만)
    // TODO(A에 요청): 참·거짓으로 돌려주는 함수가 생기면 바꾼다. 지금은 A 체커가 던지는 거절을 false로 바꾼다.
    private boolean isCommunityWritable(Long memberId) {
        try {
            accountStatusChecker.requireCommunityWritable(memberId);
            return true;
        } catch (BusinessException restricted) {
            return false;
        }
    }

    // 원래 여행이 휴지통(또는 없는 여행)이면 true → 목록·상세에서 뺀다
    private boolean isTrashed(Long tripId) {
        TripStatusReader reader = tripStatusReaderProvider.getIfAvailable();
        if (reader == null) {
            throw new IllegalStateException("TripStatusReader 구현 없음 — B 파트");
        }
        return reader.findStatus(tripId).map(status -> status != TripStatus.ACTIVE).orElse(true);
    }

    private List<PublicPost> fetch(Sort sort, String titlePattern, PublicPost cursor) {
        Limit limit = Limit.of(PAGE_SIZE + 1);
        if (sort == Sort.LIKES) {
            return cursor == null
                    ? publicPostRepository.findMostLiked(titlePattern, limit)
                    : publicPostRepository.findMostLikedAfter(titlePattern, cursor.getLikeCount(),
                            cursor.getPublishedAt(), cursor.getPostId(), limit);
        }
        return cursor == null
                ? publicPostRepository.findLatest(titlePattern, limit)
                : publicPostRepository.findLatestAfter(titlePattern, cursor.getPublishedAt(), cursor.getPostId(), limit);
    }

    // PUB-03-03 정렬: 없으면 최신순, 그 밖의 값은 INVALID_INPUT
    private static Sort readSort(String raw) {
        if (raw == null || raw.isEmpty()) return Sort.LATEST;
        try {
            return Sort.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /**
     * PUB-03-02 제목 검색어 → LIKE 패턴.
     * 앞뒤 공백 제거 후 비면 검색어 없음("%"), 50자 초과면 INVALID_INPUT(화면은 50자에서 막음).
     * 일부 일치·대소문자 구분 안 함, %·_는 글자 그대로 찾는다(앞에 \).
     */
    static String titlePattern(String raw) {
        String q = raw == null ? "" : raw.strip();
        if (q.isEmpty()) return "%";
        if (q.codePointCount(0, q.length()) > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String escaped = q.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // 도시 ID 1 이상, 일수 1~7, page 0 이상
    private static void checkRange(Long cityId, Integer days, Integer page) {
        if ((cityId != null && cityId < 1) || (days != null && (days < 1 || days > DAYS_MAX))
                || (page != null && page < 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
