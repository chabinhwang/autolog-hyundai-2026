package com.autolog.server.global.security;

import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 필터 단계(401·403·429)의 오류도 { success, data, error } 구조로 쓴다. */
@Component
public class SecurityErrorWriter {

	private final JsonMapper jsonMapper;

	public SecurityErrorWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.status().value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(jsonMapper.writeValueAsString(
				ApiResponse.fail(errorCode.name(), errorCode.defaultMessage())));
	}
}
