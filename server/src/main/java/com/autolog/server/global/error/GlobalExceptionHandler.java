package com.autolog.server.global.error;

import com.autolog.server.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return respond(e.errorCode().status(), e.errorCode().name(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.findFirst()
				.orElse("요청 값이 올바르지 않습니다.");
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
		String message = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> result.getMethodParameter().getParameterName() + ": "
								+ error.getDefaultMessage()))
				.findFirst()
				.orElse("요청 값이 올바르지 않습니다.");
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
		String message = e.getConstraintViolations().stream()
				.map(violation -> lastNode(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
				.findFirst()
				.orElse("요청 값이 올바르지 않습니다.");
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getParameterName() + ": 필수 값입니다.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getName() + ": 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(Exception e) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "요청 본문을 읽을 수 없습니다.");
	}

	@ExceptionHandler({NoResourceFoundException.class, MissingPathVariableException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.");
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
		// PostgreSQL 오류 메시지에는 실패한 행의 값(라벨·메모 등)이 들어갈 수 있으므로 종류와 SQLState만 남긴다.
		String sqlState = NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sqlException
				? sqlException.getSQLState() : null;
		log.error("Database error: {} (sqlState={})", e.getClass().getSimpleName(), sqlState);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		// 상세는 서버 로그에만 남긴다. 요청 본문(장소 라벨·메모 등)은 로그에 넣지 않는다.
		log.error("Unhandled exception", e);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
	}

	private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiResponse.fail(code, message));
	}

	private static String lastNode(String path) {
		int dot = path.lastIndexOf('.');
		return dot < 0 ? path : path.substring(dot + 1);
	}
}
