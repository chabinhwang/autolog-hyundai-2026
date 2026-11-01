package com.autolog.server.global.error;

/**
 * 계약에 정의된 오류 응답으로 바뀌는 예외. 메시지는 사용자에게 그대로 노출되므로
 * 장소 라벨·메모·토큰 같은 값을 넣지 않는다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.defaultMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	/** "field: message" 형식의 VALIDATION_FAILED */
	public static BusinessException validation(String field, String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, field + ": " + message);
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		// 업무 예외는 흐름 제어용이라 스택 트레이스가 필요 없다.
		return this;
	}
}
