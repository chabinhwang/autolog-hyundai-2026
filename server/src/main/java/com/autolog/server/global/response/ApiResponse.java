package com.autolog.server.global.response;

/**
 * 모든 API 응답의 공통 구조. 성공·실패 모두 같은 형태로 반환한다.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> fail(String code, String message) {
		return new ApiResponse<>(false, null, new ApiError(code, message));
	}

	public record ApiError(String code, String message) {
	}
}
