package com.autolog.server.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class TripQueryIntegrationTest extends IntegrationTest {

	private TestOrg org;
	private long vehicleId;
	private long driverId;
	private long hqId;
	private PairedDevice device;
	private String commuteUuid;
	private String personalUuid;

	@BeforeEach
	void setUp() throws Exception {
		org = newOrg();
		driverId = createDriver(org, "영업1팀", "김영업");
		vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);
		hqId = createPlace(org, "본사", "COMPANY");
		device = pair(org, vehicleId);

		Map<String, Object> commute = trip(vehicleId, OffsetDateTime.parse("2026-11-03T08:12:00+09:00"),
				12034.5, 12051.2, "COMMUTE");
		commute.put("dest", endpoint("COMPANY", hqId, null));
		commute.put("driverId", driverId);
		Map<String, Object> personal = trip(vehicleId, OffsetDateTime.parse("2026-11-02T19:00:00+09:00"),
				11990.0, 12034.5, "PERSONAL");
		personal.put("note", "병원");
		personal.put("driverId", driverId);
		Map<String, Object> october = trip(vehicleId, OffsetDateTime.parse("2026-10-31T10:00:00+09:00"),
				11900.0, 11990.0, "BUSINESS");
		uploadResults(device, List.of(commute, personal, october));
		commuteUuid = (String) commute.get("clientUuid");
		personalUuid = (String) personal.get("clientUuid");
	}

	@Test
	void listIsMaskedAndSortedByStartDesc() throws Exception {
		JsonNode data = data(getWith(org.token(), "/api/v1/trips?from=2026-11-01&to=2026-11-30")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(1))
				.andExpect(jsonPath("$.data.content[0].clientUuid").value(commuteUuid))
				.andExpect(jsonPath("$.data.content[0].tripDate").value("2026-11-03"))
				.andExpect(jsonPath("$.data.content[0].vehicle.id").value(vehicleId))
				.andExpect(jsonPath("$.data.content[0].vehicle.regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.content[0].driver.dept").value("영업1팀"))
				.andExpect(jsonPath("$.data.content[0].startedAt").value("2026-11-03T08:12:00+09:00"))
				.andExpect(jsonPath("$.data.content[0].endedAt").value("2026-11-03T08:47:00+09:00"))
				.andExpect(jsonPath("$.data.content[0].odoStartKm").value(12034.5))
				.andExpect(jsonPath("$.data.content[0].distanceKm").value(16.7))
				.andExpect(jsonPath("$.data.content[0].origin.type").value("HOME"))
				.andExpect(jsonPath("$.data.content[0].origin.label").value("집"))
				.andExpect(jsonPath("$.data.content[0].dest.label").value("본사"))
				.andExpect(jsonPath("$.data.content[0].edited").value(false))
				.andExpect(jsonPath("$.data.content[0].masked").value(false))
				.andExpect(jsonPath("$.data.content[1].clientUuid").value(personalUuid))
				.andExpect(jsonPath("$.data.content[1].startedAt").isEmpty())
				.andExpect(jsonPath("$.data.content[1].endedAt").isEmpty())
				.andExpect(jsonPath("$.data.content[1].origin").isEmpty())
				.andExpect(jsonPath("$.data.content[1].dest").isEmpty())
				.andExpect(jsonPath("$.data.content[1].note").isEmpty())
				.andExpect(jsonPath("$.data.content[1].odoStartKm").value(11990.0))
				.andExpect(jsonPath("$.data.content[1].distanceKm").value(44.5))
				.andExpect(jsonPath("$.data.content[1].masked").value(true)));
		// 응답 원문에 개인 운행 메모가 없어야 한다
		assertThat(data.toString()).doesNotContain("병원").doesNotContain("스타벅스");
		// 계약 필드 순서·구성
		assertThat(fieldNames(data.get("content").get(0))).containsExactly("clientUuid", "tripDate", "vehicle",
				"driver", "startedAt", "endedAt", "odoStartKm", "odoEndKm", "distanceKm", "origin", "dest", "purpose",
				"purposeSource", "businessCategory", "note", "edited", "masked");
	}

	@Test
	void filtersPagingAndValidation() throws Exception {
		assertThat(data(getWith(org.token(), "/api/v1/trips?from=2026-10-01&to=2026-11-30")).get("totalElements")
				.asInt()).isEqualTo(3);
		assertThat(data(getWith(org.token(), "/api/v1/trips?from=2026-10-01&to=2026-11-30&purpose=PERSONAL"))
				.get("totalElements").asInt()).isEqualTo(1);
		assertThat(data(getWith(org.token(), "/api/v1/trips?from=2026-10-01&to=2026-11-30&driverId=" + driverId))
				.get("totalElements").asInt()).isEqualTo(2);
		assertThat(data(getWith(org.token(), "/api/v1/trips?from=2026-10-01&to=2026-11-30&vehicleId=" + vehicleId))
				.get("totalElements").asInt()).isEqualTo(3);
		JsonNode page = data(getWith(org.token(), "/api/v1/trips?from=2026-10-01&to=2026-11-30&page=1&size=2"));
		assertThat(page.get("content")).hasSize(1);
		assertThat(page.get("totalPages").asInt()).isEqualTo(2);

		getWith(org.token(), "/api/v1/trips?from=2026-01-01&to=2027-01-02")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("to: from 이후 366일 이내여야 합니다."));
		getWith(org.token(), "/api/v1/trips?from=2026-01-01&to=2027-01-01").andExpect(status().isOk());
		getWith(org.token(), "/api/v1/trips?from=2026-11-30&to=2026-11-01").andExpect(status().isBadRequest());
		getWith(org.token(), "/api/v1/trips?from=2026-11-01").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		getWith(org.token(), "/api/v1/trips?from=2026-11&to=2026-11-30").andExpect(status().isBadRequest());
		getWith(org.token(), "/api/v1/trips?from=2026-11-01&to=2026-11-30&size=101").andExpect(status().isBadRequest());
		getWith(org.token(), "/api/v1/trips?from=2026-11-01&to=2026-11-30&purpose=WORK")
				.andExpect(status().isBadRequest());
	}

	@Test
	void detailIncludesAutoValuesAndHistoryWithMasking() throws Exception {
		// 차량에서 개인 운행 메모 수정 → 이력의 메모 값도 가림
		postWith(device.token(), "/api/v1/trips/" + personalUuid + "/edits", Map.of(
				"clientEditUuid", UUID.randomUUID().toString(), "editedAt", "2026-11-20T11:00:00+09:00",
				"changes", Map.of("note", "치과")))
				.andExpect(status().isOk());

		JsonNode detail = data(getWith(org.token(), "/api/v1/trips/" + personalUuid)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.masked").value(true))
				.andExpect(jsonPath("$.data.note").isEmpty())
				.andExpect(jsonPath("$.data.startedAt").isEmpty())
				.andExpect(jsonPath("$.data.auto.purpose").value("PERSONAL"))
				.andExpect(jsonPath("$.data.edits[0].seq").value(1))
				.andExpect(jsonPath("$.data.edits[0].actorType").value("DEVICE"))
				.andExpect(jsonPath("$.data.edits[0].actorName").value("12가3456 차량"))
				.andExpect(jsonPath("$.data.edits[0].changes[0].field").value("note"))
				.andExpect(jsonPath("$.data.edits[0].changes[0].before").isEmpty())
				.andExpect(jsonPath("$.data.edits[0].changes[0].after").isEmpty())
				.andExpect(jsonPath("$.data.edits[0].editedAt").value("2026-11-20T11:00:00+09:00"))
				.andExpect(jsonPath("$.data.appVersion").value("0.1.0")));
		assertThat(detail.toString()).doesNotContain("병원").doesNotContain("치과");
		// 목록에서도 edited 표시
		getWith(org.token(), "/api/v1/trips?from=2026-11-01&to=2026-11-30&purpose=PERSONAL")
				.andExpect(jsonPath("$.data.content[0].edited").value(true));

		JsonNode commute = data(getWith(org.token(), "/api/v1/trips/" + commuteUuid)
				.andExpect(jsonPath("$.data.masked").value(false))
				.andExpect(jsonPath("$.data.recordedAt").value("2026-11-03T08:50:00+09:00"))
				.andExpect(jsonPath("$.data.syncedAt").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.edits").isEmpty()));
		assertThat(fieldNames(commute)).containsExactly("clientUuid", "tripDate", "vehicle", "driver", "startedAt",
				"endedAt", "odoStartKm", "odoEndKm", "distanceKm", "origin", "dest", "purpose", "purposeSource",
				"businessCategory", "note", "masked", "auto", "edits", "recordedAt", "syncedAt", "appVersion");

		getWith(org.token(), "/api/v1/trips/" + UUID.randomUUID())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
		getWith(org.token(), "/api/v1/trips/not-a-uuid")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	private static List<String> fieldNames(JsonNode node) {
		return node.propertyNames().stream().toList();
	}
}
