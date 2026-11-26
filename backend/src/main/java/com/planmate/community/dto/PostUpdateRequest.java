package com.planmate.community.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUB-02-05 공개용 제목·소개 수정값 (contracts/community.yaml PostUpdateRequest). 규칙은 게시(PUB-01-01)와 같다.
 */
public record PostUpdateRequest(
        @NotBlank(message = "공개용 제목을 입력해 주세요.") String title, // 1~40자
        String intro) { // 0~100자, 줄바꿈 가능
}
