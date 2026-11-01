package com.autolog.server.trip;

import static com.autolog.server.support.TestClockConfiguration.BASE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class TripEditIntegrationTest extends IntegrationTest {

	@Autowired
	private TripEditLogRepository editLogRepository;

	private TestOrg org;
	private long vehicleId;
	private long driverId;
	private long otherDriverId;
	private PairedDevice device;

	@BeforeEach
	void setUp() throws Exception {
		org = newOrg();
		driverId = createDriver(org, "영업1팀", "김영업");
		otherDriverId = createDriver(org, "영업2팀", "이외근");
		vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);
		device = pair(org, vehicleId);
	}

	@Test
	void deviceEditAppendsHistoryAndAutoFillsBusinessCategory() throws Exception {
		String uuid = uploadTrip("UNCONFIRMED");
		String editUuid = UUID.randomUUID().toString();
		Map<String, Object> body = deviceEdit(editUuid, Map.of("purpose", "BUSINESS"));

		JsonNode data = data(postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.seq").value(1))
				.andExpect(jsonPath("$.data.edit.actorType").value("DEVICE"))
				.andExpect(jsonPath("$.data.edit.changes[0].field").value("purpose"))
				.andExpect(jsonPath("$.data.edit.changes[0].before").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.edit.changes[0].after").value("BUSINESS"))
				// BUSINESS로 바꾸면서 업무 세부가 없으면 OTHER (이력에 함께 기록)
				.andExpect(jsonPath("$.data.edit.changes[1].field").value("businessCategory"))
				.andExpect(jsonPath("$.data.edit.changes[1].before").isEmpty())
				.andExpect(jsonPath("$.data.edit.changes[1].after").value("OTHER"))
				.andExpect(jsonPath("$.data.current.purpose").value("BUSINESS"))
				.andExpect(jsonPath("$.data.current.purposeSource").value("MANUAL"))
				.andExpect(jsonPath("$.data.current.businessCategory").value("OTHER"))
				.andExpect(jsonPath("$.data.current.driverId").value(driverId)));
		assertThat(data.get("edit").get("changes")).hasSize(2);

		// 같은 clientEditUuid 재전송 → 처음 결과, 이력 1건 유지
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.seq").value(1))
				.andExpect(jsonPath("$.data.edit.changes[0].after").value("BUSINESS"));
		assertThat(editLogRepository.findAll().stream()
				.filter(log -> editUuid.equals(String.valueOf(log.getClientEditUuid()))).count()).isEqualTo(1);

		// BUSINESS가 아닌 값으로 바꾸면 업무 세부는 자동 null
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("purpose", "COMMUTE"), "VOICE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.seq").value(2))
				.andExpect(jsonPath("$.data.edit.changes[1].field").value("businessCategory"))
				.andExpect(jsonPath("$.data.edit.changes[1].before").value("OTHER"))
				.andExpect(jsonPath("$.data.edit.changes[1].after").isEmpty())
				.andExpect(jsonPath("$.data.current.purposeSource").value("VOICE"))
				.andExpect(jsonPath("$.data.current.businessCategory").isEmpty());

		// 용도를 바꾸지 않는 수정은 판정 출처 유지
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("note", "출근길")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.seq").value(3))
				.andExpect(jsonPath("$.data.current.purposeSource").value("VOICE"))
				.andExpect(jsonPath("$.data.current.note").value("출근길"));
	}

	@Test
	void noActualChangeReturnsNullEdit() throws Exception {
		String uuid = uploadTrip("COMMUTE");
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("purpose", "COMMUTE", "driverId", driverId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit").isEmpty())
				.andExpect(jsonPath("$.data.current.purpose").value("COMMUTE"))
				.andExpect(jsonPath("$.data.current.purposeSource").value("VOICE"));
	}

	@Test
	void autoFieldsAreImmutable() throws Exception {
		String uuid = uploadTrip("COMMUTE");
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("odoEndKm", 99999)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AUTO_FIELD_IMMUTABLE"))
				.andExpect(jsonPath("$.error.message").value("odoEndKm: 차량이 기록한 값은 수정할 수 없습니다."));
		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits",
				Map.of("reason", "정정", "changes", Map.of("startedAt", "2026-11-20T07:00:00+09:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AUTO_FIELD_IMMUTABLE"));
	}

	@Test
	void adminEditRequiresReasonAndUsesAdminSource() throws Exception {
		String uuid = uploadTrip("BUSINESS");
		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits", Map.of("changes", Map.of("note", "회의")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EDIT_REASON_REQUIRED"))
				.andExpect(jsonPath("$.error.message").value("관리자 수정에는 사유가 필요합니다."));
		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits",
				Map.of("reason", "  ", "changes", Map.of("note", "회의")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EDIT_REASON_REQUIRED"));

		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits", Map.of("reason", "운전자 확인 후 업무 세부 정정",
				"changes", Map.of("businessCategory", "MEETING", "note", "분기 실적 회의", "driverId", otherDriverId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.seq").value(1))
				.andExpect(jsonPath("$.data.edit.actorType").value("ADMIN"))
				.andExpect(jsonPath("$.data.edit.changes.length()").value(3))
				.andExpect(jsonPath("$.data.current.businessCategory").value("MEETING"))
				.andExpect(jsonPath("$.data.current.purposeSource").value("VOICE"))
				.andExpect(jsonPath("$.data.current.driverId").value(otherDriverId))
				.andExpect(jsonPath("$.data.current.note").value("분기 실적 회의"));

		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits",
				Map.of("reason", "출퇴근으로 정정", "changes", Map.of("purpose", "COMMUTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.current.purposeSource").value("ADMIN"))
				.andExpect(jsonPath("$.data.current.businessCategory").isEmpty());

		// 상세: 이력 2건, 수정자 이름, 사유, 시각
		clock.advance(Duration.ofMinutes(1));
		getWith(org.token(), "/api/v1/trips/" + uuid)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edits.length()").value(2))
				.andExpect(jsonPath("$.data.edits[0].actorName").value(adminUserRepository.findById(org.adminId())
						.orElseThrow().getDisplayName()))
				.andExpect(jsonPath("$.data.edits[0].reason").value("운전자 확인 후 업무 세부 정정"))
				.andExpect(jsonPath("$.data.edits[0].editedAt").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.edits[0].receivedAt").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.edits[1].changes[0].after").value("COMMUTE"))
				.andExpect(jsonPath("$.data.auto.purpose").value("BUSINESS"));
	}

	@Test
	void adminCannotEditPersonalOrMakePersonal() throws Exception {
		String personal = uploadTrip("PERSONAL");
		postWith(org.token(), "/api/v1/trips/" + personal + "/edits",
				Map.of("reason", "확인", "changes", Map.of("purpose", "BUSINESS")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN"))
				.andExpect(jsonPath("$.error.message").value("개인 운행은 운전자만 수정할 수 있습니다."));
		postWith(org.token(), "/api/v1/trips/" + personal + "/edits",
				Map.of("reason", "확인", "changes", Map.of("driverId", otherDriverId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN"));

		String business = uploadTrip("BUSINESS");
		postWith(org.token(), "/api/v1/trips/" + business + "/edits",
				Map.of("reason", "개인으로", "changes", Map.of("purpose", "PERSONAL")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN"));

		// 운전자(차량)는 개인 운행을 바꿀 수 있다
		postWith(device.token(), "/api/v1/trips/" + personal + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("purpose", "COMMUTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.current.purpose").value("COMMUTE"));
	}

	@Test
	void adminEditResponseOfUnconfirmedTripIsMasked() throws Exception {
		String uuid = uploadTrip("UNCONFIRMED", "운전자 메모");
		postWith(org.token(), "/api/v1/trips/" + uuid + "/edits",
				Map.of("reason", "이용자 정정", "changes", Map.of("driverId", otherDriverId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.current.driverId").value(otherDriverId))
				.andExpect(jsonPath("$.data.current.note").isEmpty());
	}

	@Test
	void deviceEditValidation() throws Exception {
		String uuid = uploadTrip("UNCONFIRMED");
		// purpose를 바꾸는데 purposeSource 없음
		Map<String, Object> noSource = new HashMap<>(deviceEdit(UUID.randomUUID().toString(),
				Map.of("purpose", "BUSINESS")));
		noSource.remove("purposeSource");
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", noSource)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		// clientEditUuid 없음
		Map<String, Object> noKey = new HashMap<>(deviceEdit(UUID.randomUUID().toString(), Map.of("note", "x")));
		noKey.remove("clientEditUuid");
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", noKey)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		// editedAt이 10분 넘게 미래
		Map<String, Object> future = new HashMap<>(deviceEdit(UUID.randomUUID().toString(), Map.of("note", "x")));
		future.put("editedAt", BASE_TIME.plusMinutes(11).toString());
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", future)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("editedAt: 현재 시각 이후일 수 없습니다."));
		// BUSINESS가 아닌데 업무 세부 지정
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("purpose", "COMMUTE", "businessCategory", "VISIT")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		// 다른 회사 직원
		long foreignDriver = createDriver(newOrg(), "타사", "박타사");
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("driverId", foreignDriver)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("DRIVER_NOT_FOUND"));
		// 이용자 해제(null)
		Map<String, Object> clearDriver = new LinkedHashMap<>();
		clearDriver.put("driverId", null);
		postWith(device.token(), "/api/v1/trips/" + uuid + "/edits", deviceEdit(UUID.randomUUID().toString(),
				clearDriver))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.edit.changes[0].before").value(driverId))
				.andExpect(jsonPath("$.data.current.driverId").isEmpty());
	}

	@Test
	void tripNotFoundCases() throws Exception {
		// 아직 업로드되지 않은 트립
		postWith(device.token(), "/api/v1/trips/" + UUID.randomUUID() + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("note", "x")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("운행 기록을 찾을 수 없습니다."));
		// 같은 회사 다른 차량의 트립
		String uuid = uploadTrip("COMMUTE");
		long otherVehicle = createVehicle(org, "34나5678", "쏘나타");
		PairedDevice otherDevice = pair(org, otherVehicle);
		postWith(otherDevice.token(), "/api/v1/trips/" + uuid + "/edits",
				deviceEdit(UUID.randomUUID().toString(), Map.of("note", "x")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	private String uploadTrip(String purpose) throws Exception {
		return uploadTrip(purpose, null);
	}

	private String uploadTrip(String purpose, String note) throws Exception {
		Map<String, Object> trip = trip(vehicleId, BASE_TIME.minusHours(4), 100, 120, purpose);
		trip.put("driverId", driverId);
		trip.put("note", note);
		assertThat(uploadResults(device, List.of(trip)).get(0).get("result").asString()).isEqualTo("CREATED");
		return (String) trip.get("clientUuid");
	}

	private static Map<String, Object> deviceEdit(String clientEditUuid, Map<String, Object> changes) {
		return deviceEdit(clientEditUuid, changes, "MANUAL");
	}

	private static Map<String, Object> deviceEdit(String clientEditUuid, Map<String, Object> changes,
			String purposeSource) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("clientEditUuid", clientEditUuid);
		body.put("editedAt", BASE_TIME.minusMinutes(1).toString());
		body.put("purposeSource", purposeSource);
		body.put("changes", changes);
		return body;
	}
}
