package com.planmate.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.planmate.community.entity.PublicPost;

/**
 * PUB-04-01 공개 여행 상세 (contracts/community.yaml PostDetailResponse).
 * 공개 허용 값만 담는다: 실제 날짜·경비·참여자·메모·집<->공항·고정 상태·구간 요금·날씨는 칸 자체가 없다.
 */
public record PostDetailResponse(
        Long postId,
        String title,
        String intro,
        String imageUrl,
        String cityName,
        String mapProvider, // KAKAO / GOOGLE
        Integer days,
        String authorNickname,
        Integer likeCount,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt,
        Viewer viewer,
        List<PublicDay> schedule) {

    /** 보는 사람 기준 표시 값 (PUB-04-03). 비회원이면 모두 false */
    public record Viewer(boolean loggedIn, boolean mine, boolean liked, boolean bookmarked, boolean communityWritable) {

        public static final Viewer GUEST = new Viewer(false, false, false, false, false);
    }

    /** N일차 (실제 날짜 없음) */
    public record PublicDay(int dayNo, List<PublicPlace> places) {
    }

    /** 공개 장소 카드 한 개. 시각은 "HH:mm" 여행 도시 현지 시각 */
    public record PublicPlace(int order, String name, String category, String startTime, String endTime,
            Integer stayMinutes, boolean lodging, BigDecimal lat, BigDecimal lng, PublicSegment segmentToNext) {
    }

    /** 다음 장소까지 이동 (요금 없음). 임시 모양 — TODO(C와 맞추기, RTE-02-02) */
    public record PublicSegment(String mode, Integer durationMinutes, boolean estimated, Integer distanceMeters,
            String transitSummary, String walkSummary) {
    }

    /**
     * TODO(B에 요청): cityName·mapProvider·days는 원래 여행(TRIP·CITY), schedule은 원래 여행의 현재 일정(공개 허용 값만).
     * TODO(C에 요청): schedule 안 구간(segmentToNext)은 C 구간 정보(요금 제외).
     * TODO(A에 요청): authorNickname은 MemberService 닉네임 조회.
     * 그때까지는 null·빈 일정으로 보낸다(주소를 열기 전이라 화면 영향 없음).
     */
    public static PostDetailResponse of(PublicPost post, Viewer viewer) {
        return new PostDetailResponse(post.getPostId(), post.getPostTitle(), post.getPostIntro(),
                post.getPostImageUrl(), null, null, null, null, post.getLikeCount(), post.getPublishedAt(),
                post.getUpdatedAt(), viewer, List.of());
    }
}
