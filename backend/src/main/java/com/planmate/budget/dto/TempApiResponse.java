package com.planmate.budget.dto;

/**
 * 임시 공통 응답 모양 {success, data, message}.
 * TODO(공통): common/response/ApiResponse 가 생기면 이 파일을 지우고 그걸 쓴다.
 *             모양이 같아서 contracts/budget.yaml 과 화면은 고치지 않아도 된다.
 */
public record TempApiResponse<T>(boolean success, T data, String message) {

	public static <T> TempApiResponse<T> ok(T data) {
		return new TempApiResponse<>(true, data, null);
	}

}
