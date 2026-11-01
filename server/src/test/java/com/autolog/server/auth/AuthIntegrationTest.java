package com.autolog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTest {

	@Test
	void loginReturnsJwtAndAdminAndOrganization() throws Exception {
		TestOrg org = newOrg();
		JsonNode data = data(login(org.loginId(), "password-1234", randomIp())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").isEmpty())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(43200))
				.andExpect(jsonPath("$.data.admin.id").value(org.adminId()))
				.andExpect(jsonPath("$.data.admin.loginId").value(org.loginId()))
				.andExpect(jsonPath("$.data.organization.id").value(org.orgId()))
				.andExpect(jsonPath("$.data.organization.name").value(org.name())));
		String token = data.get("accessToken").asString();
		assertThat(token.split("\\.")).hasSize(3);

		// 발급한 토큰으로 /auth/me
		getWith(token, "/api/v1/auth/me")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.admin.id").value(org.adminId()))
				.andExpect(jsonPath("$.data.organization.bizRegNo").value(org.bizRegNo()))
				.andExpect(jsonPath("$.data.expiresAt").value("2026-11-21T00:00:00+09:00"));
	}

	@Test
	void wrongPasswordAndUnknownIdAreIndistinguishable() throws Exception {
		TestOrg org = newOrg();
		login(org.loginId(), "wrong-password", randomIp())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.error.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
		login("nobody@autolog-test.kr", "password-1234", randomIp())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void loginValidation() throws Exception {
		login("not-an-email", "x", randomIp())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("loginId: 이메일 형식이어야 합니다."));
	}

	@Test
	void loginIsRateLimitedPerIp() throws Exception {
		TestOrg org = newOrg();
		String ip = randomIp();
		for (int i = 0; i < 10; i++) {
			login(org.loginId(), "wrong-password", ip).andExpect(status().isUnauthorized());
		}
		login(org.loginId(), "password-1234", ip)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.error.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
		// 다른 IP는 영향 없음
		login(org.loginId(), "password-1234", randomIp()).andExpect(status().isOk());
		// 1분이 지나면 다시 허용
		clock.advance(Duration.ofSeconds(61));
		login(org.loginId(), "password-1234", ip).andExpect(status().isOk());
	}

	@Test
	void meRequiresValidJwt() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").isEmpty())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
		getWith("aaa.bbb.ccc", "/api/v1/auth/me")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void expiredJwtIsRejected() throws Exception {
		TestOrg org = newOrg();
		getWith(org.token(), "/api/v1/auth/me").andExpect(status().isOk());
		clock.advance(Duration.ofHours(12).plusSeconds(1));
		getWith(org.token(), "/api/v1/auth/me")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void jwtSignedWithAnotherKeyIsRejected() throws Exception {
		TestOrg org = newOrg();
		String[] parts = org.token().split("\\.");
		String tampered = parts[0] + "." + parts[1] + "." + "AAAA" + parts[2].substring(4);
		getWith(tampered, "/api/v1/auth/me")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void principalMismatchIsForbidden() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");
		PairedDevice device = pair(org, vehicleId);

		// 기기 토큰으로 관리자 API → 403
		getWith(device.token(), "/api/v1/auth/me")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		getWith(device.token(), "/api/v1/vehicles")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		// 관리자 JWT로 차량 API → 403
		getWith(org.token(), "/api/v1/devices/me/config")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		perform(put("/api/v1/trips/batch"), org.token(), Map.of("trips", List.of()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void unknownDeviceTokenIsDeviceTokenInvalid() throws Exception {
		getWith(newDeviceToken(), "/api/v1/devices/me/config")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("DEVICE_TOKEN_INVALID"))
				.andExpect(jsonPath("$.error.message").value("차량 등록이 해제되었습니다. 다시 페어링해 주세요."));
	}

	@Test
	void corsAllowsConfiguredOriginAndExposesContentDisposition() throws Exception {
		mockMvc.perform(options("/api/v1/trips")
						.header(HttpHeaders.ORIGIN, "https://dashboard.example.test")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://dashboard.example.test"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
		TestOrg org = newOrg();
		mockMvc.perform(get("/api/v1/auth/me")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + org.token()))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition"));
		mockMvc.perform(options("/api/v1/trips")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

	private ResultActions login(String loginId, String password, String ip)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.with(remoteAddr(ip))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("loginId", loginId, "password", password))));
	}
}
