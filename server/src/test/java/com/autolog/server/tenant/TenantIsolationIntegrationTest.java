package com.autolog.server.tenant;

import static com.autolog.server.support.TestClockConfiguration.BASE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** 회사 범위: 다른 회사 리소스는 403이 아니라 리소스별 404 (존재 여부 비노출) */
class TenantIsolationIntegrationTest extends IntegrationTest {

	private TestOrg a;
	private TestOrg b;
	private long vehicleA;
	private long driverA;
	private long placeA;
	private String tripA;
	private PairedDevice deviceA;
	private PairedDevice deviceB;
	private long vehicleB;

	@BeforeEach
	void setUp() throws Exception {
		a = newOrg();
		b = newOrg();
		driverA = createDriver(a, "영업1팀", "김영업");
		vehicleA = createVehicle(a, "12가3456", "아이오닉 5", driverA);
		placeA = createPlace(a, "A사 본사", "COMPANY");
		deviceA = pair(a, vehicleA);
		Map<String, Object> trip = trip(vehicleA, BASE_TIME.minusHours(3), 100, 110, "BUSINESS");
		trip.put("note", "A사 메모");
		uploadResults(deviceA, List.of(trip));
		tripA = (String) trip.get("clientUuid");

		vehicleB = createVehicle(b, "34나5678", "쏘나타");
		deviceB = pair(b, vehicleB);
	}

	@Test
	void masterDataOfAnotherCompanyIsNotFound() throws Exception {
		expectNotFound(patchWith(b.token(), "/api/v1/vehicles/" + vehicleA, Map.of("modelName", "x")),
				"VEHICLE_NOT_FOUND");
		expectNotFound(patchWith(b.token(), "/api/v1/drivers/" + driverA, Map.of("name", "x")), "DRIVER_NOT_FOUND");
		expectNotFound(patchWith(b.token(), "/api/v1/places/" + placeA, Map.of("radiusM", 300)), "PLACE_NOT_FOUND");
		expectNotFound(deleteWith(b.token(), "/api/v1/places/" + placeA), "PLACE_NOT_FOUND");
		// 다른 회사 직원은 기본 운전자로 지정 불가
		expectNotFound(postWith(b.token(), "/api/v1/vehicles", Map.of("regNo", "56다7890", "modelName", "x",
				"defaultDriverId", driverA)), "DRIVER_NOT_FOUND");
		// 목록에는 자기 회사 것만
		assertThat(data(getWith(b.token(), "/api/v1/vehicles"))).singleElement()
				.satisfies(v -> assertThat(v.get("id").asLong()).isEqualTo(vehicleB));
		assertThat(data(getWith(b.token(), "/api/v1/drivers"))).isEmpty();
		assertThat(data(getWith(b.token(), "/api/v1/places"))).isEmpty();
		// 변경되지 않았음
		assertThat(data(getWith(a.token(), "/api/v1/places")).get(0).get("radiusM").asInt()).isEqualTo(200);
	}

	@Test
	void tripsOfAnotherCompanyAreNotFound() throws Exception {
		expectNotFound(getWith(b.token(), "/api/v1/trips/" + tripA), "TRIP_NOT_FOUND");
		expectNotFound(postWith(b.token(), "/api/v1/trips/" + tripA + "/edits",
				Map.of("reason", "x", "changes", Map.of("note", "탈취"))), "TRIP_NOT_FOUND");
		expectNotFound(postWith(deviceB.token(), "/api/v1/trips/" + tripA + "/edits", Map.of(
				"clientEditUuid", UUID.randomUUID().toString(), "editedAt", BASE_TIME.toString(),
				"changes", Map.of("note", "탈취"))), "TRIP_NOT_FOUND");
		JsonNode list = data(getWith(b.token(), "/api/v1/trips?from=2026-11-01&to=2026-11-30&vehicleId=" + vehicleA));
		assertThat(list.get("totalElements").asInt()).isZero();
		assertThat(data(getWith(a.token(), "/api/v1/trips/" + tripA)).get("note").asString()).isEqualTo("A사 메모");
	}

	@Test
	void pairingAndUnpairingAnotherCompanyVehicleIsNotFound() throws Exception {
		IssuedCode code = issuePairingCode(newDeviceToken());
		expectNotFound(postWith(b.token(), "/api/v1/devices/pair", Map.of("code", code.code(), "vehicleId", vehicleA)),
				"VEHICLE_NOT_FOUND");
		expectNotFound(deleteWith(b.token(), "/api/v1/vehicles/" + vehicleA + "/device"), "VEHICLE_NOT_FOUND");
		// A 기기는 그대로 유효
		getWith(deviceA.token(), "/api/v1/devices/me/config").andExpect(status().isOk());
	}

	@Test
	void reportsOfAnotherCompanyVehicleAreNotFoundOrEmpty() throws Exception {
		expectNotFound(getWith(b.token(),
				"/api/v1/reports/logbook?vehicleId=" + vehicleA + "&from=2026-11-01&to=2026-11-30"), "VEHICLE_NOT_FOUND");
		JsonNode summary = data(getWith(b.token(), "/api/v1/dashboard/summary?month=2026-11"));
		assertThat(summary.get("vehicles")).singleElement()
				.satisfies(v -> assertThat(v.get("vehicleId").asLong()).isEqualTo(vehicleB));
		assertThat(summary.get("totals").get("totalKm").decimalValue()).isEqualByComparingTo("0");
	}

	@Test
	void devicesSeeOnlyTheirOwnCompany() throws Exception {
		JsonNode config = data(getWith(deviceB.token(), "/api/v1/devices/me/config"));
		assertThat(config.get("vehicle").get("id").asLong()).isEqualTo(vehicleB);
		assertThat(config.get("drivers")).isEmpty();
		assertThat(config.get("places")).isEmpty();
		// 다른 회사 직원 동의 기록 불가
		expectNotFound(postWith(deviceB.token(), "/api/v1/devices/me/consents", Map.of("driverId", driverA,
				"consentVersion", "2026-11-01", "consentedAt", BASE_TIME.toString())), "DRIVER_NOT_FOUND");
		// 다른 회사 직원·장소로 업로드 불가
		Map<String, Object> trip = trip(vehicleB, BASE_TIME.minusHours(1), 10, 20, "COMMUTE");
		trip.put("driverId", driverA);
		Map<String, Object> trip2 = trip(vehicleB, BASE_TIME.minusHours(1), 10, 20, "COMMUTE");
		trip2.put("dest", endpoint("COMPANY", placeA, null));
		JsonNode results = uploadResults(deviceB, List.of(trip, trip2));
		assertThat(results.get(0).get("errorCode").asString()).isEqualTo("DRIVER_NOT_FOUND");
		assertThat(results.get(1).get("errorCode").asString()).isEqualTo("PLACE_NOT_FOUND");
	}

	private static void expectNotFound(ResultActions actions, String code) throws Exception {
		actions.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(code));
	}
}
