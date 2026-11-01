package com.autolog.server.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import com.autolog.server.support.TestClockConfiguration;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class MasterDataIntegrationTest extends IntegrationTest {

	@Test
	void vehicleCrudWithDefaultDriverAndDeviceStatus() throws Exception {
		TestOrg org = newOrg();
		long driverId = createDriver(org, "영업1팀", "김영업");
		postWith(org.token(), "/api/v1/vehicles", Map.of("regNo", "12가3456", "modelName", "아이오닉 5",
				"defaultDriverId", driverId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.modelName").value("아이오닉 5"))
				.andExpect(jsonPath("$.data.defaultDriver.id").value(driverId))
				.andExpect(jsonPath("$.data.defaultDriver.dept").value("영업1팀"))
				.andExpect(jsonPath("$.data.defaultDriver.name").value("김영업"))
				.andExpect(jsonPath("$.data.active").value(true))
				.andExpect(jsonPath("$.data.device.paired").value(false))
				.andExpect(jsonPath("$.data.device.deviceId").isEmpty())
				.andExpect(jsonPath("$.data.device.pairedAt").isEmpty())
				.andExpect(jsonPath("$.data.device.lastSyncedAt").isEmpty());
		long other = createVehicle(org, "34나5678", "쏘나타");

		JsonNode list = data(getWith(org.token(), "/api/v1/vehicles").andExpect(status().isOk()));
		assertThat(list).hasSize(2);
		assertThat(list.get(1).get("id").asLong()).isEqualTo(other);
		assertThat(list.get(1).get("defaultDriver").isNull()).isTrue();

		// 같은 회사 같은 번호 → 409
		postWith(org.token(), "/api/v1/vehicles", Map.of("regNo", "12가3456", "modelName", "다른 차"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_REG_NO_DUPLICATE"))
				.andExpect(jsonPath("$.error.message").value("이미 등록된 자동차등록번호입니다."));
		patchWith(org.token(), "/api/v1/vehicles/" + other, Map.of("regNo", "12가3456"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_REG_NO_DUPLICATE"));
		// 다른 회사는 같은 번호 가능
		createVehicle(newOrg(), "12가3456", "아이오닉 5");

		// 검증
		postWith(org.token(), "/api/v1/vehicles", Map.of("regNo", "", "modelName", "x"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("regNo: 비어 있을 수 없습니다."));
		postWith(org.token(), "/api/v1/vehicles", Map.of("regNo", "99다9999", "modelName", "x",
				"defaultDriverId", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"));
	}

	@Test
	void vehiclePatchOnlyChangesSentFieldsAndDeactivationRevokesDevice() throws Exception {
		TestOrg org = newOrg();
		long driverId = createDriver(org, "영업1팀", "김영업");
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);
		PairedDevice device = pair(org, vehicleId);
		uploadResults(device, List.of(trip(vehicleId, TestClockConfiguration.BASE_TIME.minusHours(3), 100.0, 110.0,
				"BUSINESS")));

		patchWith(org.token(), "/api/v1/vehicles/" + vehicleId, Map.of("modelName", "아이오닉 6"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.modelName").value("아이오닉 6"))
				.andExpect(jsonPath("$.data.regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.defaultDriver.id").value(driverId))
				.andExpect(jsonPath("$.data.device.paired").value(true))
				.andExpect(jsonPath("$.data.device.deviceId").value(device.deviceId()))
				.andExpect(jsonPath("$.data.device.pairedAt").value("2026-11-20T12:00:00+09:00"));

		Map<String, Object> deactivate = new HashMap<>();
		deactivate.put("defaultDriverId", null);
		deactivate.put("active", false);
		patchWith(org.token(), "/api/v1/vehicles/" + vehicleId, deactivate)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.defaultDriver").isEmpty())
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.device.paired").value(false))
				.andExpect(jsonPath("$.data.device.deviceId").isEmpty())
				.andExpect(jsonPath("$.data.device.lastSyncedAt").value("2026-11-20T12:00:00+09:00"));
		// 비활성 차량의 기기 토큰은 폐기됨
		getWith(device.token(), "/api/v1/devices/me/config")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("DEVICE_TOKEN_INVALID"));
		// 기본 목록에서 빠지고 includeInactive=true면 포함
		assertThat(data(getWith(org.token(), "/api/v1/vehicles"))).isEmpty();
		assertThat(data(getWith(org.token(), "/api/v1/vehicles?includeInactive=true"))).hasSize(1);
	}

	@Test
	void driverCrudAndDeactivationClearsDefaultDriver() throws Exception {
		TestOrg org = newOrg();
		postWith(org.token(), "/api/v1/drivers", Map.of("dept", "영업1팀", "name", "김영업"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.dept").value("영업1팀"))
				.andExpect(jsonPath("$.data.name").value("김영업"))
				.andExpect(jsonPath("$.data.active").value(true));
		long driverId = createDriver(org, "영업2팀", "이외근");
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);

		postWith(org.token(), "/api/v1/drivers", Map.of("dept", "영업1팀", "name", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("name: 비어 있을 수 없습니다."));

		patchWith(org.token(), "/api/v1/drivers/" + driverId, Map.of("active", false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.name").value("이외근"));
		JsonNode vehicles = data(getWith(org.token(), "/api/v1/vehicles"));
		assertThat(vehicles.get(0).get("id").asLong()).isEqualTo(vehicleId);
		assertThat(vehicles.get(0).get("defaultDriver").isNull()).isTrue();

		assertThat(data(getWith(org.token(), "/api/v1/drivers"))).hasSize(1);
		assertThat(data(getWith(org.token(), "/api/v1/drivers?includeInactive=true"))).hasSize(2);
		// 비활성 직원은 기본 운전자로 지정 불가
		patchWith(org.token(), "/api/v1/vehicles/" + vehicleId, Map.of("defaultDriverId", driverId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"));
		patchWith(org.token(), "/api/v1/drivers/999999", Map.of("active", false))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("직원을 찾을 수 없습니다."));
	}

	@Test
	void placeCrudSoftDeleteAndValidation() throws Exception {
		TestOrg org = newOrg();
		postWith(org.token(), "/api/v1/places", Map.of("name", "한빛상사", "type", "CLIENT", "lat", 37.566535,
				"lng", 126.977969, "radiusM", 150))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("한빛상사"))
				.andExpect(jsonPath("$.data.type").value("CLIENT"))
				.andExpect(jsonPath("$.data.lat").value(37.566535))
				.andExpect(jsonPath("$.data.lng").value(126.977969))
				.andExpect(jsonPath("$.data.radiusM").value(150))
				.andExpect(jsonPath("$.data.updatedAt").value("2026-11-20T12:00:00+09:00"));
		long companyId = id(postWith(org.token(), "/api/v1/places", Map.of("name", "본사", "type", "COMPANY",
				"lat", 37.497942, "lng", 127.027621)).andExpect(jsonPath("$.data.radiusM").value(200)));

		postWith(org.token(), "/api/v1/places", Map.of("name", "x", "type", "CLIENT", "lat", 37.5, "lng", 127.0,
				"radiusM", 10))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("radiusM: 50 이상이어야 합니다."));
		postWith(org.token(), "/api/v1/places", Map.of("name", "x", "type", "HOME", "lat", 37.5, "lng", 127.0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("type: COMPANY 또는 CLIENT여야 합니다."));
		postWith(org.token(), "/api/v1/places", Map.of("name", "x", "type", "CLIENT", "lat", 91, "lng", 127.0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("lat: 90 이하여야 합니다."));

		assertThat(data(getWith(org.token(), "/api/v1/places"))).hasSize(2);
		assertThat(data(getWith(org.token(), "/api/v1/places?type=COMPANY"))).hasSize(1);

		clock.advance(Duration.ofHours(4));
		patchWith(org.token(), "/api/v1/places/" + companyId, Map.of("radiusM", 300))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.radiusM").value(300))
				.andExpect(jsonPath("$.data.name").value("본사"))
				.andExpect(jsonPath("$.data.updatedAt").value("2026-11-20T16:00:00+09:00"));

		deleteWith(org.token(), "/api/v1/places/" + companyId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(companyId))
				.andExpect(jsonPath("$.data.deleted").value(true));
		assertThat(data(getWith(org.token(), "/api/v1/places"))).hasSize(1);
		deleteWith(org.token(), "/api/v1/places/" + companyId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
		patchWith(org.token(), "/api/v1/places/" + companyId, Map.of("radiusM", 300))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
	}

	@Test
	void malformedJsonIsInvalidRequestBody() throws Exception {
		TestOrg org = newOrg();
		mockMvc.perform(post("/api/v1/drivers")
						.header("Authorization", "Bearer " + org.token())
						.contentType("application/json")
						.content("{not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_BODY"));
	}
}
