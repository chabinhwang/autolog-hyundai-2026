package com.autolog.server.report;

import com.autolog.server.masterdata.DriverRef;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 대시보드 집계 응답 (docs/contracts/api/reports.md) */
public final class ReportDtos {

	private ReportDtos() {
	}

	public record SummaryResponse(String month, List<VehicleSummary> vehicles, Totals totals) {
	}

	public record VehicleSummary(Long vehicleId, String regNo, String modelName, DriverRef defaultDriver,
			boolean paired, OffsetDateTime lastSyncedAt, int tripCount, BigDecimal totalKm, BigDecimal commuteKm,
			BigDecimal businessKm, BigDecimal personalKm, BigDecimal unconfirmedKm, int unconfirmedCount,
			BigDecimal businessRatio, BigDecimal maxBusinessRatio, List<Warning> warnings) {
	}

	public record Totals(BigDecimal totalKm, BigDecimal commuteKm, BigDecimal businessKm, BigDecimal personalKm,
			BigDecimal unconfirmedKm, int unconfirmedCount, BigDecimal businessRatio) {
	}

	/** 경고 (D9) */
	public enum Warning {
		/** 업무비율 95% 이상 */
		RATIO_HIGH,
		/** 미확인 1건 이상 */
		UNCONFIRMED_EXISTS,
		/** 마지막 동기화 48시간 초과 (페어링된 차량만) */
		SYNC_DELAYED,
		/** 직전 트립 주행 후 거리와 다음 트립 주행 전 거리 차이 1km 초과 */
		ODO_GAP
	}
}
