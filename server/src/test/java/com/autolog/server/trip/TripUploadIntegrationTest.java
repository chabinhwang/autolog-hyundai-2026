package com.autolog.server.trip;

import static com.autolog.server.support.TestClockConfiguration.BASE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.device.DeviceRepository;
import com.autolog.server.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class TripUploadIntegrationTest extends IntegrationTest {

	@Autowired
	private TripRepository tripRepository;
	@Autowired
	private DeviceRepository deviceRepository;

	private TestOrg org;
	private long vehicleId;
	private long driverId;
	private long hqId;
	private long clientId;
	private PairedDevice device;

	@BeforeEach
	void setUp() throws Exception {
		org = newOrg();
		driverId = createDriver(org, "영업1팀", "김영업");
		vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);
		hqId = createPlace(org, "본사", "COMPANY");
		clientId = createPlace(org, "한빛상사", "CLIENT");
		device = pair(org, vehicleId);
	}

	@Test
	void createdThenDuplicateAndServerComputesDateDistanceAndLabels() throws Exception {
		Map<String, Object> commute = trip(vehicleId, OffsetDateTime.parse("2026-11-20T08:12:00+09:00"),
				12034.5, 12051.2, "COMMUTE");
		commute.put("origin", endpoint("HOME", null, "우리집 상세주소"));
		commute.put("dest", endpoint("COMPANY", hqId, "요청 라벨은 무시"));
		commute.put("driverId", driverId);
		// UTC로는 전날(11-19)이지만 서울 날짜는 11-20
		Map<String, Object> lateNight = trip(vehicleId, OffsetDateTime.parse("2026-11-19T15:30:00Z"),
				12051.2, 12060.04, "BUSINESS");
		lateNight.put("dest", endpoint("CLIENT", clientId, null));
		lateNight.put("businessCategory", "VISIT");
		lateNight.put("note", "견적 전달");

		JsonNode results = uploadResults(device, List.of(commute, lateNight));
		assertThat(results).hasSize(2);
		assertThat(results.get(0).get("clientUuid").asString()).isEqualTo(commute.get("clientUuid"));
		assertThat(results.get(0).get("result").asString()).isEqualTo("CREATED");
		assertThat(results.get(0).get("errorCode").isNull()).isTrue();
		assertThat(results.get(1).get("result").asString()).isEqualTo("CREATED");

		Trip saved = tripRepository.findByClientUuidAndOrgId(UUID.fromString((String) commute.get("clientUuid")),
				org.orgId()).orElseThrow();
		assertThat(saved.getTripDate()).hasToString("2026-11-20");
		assertThat(saved.getDistanceKm()).isEqualByComparingTo("16.7");
		assertThat(saved.getOrigin().label()).isEqualTo("집");
		assertThat(saved.getDest().label()).isEqualTo("본사");
		assertThat(saved.getDest().placeId()).isEqualTo(hqId);
		assertThat(saved.getAppVersion()).isEqualTo("0.1.0");
		assertThat(saved.getDeviceId()).isEqualTo(device.deviceId());

		Trip night = tripRepository.findByClientUuidAndOrgId(UUID.fromString((String) lateNight.get("clientUuid")),
				org.orgId()).orElseThrow();
		assertThat(night.getTripDate()).hasToString("2026-11-20");
		// 계기판 값은 소수 첫째 자리로 반올림
		assertThat(night.getOdoEndKm()).isEqualByComparingTo("12060.0");
		assertThat(night.getDest().label()).isEqualTo("한빛상사");

		// 같은 clientUuid 재전송 → 내용 비교 없이 DUPLICATE, 저장값 유지
		commute.put("note", "바뀐 메모");
		JsonNode again = uploadResults(device, List.of(commute));
		assertThat(again.get(0).get("result").asString()).isEqualTo("DUPLICATE");
		assertThat(tripRepository.findByClientUuidAndOrgId(saved.getClientUuid(), org.orgId()).orElseThrow()
				.getNote()).isNull();

		// 업로드 성공 시 마지막 동기화 시각 갱신
		assertThat(deviceRepository.findById(device.deviceId()).orElseThrow().getLastSyncedAt())
				.isEqualTo(BASE_TIME.toInstant());
	}

	@Test
	void duplicateInsideSameBatch() throws Exception {
		Map<String, Object> trip = trip(vehicleId, BASE_TIME.minusHours(2), 100, 110, "BUSINESS");
		JsonNode results = uploadResults(device, List.of(trip, new LinkedHashMap<>(trip)));
		assertThat(results.get(0).get("result").asString()).isEqualTo("CREATED");
		assertThat(results.get(1).get("result").asString()).isEqualTo("DUPLICATE");
	}

	@Test
	void validationFailuresAreRejectedPerTrip() throws Exception {
		List<Consumer<Map<String, Object>>> breakers = List.of(
				t -> t.put("odoEndKm", 99.0),                                   // odoEnd < odoStart
				t -> t.put("odoStartKm", -1.0),
				t -> t.put("endedAt", t.get("startedAt")),                      // endedAt > startedAt 아님
				t -> t.put("purpose", "WORK"),                                  // 열거값
				t -> t.put("purposeSource", "ADMIN"),                           // 차량은 ADMIN 불가
				t -> { t.put("purpose", "UNCONFIRMED"); t.put("purposeSource", "VOICE"); },
				t -> t.put("businessCategory", "VISIT"),                        // BUSINESS가 아님
				t -> t.put("dest", endpoint("CLIENT", null, null)),             // placeId 필수
				t -> t.put("origin", endpoint("HOME", hqId, null)),             // HOME은 placeId null
				t -> t.put("dest", endpoint("UNREGISTERED", null, "가".repeat(101))),
				t -> t.put("note", "가".repeat(201)),
				t -> t.put("startedAt", "2026-11-20 08:00"),                    // ISO-8601 아님
				t -> t.remove("recordedAt"),
				t -> t.put("vehicleId", null),
				t -> t.put("clientUuid", "not-a-uuid"));
		List<Map<String, Object>> trips = new ArrayList<>();
		for (Consumer<Map<String, Object>> breaker : breakers) {
			Map<String, Object> trip = trip(vehicleId, BASE_TIME.minusHours(5), 100, 110, "COMMUTE");
			breaker.accept(trip);
			trips.add(trip);
		}
		Map<String, Object> valid = trip(vehicleId, BASE_TIME.minusHours(5), 100, 110, "COMMUTE");
		trips.add(valid);

		JsonNode results = uploadResults(device, trips);
		assertThat(results).hasSize(breakers.size() + 1);
		for (int i = 0; i < breakers.size(); i++) {
			assertThat(results.get(i).get("result").asString()).as("trip %d", i).isEqualTo("REJECTED");
			assertThat(results.get(i).get("errorCode").asString()).as("trip %d", i).isEqualTo("VALIDATION_FAILED");
		}
		assertThat(results.get(breakers.size() - 1).get("clientUuid").asString()).isEqualTo("not-a-uuid");
		assertThat(results.get(breakers.size()).get("result").asString()).isEqualTo("CREATED");
	}

	@Test
	void vehicleMismatchTimestampDriverAndPlaceErrors() throws Exception {
		TestOrg otherOrg = newOrg();
		long otherDriver = createDriver(otherOrg, "타사", "박타사");
		long otherPlace = createPlace(otherOrg, "타사 본사", "COMPANY");
		long inactiveDriver = createDriver(org, "퇴사", "최퇴사");
		patchWith(org.token(), "/api/v1/drivers/" + inactiveDriver, Map.of("active", false)).andExpect(status().isOk());

		Map<String, Object> mismatch = trip(vehicleId + 1000, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		Map<String, Object> future = trip(vehicleId, BASE_TIME.plusMinutes(11), 100, 110, "COMMUTE");
		Map<String, Object> futureRecorded = trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		futureRecorded.put("recordedAt", BASE_TIME.plusMinutes(10).plusSeconds(1).toString());
		Map<String, Object> tooOld = trip(vehicleId, BASE_TIME.minusDays(366).minusMinutes(1), 100, 110, "COMMUTE");
		Map<String, Object> oldButOk = trip(vehicleId, BASE_TIME.minusDays(365), 100, 110, "COMMUTE");
		Map<String, Object> foreignDriver = trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		foreignDriver.put("driverId", otherDriver);
		Map<String, Object> retiredDriver = trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		retiredDriver.put("driverId", inactiveDriver);
		Map<String, Object> foreignPlace = trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		foreignPlace.put("dest", endpoint("COMPANY", otherPlace, null));

		JsonNode results = uploadResults(device, List.of(mismatch, future, futureRecorded, tooOld, oldButOk,
				foreignDriver, retiredDriver, foreignPlace));
		assertRejected(results.get(0), "VEHICLE_MISMATCH");
		assertRejected(results.get(1), "TIMESTAMP_OUT_OF_RANGE");
		assertRejected(results.get(2), "TIMESTAMP_OUT_OF_RANGE");
		assertRejected(results.get(3), "TIMESTAMP_OUT_OF_RANGE");
		assertThat(results.get(4).get("result").asString()).isEqualTo("CREATED");
		assertRejected(results.get(5), "DRIVER_NOT_FOUND");
		assertRejected(results.get(6), "DRIVER_NOT_FOUND");
		assertRejected(results.get(7), "PLACE_NOT_FOUND");
	}

	@Test
	void requestLevelValidation() throws Exception {
		upload(device, List.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("trips: 1~50건이어야 합니다."));
		List<Map<String, Object>> tooMany = new ArrayList<>();
		for (int i = 0; i < 51; i++) {
			tooMany.add(trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE"));
		}
		upload(device, tooMany)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("trips: 1~50건이어야 합니다."));
		perform(put("/api/v1/trips/batch"),
				device.token(), Map.of("appVersion", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("trips: 1~50건이어야 합니다."));
	}

	@Test
	void revokedDeviceCannotUpload() throws Exception {
		deleteWith(org.token(), "/api/v1/vehicles/" + vehicleId + "/device").andExpect(status().isOk());
		upload(device, List.of(trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("DEVICE_TOKEN_INVALID"));
	}

	@Test
	void rePairedSameVehicleUploadsWithNewDevice() throws Exception {
		Map<String, Object> pending = trip(vehicleId, BASE_TIME.minusHours(1), 100, 110, "COMMUTE");
		PairedDevice newDevice = pair(org, vehicleId);
		assertThat(uploadResults(newDevice, List.of(pending)).get(0).get("result").asString()).isEqualTo("CREATED");
		assertThat(tripRepository.findByClientUuidAndOrgId(UUID.fromString((String) pending.get("clientUuid")),
				org.orgId()).orElseThrow().getDeviceId()).isEqualTo(newDevice.deviceId());

		// 다른 차량에 페어링된 앱이 이전 차량 트립을 올리면 VEHICLE_MISMATCH
		long otherVehicle = createVehicle(org, "34나5678", "쏘나타");
		PairedDevice moved = pair(org, otherVehicle);
		Map<String, Object> oldVehicleTrip = trip(vehicleId, BASE_TIME.minusHours(1), 110, 120, "COMMUTE");
		assertRejected(uploadResults(moved, List.of(oldVehicleTrip)).get(0), "VEHICLE_MISMATCH");
	}

	private static void assertRejected(JsonNode result, String errorCode) {
		assertThat(result.get("result").asString()).isEqualTo("REJECTED");
		assertThat(result.get("errorCode").asString()).isEqualTo(errorCode);
	}
}
