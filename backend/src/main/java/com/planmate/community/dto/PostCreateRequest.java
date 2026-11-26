package com.planmate.community.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUB-01-03 게시 입력값 (contracts/community.yaml PostCreateRequest). 미리보기한 공개 정보.
 * 글자 수(이모지 1글자)·제어문자는 PublicPostService가 검사·정리한다.
 * 사진은 형식·저장 위치가 정해지지 않아 아직 받지 않는다. TODO(팀 결정)
 */
public record PostCreateRequest(
        String requestKey, // 같은 요청 두 번 처리 방지 (1~64자, UUID 권장)
        @NotBlank(message = "공개용 제목을 입력해 주세요.") String title, // 1~40자
        String intro) { // 0~100자, 줄바꿈 가능
}
