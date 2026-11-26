package com.planmate.community.dto;

import java.util.List;

/**
 * PUB-01-02 공개 미리보기 (contracts/community.yaml PostPreviewResponse).
 * 공개용 제목·소개는 아직 저장 전이라 화면이 들고 있고, 서버는 원래 여행에서 공개 허용 값만 골라 준다.
 * 실제 날짜·경비·참여자·메모·집<->공항·고정 상태·AI 조건·구간 요금은 칸 자체가 없다.
 */
public record PostPreviewResponse(
        String cityName,
        String mapProvider, // KAKAO / GOOGLE
        Integer days, // 여행 일수 (N일차로만 표시)
        String authorNickname,
        List<PostDetailResponse.PublicDay> schedule) {
}
