package com.autolog.server.global.security;

import com.autolog.server.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 인증은 됐지만 호출 주체가 맞지 않음 (예: 기기 토큰으로 관리자 API) → 403 FORBIDDEN */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	private final SecurityErrorWriter errorWriter;

	public JsonAccessDeniedHandler(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		errorWriter.write(response, ErrorCode.FORBIDDEN);
	}
}
