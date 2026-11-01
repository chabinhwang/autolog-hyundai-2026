package com.autolog.server.report;

import com.autolog.server.global.response.ApiResponse;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.report.ReportDtos.SummaryResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드·보고서 API (docs/contracts/api/reports.md) */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

	public static final MediaType XLSX = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	/** filename*를 해석하지 못하는 클라이언트용 ASCII 파일명 */
	static final String FALLBACK_FILENAME = "logbook.xlsx";

	private final DashboardService dashboardService;
	private final LogbookService logbookService;

	public ReportController(DashboardService dashboardService, LogbookService logbookService) {
		this.dashboardService = dashboardService;
		this.logbookService = logbookService;
	}

	@GetMapping("/dashboard/summary")
	public ApiResponse<SummaryResponse> summary(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam(required = false) String month) {
		return ApiResponse.ok(dashboardService.summary(admin.orgId(), month));
	}

	/** 성공 응답은 공통 구조의 예외: 본문이 XLSX 파일 (실패는 JSON) */
	@GetMapping("/reports/logbook")
	public ResponseEntity<byte[]> logbook(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam Long vehicleId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String format) {
		LogbookService.LogbookFile file = logbookService.export(admin.orgId(), vehicleId, from, to, format);
		return ResponseEntity.ok()
				.contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
				.body(file.content());
	}

	/** RFC 6266 + RFC 5987: attachment; filename="logbook.xlsx"; filename*=UTF-8''… */
	static String contentDisposition(String filename) {
		return "attachment; filename=\"" + FALLBACK_FILENAME + "\"; filename*=UTF-8''" + rfc5987Encode(filename);
	}

	static String rfc5987Encode(String value) {
		StringBuilder encoded = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xFF;
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| "!#$&+-.^_`|~".indexOf(c) >= 0) {
				encoded.append((char) c);
			} else {
				encoded.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
						.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
			}
		}
		return encoded.toString();
	}
}
