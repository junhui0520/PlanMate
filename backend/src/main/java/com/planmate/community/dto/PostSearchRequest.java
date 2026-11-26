package com.planmate.community.dto;

/**
 * PUB-03-01·02·03 공개 목록 조건 (contracts/community.yaml GET /api/community/posts 매개변수).
 * 검사(정렬 값·글자 수·범위)는 PublicPostQueryService가 한다. 없는 값은 null.
 */
public record PostSearchRequest(
        String sort, // LATEST(기본) / LIKES
        String q, // 제목 검색어 0~50자
        Long cityId, // 도시 (null = 전체)
        Integer days, // 여행 일수 1~7 (null = 전체)
        Long after, // 지금까지 받은 마지막 카드의 postId (null = 첫 10개)
        Integer page) { // 몇 번째 요청인지(0부터), 응답에 그대로 돌려줌
}
