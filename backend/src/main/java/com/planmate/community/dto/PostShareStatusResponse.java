package com.planmate.community.dto;

/**
 * PUB-01-01 공유하기 버튼 상태·입력 칸 (contracts/community.yaml PostShareStatusResponse).
 * PUB-02-03 내 여행 카드의 "공개" / "비공개" 표시에도 쓴다(B가 PublicPostService.findShareStatus를 부름).
 */
public record PostShareStatusResponse(
        Status status,
        Long postId, // 게시물이 없으면 null
        String title, // 비공개로 바꾼 게시물이면 예전 공개용 제목 (다시 공개할 때 입력 칸에 넣음)
        String intro) {

    /**
     * NONE = 공유한 적 없음, PUBLIC = 공개 중(공유하기 비활성), PRIVATE = 소유자가 비공개로 바꿈(다시 공유 가능),
     * ADMIN_HIDDEN = 관리자 비공개(다시 공유 불가). 카드 글자는 PUBLIC만 "공개", 나머지는 "비공개".
     */
    public enum Status { NONE, PUBLIC, PRIVATE, ADMIN_HIDDEN }

    public static final PostShareStatusResponse NONE = new PostShareStatusResponse(Status.NONE, null, null, null);
}
