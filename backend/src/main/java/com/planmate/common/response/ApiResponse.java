package com.planmate.common.response;

import com.planmate.common.exception.ErrorCode;

import java.util.List;

/**
 * 모든 API의 공통 응답 모양 {success, data, message} (AGENTS §6).
 * 실패 응답은 data에 {code, fieldErrors}를 담고, message에는 ErrorCode의 안내 문구를 담는다.
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    /** 성공, 돌려줄 값 없음 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 성공, 돌려줄 값 있음 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 성공, 돌려줄 값 + 안내 문구 */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    /** 실패, 칸별 오류 없음 (fieldErrors는 빈 배열) */
    public static ApiResponse<ErrorData> fail(ErrorCode errorCode) {
        return fail(errorCode, List.of());
    }

    /** 실패, 칸별 오류 포함 */
    public static ApiResponse<ErrorData> fail(ErrorCode errorCode, List<FieldErrorData> fieldErrors) {
        List<FieldErrorData> errors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        return new ApiResponse<>(false, new ErrorData(errorCode.name(), errors), errorCode.getMessage());
    }

    /** 실패 응답의 data. code는 ErrorCode 이름, fieldErrors는 항상 배열(없으면 []) */
    public record ErrorData(String code, List<FieldErrorData> fieldErrors) {
    }

    /** 입력 칸 하나의 오류 (칸 이름 + 기능정의서 안내 문구) */
    public record FieldErrorData(String field, String message) {
    }
}
