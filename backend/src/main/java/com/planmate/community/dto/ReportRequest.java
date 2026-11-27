package com.planmate.community.dto;

import com.planmate.community.entity.Report;

import jakarta.validation.constraints.NotNull;

/**
 * PUB-09-01 신고 입력값 (contracts/community.yaml ReportRequest). 대상은 주소로 정한다(게시물 / 댓글).
 * 상세 내용은 기타일 때만 1~300자(줄바꿈 허용, 다른 제어문자는 공백 1칸) — ReportService가 검사·정리한다.
 * 기타가 아닌 사유에 상세 내용이 오면 저장하지 않는다(화면에 그 칸이 없음, 정한 내용 11/27).
 */
public record ReportRequest(
        @NotNull(message = "입력값을 확인해 주세요.") Report.Reason reason,
        String detail) {
}
