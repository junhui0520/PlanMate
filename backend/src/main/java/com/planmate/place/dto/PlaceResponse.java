package com.planmate.place.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 표준화한 장소 정보 = 공통 장소 형식 (PLC-01-02, 팀 공통 C02, contracts/place.yaml PlaceResponse).
 * 화면과 A(보관함)·B(공동 후보·일정)·D(공개 화면)에 넘기는 모양이다. 이 자체는 저장 테이블이 아니다.
 * 카카오·구글 응답을 이 모양으로 바꾸는 일은 PlaceSearchService가 한다.
 *
 * - 같은 장소 판단 = provider + placeId (B의 후보 중복 판단과 같은 기준)
 * - lat·lng가 null이면 '위치 미확인' (목록에 "위치 미확인" 표시, 지도 핀 생략)
 */
public record PlaceResponse(
        Provider provider,
        String placeId,          // 제공자 고유값. ADDRESS면 null
        String placeName,        // ADDRESS면 null
        String address,          // 도로명 우선
        BigDecimal lat,          // 위도, 소수 7자리. 위치 미확인이면 null
        BigDecimal lng,          // 경도, 소수 7자리. 위치 미확인이면 null
        Category category,       // ADDRESS면 null
        LocalDateTime fetchedAt  // 조회 시각 (서버 시각)
) {

    /** 제공자. KAKAO = 국내, GOOGLE = 해외, ADDRESS = 장소 ID 없는 주소 변환 결과 (후보·일정에 못 넣고 개인 출발 장소에만 사용) */
    public enum Provider { KAKAO, GOOGLE, ADDRESS }

    /** 내부 카테고리 코드: 관광명소 / 식당 / 카페 / 쇼핑 / 숙소 / 교통(공항·역) / 기타 */
    public enum Category { ATTRACTION, FOOD, CAFE, SHOPPING, LODGING, TRANSPORT, ETC }
}
