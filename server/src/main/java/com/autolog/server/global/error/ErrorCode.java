package com.autolog.server.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 (docs/contracts/README.md#오류-코드). 메시지는 계약 예시 문구를 기본값으로 쓴다.
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),
	AUTO_FIELD_IMMUTABLE(HttpStatus.BAD_REQUEST, "차량이 기록한 값은 수정할 수 없습니다."),
	EDIT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "관리자 수정에는 사유가 필요합니다."),
	PAIRING_CODE_INVALID(HttpStatus.BAD_REQUEST, "페어링 코드가 올바르지 않습니다."),
	PAIRING_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "페어링 코드가 만료되었습니다. 차량에서 새 코드를 받아 주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	DEVICE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "차량 등록이 해제되었습니다. 다시 페어링해 주세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "이 요청을 수행할 권한이 없습니다."),
	PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN(HttpStatus.FORBIDDEN, "개인 운행은 운전자만 수정할 수 있습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "운행 기록을 찾을 수 없습니다."),
	VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "차량을 찾을 수 없습니다."),
	DRIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."),
	PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
	PAIRING_NOT_FOUND(HttpStatus.NOT_FOUND, "페어링 요청을 찾을 수 없습니다."),
	DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결된 기기가 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	VEHICLE_REG_NO_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 자동차등록번호입니다."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
