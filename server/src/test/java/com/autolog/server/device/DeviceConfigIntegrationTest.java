package com.autolog.server.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class DeviceConfigIntegrationTest extends IntegrationTest {

	@Test
	void fullConfigThenOnlyChangesSince() throws Exception {
		TestOrg org = newOrg();
		long kim = createDriver(org, "영업1팀", "김영업");
		long lee = createDriver(org, "영업2팀", "이외근");
		long vehicleId = createVehicle(org, "12가3456", "아이오닉 5", kim);
		long hq = createPlace(org, "본사", "COMPANY");
		long client = createPlace(org, "한빛상사", "CLIENT");
		PairedDevice device = pair(org, vehicleId);

		JsonNode full = data(getWith(device.token(), "/api/v1/devices/me/config")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.serverTime").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.consentVersion").value("2026-11-01"))
				.andExpect(jsonPath("$.data.vehicle.id").value(vehicleId))
				.andExpect(jsonPath("$.data.vehicle.regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.vehicle.modelName").value("아이오닉 5"))
				.andExpect(jsonPath("$.data.vehicle.defaultDriverId").value(kim))
				.andExpect(jsonPath("$.data.drivers[0].id").value(kim))
				.andExpect(jsonPath("$.data.drivers[0].dept").value("영업1팀"))
				.andExpect(jsonPath("$.data.drivers[0].active").value(true))
				.andExpect(jsonPath("$.data.places[0].id").value(hq))
				.andExpect(jsonPath("$.data.places[0].type").value("COMPANY"))
				.andExpect(jsonPath("$.data.places[0].lat").value(37.497942))
				.andExpect(jsonPath("$.data.places[0].radiusM").value(200))
				.andExpect(jsonPath("$.data.places[0].deleted").value(false)));
		assertThat(full.get("drivers")).hasSize(2);
		assertThat(full.get("places")).hasSize(2);
		String since = full.get("serverTime").asString();

		// 아무것도 안 바뀌면 빈 목록
		clock.advance(Duration.ofMinutes(5));
		JsonNode none = data(configSince(device, since));
		// serverTime 같은 초의 변경도 다시 싣기 위해 이상(>=) 비교 → 같은 초에 만든 항목은 다시 포함될 수 있음
		String afterFirst = none.get("serverTime").asString();
		JsonNode empty = data(configSince(device, afterFirst));
		assertThat(empty.get("drivers")).isEmpty();
		assertThat(empty.get("places")).isEmpty();
		assertThat(empty.get("vehicle").get("id").asLong()).isEqualTo(vehicleId);

		// 직원 비활성·장소 삭제 → 다음 since 요청에 전달
		clock.advance(Duration.ofMinutes(5));
		patchWith(org.token(), "/api/v1/drivers/" + lee, Map.of("active", false)).andExpect(status().isOk());
		deleteWith(org.token(), "/api/v1/places/" + client).andExpect(status().isOk());
		JsonNode changed = data(configSince(device, afterFirst));
		assertThat(changed.get("drivers")).singleElement().satisfies(driver -> {
			assertThat(driver.get("id").asLong()).isEqualTo(lee);
			assertThat(driver.get("active").asBoolean()).isFalse();
		});
		assertThat(changed.get("places")).singleElement().satisfies(place -> {
			assertThat(place.get("id").asLong()).isEqualTo(client);
			assertThat(place.get("deleted").asBoolean()).isTrue();
		});
		// '+'를 인코딩하지 않아 공백으로 온 경우도 받는다
		configSince(device, afterFirst.replace("+", " "))
				.andExpect(status().isOk());
	}

	@Test
	void tripUpdatesCarryAdminEditsOfThisVehicleOnly() throws Exception {
		TestOrg org = newOrg();
		PairedDevice device = pair(org, createVehicle(org, "12가3456", "아이오닉 5"));
		PairedDevice other = pair(org, createVehicle(org, "34나5678", "쏘나타"));
		OffsetDateTime start = OffsetDateTime.parse("2026-11-20T09:00:00+09:00");
		Map<String, Object> unconfirmed = trip(device.vehicleId(), start, 100, 105, "UNCONFIRMED");
		Map<String, Object> deviceEdited = trip(device.vehicleId(), start.plusHours(1), 105, 110, "UNCONFIRMED");
		Map<String, Object> otherVehicle = trip(other.vehicleId(), start, 200, 205, "UNCONFIRMED");
		uploadResults(device, List.of(unconfirmed, deviceEdited));
		uploadResults(other, List.of(otherVehicle));
		String since = data(getWith(device.token(), "/api/v1/devices/me/config")).get("serverTime").asString();
		assertThat(data(configSince(device, since)).get("tripUpdates")).isEmpty();

		clock.advance(Duration.ofMinutes(5));
		// 관리자 확정(D15) — 대상
		postWith(org.token(), "/api/v1/trips/" + unconfirmed.get("clientUuid") + "/edits",
				Map.of("reason", "운전자 확인", "changes", Map.of("purpose", "BUSINESS"))).andExpect(status().isOk());
		// 차량 수정 — 차량이 이미 아는 값이라 대상 아님
		postWith(device.token(), "/api/v1/trips/" + deviceEdited.get("clientUuid") + "/edits",
				Map.of("clientEditUuid", UUID.randomUUID().toString(), "editedAt", "2026-11-20T12:05:00+09:00",
						"purposeSource", "MANUAL", "changes", Map.of("purpose", "PERSONAL"))).andExpect(status().isOk());
		// 다른 차량 트립의 관리자 수정 — 대상 아님
		postWith(org.token(), "/api/v1/trips/" + otherVehicle.get("clientUuid") + "/edits",
				Map.of("reason", "운전자 확인", "changes", Map.of("purpose", "COMMUTE"))).andExpect(status().isOk());

		JsonNode changed = data(configSince(device, since));
		assertThat(changed.get("tripUpdates")).singleElement().satisfies(update -> {
			assertThat(update.get("clientUuid").asString()).isEqualTo(unconfirmed.get("clientUuid"));
			assertThat(update.get("purpose").asString()).isEqualTo("BUSINESS");
			assertThat(update.get("purposeSource").asString()).isEqualTo("ADMIN");
			assertThat(update.get("businessCategory").asString()).isEqualTo("OTHER");
		});
		// since 없음 → 최근 90일 관리자 수정
		assertThat(data(getWith(device.token(), "/api/v1/devices/me/config")).get("tripUpdates")).hasSize(1);
		clock.advance(Duration.ofDays(91));
		assertThat(data(getWith(device.token(), "/api/v1/devices/me/config")).get("tripUpdates")).isEmpty();
	}

	@Test
	void invalidSinceIsValidationFailed() throws Exception {
		TestOrg org = newOrg();
		PairedDevice device = pair(org, createVehicle(org, "12가3456", "아이오닉 5"));
		configSince(device, "yesterday")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("since: ISO-8601 형식이어야 합니다."));
	}

	@Test
	void consentIsCreatedOnceThenReturnedAsIs() throws Exception {
		TestOrg org = newOrg();
		long kim = createDriver(org, "영업1팀", "김영업");
		PairedDevice device = pair(org, createVehicle(org, "12가3456", "아이오닉 5"));
		Map<String, Object> body = Map.of("driverId", kim, "consentVersion", "2026-11-01",
				"consentedAt", "2026-11-20T11:58:00+09:00");

		postWith(device.token(), "/api/v1/devices/me/consents", body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.driverId").value(kim))
				.andExpect(jsonPath("$.data.consentVersion").value("2026-11-01"))
				.andExpect(jsonPath("$.data.consentedAt").value("2026-11-20T11:58:00+09:00"));
		// 같은 직원·버전 재전송 → 200, 기존 기록 그대로
		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", kim,
				"consentVersion", "2026-11-01", "consentedAt", "2026-11-20T11:59:30+09:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.consentedAt").value("2026-11-20T11:58:00+09:00"));
		// 새 버전은 새 기록
		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", kim,
				"consentVersion", "2027-01-01", "consentedAt", "2026-11-20T11:59:00+09:00"))
				.andExpect(status().isCreated());
	}

	@Test
	void consentValidation() throws Exception {
		TestOrg org = newOrg();
		long kim = createDriver(org, "영업1팀", "김영업");
		PairedDevice device = pair(org, createVehicle(org, "12가3456", "아이오닉 5"));

		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", kim,
				"consentVersion", "2026-11-01", "consentedAt", "2026-11-20T12:10:01+09:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("consentedAt: 현재 시각 이후일 수 없습니다."));
		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", kim,
				"consentVersion", "2026-11-01", "consentedAt", "2026-11-20T12:10:00+09:00"))
				.andExpect(status().isCreated());
		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", 999_999,
				"consentVersion", "2026-11-01", "consentedAt", "2026-11-20T11:00:00+09:00"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"));
		patchWith(org.token(), "/api/v1/drivers/" + kim, Map.of("active", false)).andExpect(status().isOk());
		postWith(device.token(), "/api/v1/devices/me/consents", Map.of("driverId", kim,
				"consentVersion", "2027-01-01", "consentedAt", "2026-11-20T11:00:00+09:00"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"));
	}

	private ResultActions configSince(PairedDevice device, String since) throws Exception {
		return perform(get("/api/v1/devices/me/config").param("since", since), device.token(), null);
	}
}
