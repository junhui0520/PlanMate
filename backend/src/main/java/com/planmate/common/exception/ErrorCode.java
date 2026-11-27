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
    // SCH·RTE·SUP·TRP 여러 기능의 '권한 없음' 공통 문구 (조회 참여자가 편집하려 할 때)
    EDIT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "편집 권한이 없어요."),
    // CSRF 토큰이 없거나 맞지 않음 (사용자 확정 11/26)
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),

    // ===== 공통 접근 제어 (ACC-07) — 판정은 common/security 체커 =====
    // ACC-07-01 로그인·계정 상태
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    COMMUNITY_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 커뮤니티 활동을 할 수 없어요."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정이에요."),
    // ACC-07-02 여행·개인 자료 접근
    RESOURCE_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND, "이미 삭제되었거나 볼 수 없는 내용이에요."),
    // 화면을 연 채 역할이 바뀐 경우(B 실시간 전파용)
    EDIT_PERMISSION_CHANGED(HttpStatus.FORBIDDEN, "편집 권한이 변경됐어요. 최신 상태를 확인해 주세요."),
    // ACC-07-03 관리자 검사
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있어요."),
    ADMIN_REAUTH_REQUIRED(HttpStatus.FORBIDDEN, "보안을 위해 본인 확인을 다시 진행해 주세요."),

    // ===== A 파트 (auth·member·participant·notification·admin·savedplace·weather·checklist·cs) =====
    // ACC-01-02·ACC-04-02 인증번호 발송
    VERIFICATION_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증번호를 보내지 못했어요. 다시 시도해 주세요."),
    VERIFICATION_REQUEST_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 요청해 주세요."),
    // ACC-01-03·ACC-04-02 인증번호 확인
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않아요."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 지났어요. 번호를 다시 받아 주세요."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "입력 횟수를 초과했어요. 인증번호를 다시 받아 주세요."),
    // ACC-01-04 이메일 가입 완료 (이메일 소유 확인 뒤에만 안내)
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 이메일로 가입한 계정이에요. 로그인하거나 비밀번호를 재설정해 주세요."),

    // ===== B 파트 (trip·schedule·collab) =====
    // TRP-01-01·03 여행 생성
    TRIP_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "참여 중인 여행을 포함해 여행은 최대 3개까지 가질 수 있어요. 기존 여행을 삭제하거나 나가면 더 만들 수 있어요."),
    TRIP_END_DATE_BEFORE_START(HttpStatus.BAD_REQUEST, "종료일은 시작일과 같거나 이후여야 해요."),
    TRIP_PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, "여행 기간은 최대 7일까지 만들 수 있어요."),
    TRIP_START_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "시작일은 오늘부터 1년 후까지 고를 수 있어요."),
    TRIP_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "여행을 만들지 못했어요. 잠시 후 다시 시도해 주세요."),
    // TRP-01-02 도시 검색·선택
    CITY_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "도시를 검색하지 못했어요. 다시 시도해 주세요."),
    CITY_INFO_FAILED(HttpStatus.BAD_GATEWAY, "도시 정보를 불러오지 못했어요. 다시 시도해 주세요."),

    // ===== C 파트 (place·ai·route) =====

    // ===== D 파트 (community·budget) =====
    // PUB-04-01 공개 상세: 비공개·관리자 숨김·원래 여행 휴지통·없는 게시물
    PUBLIC_POST_NOT_VISIBLE(HttpStatus.NOT_FOUND, "볼 수 없는 게시물이에요."),
    // PUB-05-01 좋아요: 이용 제한 회원
    LIKE_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 좋아요를 누를 수 없어요."),
    // PUB-05-02 좋아요 취소·PUB-06-01 북마크: 이용 제한 회원 (공통 COMMUNITY_RESTRICTED와 문구가 달라 따로 둠)
    COMMUNITY_FEATURE_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 커뮤니티 기능을 쓸 수 없어요."),
    // PUB-06-01 북마크 회원당 200개
    BOOKMARK_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "북마크는 최대 200개까지 담을 수 있어요."),
    // PUB-01-01·02·03 공유하기·미리보기·게시, PUB-02-05 제목 수정
    POST_TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "공개용 제목을 입력해 주세요."),
    POST_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 게시할 수 없어요."),
    POST_OWNER_ONLY(HttpStatus.FORBIDDEN, "여행 소유자만 공개할 수 있어요."),
    POST_ALREADY_PUBLIC(HttpStatus.CONFLICT, "이미 공개한 여행이에요."),
    POST_TRIP_TRASHED(HttpStatus.CONFLICT, "휴지통에 있는 여행은 게시할 수 없어요."),
    // PUB-02-03 관리자가 비공개로 바꾼 게시물은 소유자가 다시 공유할 수 없음
    POST_ADMIN_HIDDEN(HttpStatus.FORBIDDEN, "관리자가 비공개로 바꾼 게시물이라 다시 공유할 수 없어요."),
    // PUB-08-01·03·04 공개 댓글
    COMMENT_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 댓글을 쓸 수 없어요."),
    COMMENT_EDIT_RESTRICTED(HttpStatus.FORBIDDEN, "이용 제한 중에는 댓글을 고칠 수 없어요."),
    COMMENT_TOO_FAST(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 등록해 주세요."),
    COMMENT_EDIT_FORBIDDEN(HttpStatus.FORBIDDEN, "내가 쓴 댓글만 고칠 수 있어요."),
    COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "내가 쓴 댓글만 지울 수 있어요."),

    ;

    private final HttpStatus status;
    private final String message;
}
