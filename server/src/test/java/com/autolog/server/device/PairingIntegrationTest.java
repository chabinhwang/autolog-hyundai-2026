package com.autolog.server.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.global.security.TokenHasher;
import com.autolog.server.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class PairingIntegrationTest extends IntegrationTest {

	@Autowired
	private DeviceRepository deviceRepository;

	@Test
	void fullPairingFlowAndRePairingRevokesPreviousDevice() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");

		// 1. 차량: 코드 발급
		String firstToken = newDeviceToken();
		JsonNode issued = data(mockMvc.perform(post("/api/v1/devices/pairing-codes")
						.with(remoteAddr(randomIp()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("installationId", "8b1f2c64-5a1e-4b7c-9d3a-0f6e2c1d7a90",
								"tokenHash", TokenHasher.sha256Hex(firstToken)))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.expiresAt").value("2026-11-20T12:10:00+09:00")));
		String pairingId = issued.get("pairingId").asString();
		String code = issued.get("code").asString();
		assertThat(code).matches("\\d{6}");

		// 2. 차량: 상태 조회 (PENDING, vehicle null)
		mockMvc.perform(get("/api/v1/devices/pairing-codes/" + pairingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.vehicle").isEmpty());

		// 3. 관리자: 페어링
		JsonNode paired = data(postWith(org.token(), "/api/v1/devices/pair", Map.of("code", code, "vehicleId", vehicleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.vehicleId").value(vehicleId))
				.andExpect(jsonPath("$.data.pairedAt").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.replacedDeviceId").isEmpty()));
		long firstDeviceId = paired.get("deviceId").asLong();

		// 4. 차량: PAIRED + 차량 정보, 이후 기기 토큰 사용
		mockMvc.perform(get("/api/v1/devices/pairing-codes/" + pairingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAIRED"))
				.andExpect(jsonPath("$.data.vehicle.regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.vehicle.modelName").value("아이오닉 5"));
		getWith(firstToken, "/api/v1/devices/me/config").andExpect(status().isOk());
		// 서버에는 해시만 저장된다
		assertThat(deviceRepository.findByTokenHash(TokenHasher.sha256Hex(firstToken))).isPresent();

		// 같은 코드는 1회용
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", code, "vehicleId", vehicleId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_INVALID"));

		// 재페어링: 기존 기기 폐기
		PairedDevice second = pair(org, vehicleId);
		assertThat(second.deviceId()).isNotEqualTo(firstDeviceId);
		getWith(firstToken, "/api/v1/devices/me/config")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("DEVICE_TOKEN_INVALID"));
		getWith(second.token(), "/api/v1/devices/me/config").andExpect(status().isOk());
		assertThat(deviceRepository.findById(firstDeviceId).orElseThrow().getRevokedAt()).isNotNull();

		String thirdToken = newDeviceToken();
		IssuedCode third = issuePairingCode(thirdToken);
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", third.code(), "vehicleId", vehicleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.replacedDeviceId").value(second.deviceId()));
	}

	@Test
	void pairingSameInstallationToAnotherVehicleRevokesPreviousVehicleDevice() throws Exception {
		TestOrg org = newOrg();
		long firstVehicle = createVehicle(org, "12가3456", "아이오닉 5");
		long secondVehicle = createVehicle(org, "34나5678", "쏘나타");
		String installationId = UUID.randomUUID().toString();

		String firstToken = newDeviceToken();
		long firstDeviceId = pairWithInstallation(org, firstVehicle, installationId, firstToken);
		String secondToken = newDeviceToken();
		pairWithInstallation(org, secondVehicle, installationId, secondToken);

		// 이전 차량의 기기는 폐기되고, 차량 목록에서도 연결 안 됨으로 보인다
		assertThat(deviceRepository.findById(firstDeviceId).orElseThrow().getRevokedAt()).isNotNull();
		getWith(firstToken, "/api/v1/devices/me/config").andExpect(status().isUnauthorized());
		getWith(secondToken, "/api/v1/devices/me/config").andExpect(status().isOk());
		getWith(org.token(), "/api/v1/vehicles")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == " + firstVehicle + ")].device.paired").value(false))
				.andExpect(jsonPath("$.data[?(@.id == " + secondVehicle + ")].device.paired").value(true));
	}

	private long pairWithInstallation(TestOrg org, long vehicleId, String installationId, String token)
			throws Exception {
		JsonNode issued = data(mockMvc.perform(post("/api/v1/devices/pairing-codes")
						.with(remoteAddr(randomIp()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("installationId", installationId,
								"tokenHash", TokenHasher.sha256Hex(token)))))
				.andExpect(status().isCreated()));
		JsonNode paired = data(postWith(org.token(), "/api/v1/devices/pair",
						Map.of("code", issued.get("code").asString(), "vehicleId", vehicleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.replacedDeviceId").isEmpty()));
		return paired.get("deviceId").asLong();
	}

	@Test
	void invalidAndExpiredCodes() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");
		IssuedCode code = issuePairingCode(newDeviceToken());

		String wrong = code.code().equals("000000") ? "000001" : "000000";
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", wrong, "vehicleId", vehicleId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_INVALID"))
				.andExpect(jsonPath("$.error.message").value("페어링 코드가 올바르지 않습니다."));
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", "12a456", "vehicleId", vehicleId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		clock.advance(Duration.ofMinutes(10));
		mockMvc.perform(get("/api/v1/devices/pairing-codes/" + code.pairingId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("EXPIRED"));
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", code.code(), "vehicleId", vehicleId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_EXPIRED"))
				.andExpect(jsonPath("$.error.message").value("페어링 코드가 만료되었습니다. 차량에서 새 코드를 받아 주세요."));
	}

	@Test
	void pairingToInactiveOrUnknownVehicleIsVehicleNotFound() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");
		patchWith(org.token(), "/api/v1/vehicles/" + vehicleId, Map.of("active", false)).andExpect(status().isOk());
		IssuedCode code = issuePairingCode(newDeviceToken());
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", code.code(), "vehicleId", vehicleId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_NOT_FOUND"));
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", code.code(), "vehicleId", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_NOT_FOUND"));
	}

	@Test
	void unpairRevokesActiveDevice() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");
		PairedDevice device = pair(org, vehicleId);
		clock.advance(Duration.ofHours(1));

		deleteWith(org.token(), "/api/v1/vehicles/" + vehicleId + "/device")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.vehicleId").value(vehicleId))
				.andExpect(jsonPath("$.data.deviceId").value(device.deviceId()))
				.andExpect(jsonPath("$.data.revokedAt").value("2026-11-20T13:00:00+09:00"));
		getWith(device.token(), "/api/v1/devices/me/config")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("DEVICE_TOKEN_INVALID"));
		deleteWith(org.token(), "/api/v1/vehicles/" + vehicleId + "/device")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DEVICE_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("연결된 기기가 없습니다."));
		deleteWith(org.token(), "/api/v1/vehicles/999999/device")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_NOT_FOUND"));
	}

	@Test
	void pairingStatusNotFoundAndValidation() throws Exception {
		mockMvc.perform(get("/api/v1/devices/pairing-codes/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PAIRING_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("페어링 요청을 찾을 수 없습니다."));
		mockMvc.perform(get("/api/v1/devices/pairing-codes/not-a-uuid"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PAIRING_NOT_FOUND"));
		mockMvc.perform(post("/api/v1/devices/pairing-codes")
						.with(remoteAddr(randomIp()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("installationId", "abc", "tokenHash", "XYZ"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("tokenHash: 64자리 16진수여야 합니다."));
	}

	@Test
	void pairingCodeIssueIsRateLimitedPerIp() throws Exception {
		String ip = randomIp();
		for (int i = 0; i < 10; i++) {
			issueFrom(ip).andExpect(status().isCreated());
		}
		issueFrom(ip)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
		issueFrom(randomIp()).andExpect(status().isCreated());
	}

	@Test
	void pairIsRateLimitedPerAdmin() throws Exception {
		TestOrg org = newOrg();
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5");
		for (int i = 0; i < 10; i++) {
			postWith(org.token(), "/api/v1/devices/pair", Map.of("code", "999999", "vehicleId", vehicleId))
					.andExpect(status().is4xxClientError());
		}
		postWith(org.token(), "/api/v1/devices/pair", Map.of("code", "999999", "vehicleId", vehicleId))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
		// 다른 관리자는 영향 없음
		TestOrg other = newOrg();
		long otherVehicle = createVehicle(other, "34나5678", "쏘나타");
		pair(other, otherVehicle);
	}

	private org.springframework.test.web.servlet.ResultActions issueFrom(String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/devices/pairing-codes")
				.with(remoteAddr(ip))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("installationId", UUID.randomUUID().toString(),
						"tokenHash", TokenHasher.sha256Hex(newDeviceToken())))));
	}
}
