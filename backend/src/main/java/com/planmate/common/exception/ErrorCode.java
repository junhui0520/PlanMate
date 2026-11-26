package com.planmate.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/*
 * 에러 코드·안내 문구 목록.
 * 각 파트는 기능정의서 '예외·오류 안내 문구'를 그대로 자기 구역에 추가한다. 공통 파일이라 팀에 공유 후 수정.
 * 응답의 code에는 enum 이름(name())을 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청이에요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),

    // ===== 공통 접근 제어 (ACC-07) — 상수만 둠, 판정 로직은 pjh/security에서 만든다 =====
    // ACC-07-01 로그인·계정 상태
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    COMMUNITY_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 커뮤니티 활동을 할 수 없어요."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정이에요."),
    // ACC-07-02 여행·개인 자료 접근
    RESOURCE_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND, "이미 삭제되었거나 볼 수 없는 내용이에요."),
    EDIT_PERMISSION_CHANGED(HttpStatus.FORBIDDEN, "편집 권한이 변경됐어요. 최신 상태를 확인해 주세요."),
    // ACC-07-03 관리자 검사
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있어요."),
    ADMIN_REAUTH_REQUIRED(HttpStatus.FORBIDDEN, "보안을 위해 본인 확인을 다시 진행해 주세요."),

    // ===== A 파트 (auth·member·participant·notification·admin·savedplace·weather·checklist·cs) =====

    // ===== B 파트 (trip·schedule·collab) =====

    // ===== C 파트 (place·ai·route) =====

    // ===== D 파트 (community·budget) =====

    ;

    private final HttpStatus status;
    private final String message;
}
