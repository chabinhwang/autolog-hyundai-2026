package com.autolog.server.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ReportIntegrationTest extends IntegrationTest {

	private TestOrg org;
	private long driverId;
	private long vehicleId;
	private long clientId;
	private PairedDevice device;

	@BeforeEach
	void setUp() throws Exception {
		org = newOrg();
		driverId = createDriver(org, "영업1팀", "김영업");
		vehicleId = createVehicle(org, "12가3456", "아이오닉 5", driverId);
		clientId = createPlace(org, "한빛상사", "CLIENT");
		device = pair(org, vehicleId);
	}

	@Test
	void monthlySummaryRatiosAndWarnings() throws Exception {
		// 11월: 출퇴근 20.0 + 업무 30.0 + 개인 10.0 + 미확인 40.0 = 100.0 (11-05 → 11-06 사이 ODO 2km 공백)
		uploadResults(device, List.of(
				at("2026-11-04T08:00:00+09:00", 1000.0, 1020.0, "COMMUTE"),
				at("2026-11-05T10:00:00+09:00", 1020.0, 1050.0, "BUSINESS"),
				at("2026-11-06T19:00:00+09:00", 1052.0, 1062.0, "PERSONAL"),
				at("2026-11-07T09:00:00+09:00", 1062.0, 1102.0, "UNCONFIRMED"),
				// 10월 트립은 11월 집계에서 빠짐
				at("2026-10-31T09:00:00+09:00", 900.0, 1000.0, "BUSINESS")));

		JsonNode vehicle = data(getWith(org.token(), "/api/v1/dashboard/summary?month=2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.month").value("2026-11"))
				.andExpect(jsonPath("$.data.vehicles.length()").value(1))
				.andExpect(jsonPath("$.data.vehicles[0].vehicleId").value(vehicleId))
				.andExpect(jsonPath("$.data.vehicles[0].regNo").value("12가3456"))
				.andExpect(jsonPath("$.data.vehicles[0].modelName").value("아이오닉 5"))
				.andExpect(jsonPath("$.data.vehicles[0].defaultDriver.id").value(driverId))
				.andExpect(jsonPath("$.data.vehicles[0].paired").value(true))
				.andExpect(jsonPath("$.data.vehicles[0].lastSyncedAt").value("2026-11-20T12:00:00+09:00"))
				.andExpect(jsonPath("$.data.vehicles[0].tripCount").value(4))
				.andExpect(jsonPath("$.data.vehicles[0].totalKm").value(100.0))
				.andExpect(jsonPath("$.data.vehicles[0].commuteKm").value(20.0))
				.andExpect(jsonPath("$.data.vehicles[0].businessKm").value(30.0))
				.andExpect(jsonPath("$.data.vehicles[0].personalKm").value(10.0))
				.andExpect(jsonPath("$.data.vehicles[0].unconfirmedKm").value(40.0))
				.andExpect(jsonPath("$.data.vehicles[0].unconfirmedCount").value(1))
				.andExpect(jsonPath("$.data.vehicles[0].businessRatio").value(50.0))
				.andExpect(jsonPath("$.data.vehicles[0].maxBusinessRatio").value(90.0))
				.andExpect(jsonPath("$.data.totals.totalKm").value(100.0))
				.andExpect(jsonPath("$.data.totals.unconfirmedCount").value(1))
				.andExpect(jsonPath("$.data.totals.businessRatio").value(50.0)))
				.get("vehicles").get(0);
		assertThat(vehicle.get("warnings").toString()).isEqualTo("[\"UNCONFIRMED_EXISTS\",\"ODO_GAP\"]");
		// 거리는 소수 첫째 자리 number로 직렬화
		assertThat(vehicle.get("totalKm").toString()).isEqualTo("100.0");
	}

	@Test
	void ratioHighAndSyncDelayedAndEmptyMonth() throws Exception {
		uploadResults(device, List.of(
				at("2026-11-04T08:00:00+09:00", 1000.0, 1100.0, "BUSINESS"),
				at("2026-11-05T08:00:00+09:00", 1100.0, 1103.0, "PERSONAL")));
		long idle = createVehicle(org, "34나5678", "쏘나타");

		clock.advance(Duration.ofHours(48).plusMinutes(1));
		String token = jwtService.issue(org.adminId(), org.orgId()).value();
		JsonNode vehicles = data(getWith(token, "/api/v1/dashboard/summary?month=2026-11")).get("vehicles");
		JsonNode busy = vehicles.get(0);
		// 100 / 103 = 97.08… → 97.1
		assertThat(busy.get("businessRatio").decimalValue()).isEqualByComparingTo("97.1");
		assertThat(busy.get("warnings").toString()).isEqualTo("[\"RATIO_HIGH\",\"SYNC_DELAYED\"]");

		JsonNode empty = vehicles.get(1);
		assertThat(empty.get("vehicleId").asLong()).isEqualTo(idle);
		assertThat(empty.get("paired").asBoolean()).isFalse();
		assertThat(empty.get("totalKm").decimalValue()).isEqualByComparingTo("0");
		assertThat(empty.get("businessRatio").isNull()).isTrue();
		assertThat(empty.get("maxBusinessRatio").isNull()).isTrue();
		// 페어링 안 된 차량은 SYNC_DELAYED 없음
		assertThat(empty.get("warnings")).isEmpty();

		getWith(token, "/api/v1/dashboard/summary?month=2026-13")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("month: YYYY-MM 형식이어야 합니다."));
		getWith(token, "/api/v1/dashboard/summary")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void logbookXlsxRowsTotalsAndMasking() throws Exception {
		Map<String, Object> business = at("2026-11-03T10:00:00+09:00", 1020.0, 1050.5, "BUSINESS");
		business.put("dest", endpoint("CLIENT", clientId, null));
		business.put("businessCategory", "VISIT");
		business.put("note", "견적 전달");
		Map<String, Object> personal = at("2026-11-03T19:00:00+09:00", 1050.5, 1060.5, "PERSONAL");
		personal.put("note", "비밀 메모");
		personal.put("dest", endpoint("UNREGISTERED", null, "동네 병원"));
		uploadResults(device, List.of(
				at("2026-11-03T08:00:00+09:00", 1000.0, 1020.0, "COMMUTE"),
				business, personal,
				at("2026-11-04T09:00:00+09:00", 1060.5, 1070.5, "UNCONFIRMED"),
				at("2026-12-01T09:00:00+09:00", 1070.5, 1080.5, "BUSINESS")));

		byte[] file = getWith(org.token(),
				"/api/v1/reports/logbook?vehicleId=" + vehicleId + "&from=2026-11-01&to=2026-11-30&format=xlsx")
				.andExpect(status().isOk())
				.andExpect(content().contentType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"logbook.xlsx\"; "
						+ "filename*=UTF-8''%EC%9A%B4%ED%96%89%EA%B8%B0%EB%A1%9D%EB%B6%80_12%EA%B0%803456"
						+ "_2026-11-01_2026-11-30.xlsx"))
				.andReturn().getResponse().getContentAsByteArray();

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheet(LogbookXlsxWriter.SHEET_NAME);
			assertThat(text(sheet, 1, 1)).isEqualTo("2026-11-01 ~ 2026-11-30");
			assertThat(text(sheet, 2, 1)).isEqualTo(org.name());
			assertThat(text(sheet, 3, 1)).isEqualTo(org.bizRegNo());
			assertThat(text(sheet, 4, 1)).isEqualTo("아이오닉 5");
			assertThat(text(sheet, 5, 1)).isEqualTo("12가3456");
			assertThat(text(sheet, LogbookXlsxWriter.WARNING_ROW, 0)).contains("미확인").contains("1건");
			Row header = sheet.getRow(LogbookXlsxWriter.HEADER_ROW);
			List<String> headers = new ArrayList<>();
			header.forEach(cell -> headers.add(cell.getStringCellValue()));
			assertThat(headers).isEqualTo(LogbookXlsxWriter.COLUMNS);

			int first = LogbookXlsxWriter.HEADER_ROW + 1;
			// 1행: 출퇴근
			assertThat(text(sheet, first, 0)).isEqualTo("2026-11-03(화)");
			assertThat(text(sheet, first, 1)).isEqualTo("영업1팀");
			assertThat(text(sheet, first, 2)).isEqualTo("김영업");
			assertThat(number(sheet, first, 3)).isEqualTo(1000.0);
			assertThat(number(sheet, first, 4)).isEqualTo(1020.0);
			assertThat(number(sheet, first, 5)).isEqualTo(20.0);
			assertThat(number(sheet, first, 6)).isEqualTo(20.0);
			assertThat(sheet.getRow(first).getCell(7).getCellType()).isEqualTo(CellType.BLANK);
			assertThat(text(sheet, first, 8)).isEqualTo("출퇴근");
			// 2행: 업무 (업무 세부 + 도착지 + 메모)
			assertThat(number(sheet, first + 1, 7)).isEqualTo(30.5);
			assertThat(text(sheet, first + 1, 8)).isEqualTo("거래처 방문 / 한빛상사 / 견적 전달");
			// 3행: 개인 (계기판 거리는 공개, 비고 빈칸)
			assertThat(number(sheet, first + 2, 3)).isEqualTo(1050.5);
			assertThat(number(sheet, first + 2, 5)).isEqualTo(10.0);
			assertThat(text(sheet, first + 2, 8)).isEmpty();
			// 4행: 미확인
			assertThat(text(sheet, first + 3, 0)).isEqualTo("2026-11-04(수)");
			assertThat(text(sheet, first + 3, 8)).isEqualTo("미확인");
			// 12월 트립은 기간 밖
			int totalRow = first + 5;
			assertThat(text(sheet, totalRow, 0)).isEqualTo(LogbookXlsxWriter.TOTAL_KM_LABEL);
			assertThat(number(sheet, totalRow, 1)).isEqualTo(70.5);
			assertThat(text(sheet, totalRow + 1, 0)).isEqualTo(LogbookXlsxWriter.BUSINESS_KM_LABEL);
			assertThat(number(sheet, totalRow + 1, 1)).isEqualTo(50.5);
			assertThat(text(sheet, totalRow + 2, 0)).isEqualTo(LogbookXlsxWriter.RATIO_LABEL);
			assertThat(number(sheet, totalRow + 2, 1)).isEqualTo(71.6);

			// 시트 어디에도 개인 운행의 메모·장소가 없어야 한다
			StringBuilder all = new StringBuilder();
			sheet.forEach(row -> row.forEach(cell -> all.append(cell.toString()).append('|')));
			assertThat(all.toString()).doesNotContain("비밀 메모").doesNotContain("동네 병원");
		}
	}

	@Test
	void logbookValidation() throws Exception {
		getWith(org.token(), "/api/v1/reports/logbook?vehicleId=" + vehicleId + "&from=2026-01-01&to=2026-12-31&format=pdf")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.message").value("format: 지원하는 형식은 xlsx입니다."));
		getWith(org.token(), "/api/v1/reports/logbook?vehicleId=" + vehicleId + "&from=2026-01-01&to=2027-01-02")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
		getWith(org.token(), "/api/v1/reports/logbook?vehicleId=999999&from=2026-01-01&to=2026-12-31")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("VEHICLE_NOT_FOUND"));
		// 기본 형식은 xlsx, 빈 기간도 파일 생성
		getWith(org.token(), "/api/v1/reports/logbook?vehicleId=" + vehicleId + "&from=2026-01-01&to=2026-12-31")
				.andExpect(status().isOk());
	}

	private Map<String, Object> at(String startedAt, double odoStart, double odoEnd, String purpose) {
		Map<String, Object> trip = trip(vehicleId, OffsetDateTime.parse(startedAt), odoStart, odoEnd, purpose);
		trip.put("driverId", driverId);
		return trip;
	}

	private static String text(Sheet sheet, int row, int column) {
		return sheet.getRow(row).getCell(column).getStringCellValue();
	}

	private static double number(Sheet sheet, int row, int column) {
		return sheet.getRow(row).getCell(column).getNumericCellValue();
	}
}
