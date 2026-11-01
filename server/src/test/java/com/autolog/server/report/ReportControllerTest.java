package com.autolog.server.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportControllerTest {

	@Test
	void contentDispositionUsesRfc5987FilenameLikeContractExample() {
		assertThat(ReportController.contentDisposition("운행기록부_12가3456_2026-01-01_2026-12-31.xlsx"))
				.isEqualTo("attachment; filename=\"logbook.xlsx\"; filename*=UTF-8''"
						+ "%EC%9A%B4%ED%96%89%EA%B8%B0%EB%A1%9D%EB%B6%80_12%EA%B0%803456_2026-01-01_2026-12-31.xlsx");
	}

	@Test
	void spacesAndQuotesArePercentEncoded() {
		assertThat(ReportController.rfc5987Encode("a b\"c")).isEqualTo("a%20b%22c");
	}
}
