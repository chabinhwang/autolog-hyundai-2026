package com.autolog.server.global.security;

import com.autolog.server.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 인증 필요 경로에 인증 없이 들어온 요청 → 401 (UNAUTHORIZED 또는 DEVICE_TOKEN_INVALID) */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorWriter errorWriter;

	public JsonAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		Object error = request.getAttribute(BearerTokenAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
		errorWriter.write(response, error instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED);
	}
}
