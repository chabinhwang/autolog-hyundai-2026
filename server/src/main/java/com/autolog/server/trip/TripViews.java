package com.autolog.server.trip;

import com.autolog.server.masterdata.DriverRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 관리자에게 나가는 운행 응답. 전부 {@link TripViewMapper}가 마스킹을 적용해 만든다. */
public final class TripViews {

	private TripViews() {
	}

	public record VehicleRef(Long id, String regNo) {
	}

	public record EndpointView(EndpointType type, String label) {
	}

	/** 운행 목록 항목 (GET /trips) */
	public record TripListItem(UUID clientUuid, LocalDate tripDate, VehicleRef vehicle, DriverRef driver,
			OffsetDateTime startedAt, OffsetDateTime endedAt, BigDecimal odoStartKm, BigDecimal odoEndKm,
			BigDecimal distanceKm, EndpointView origin, EndpointView dest, Purpose purpose,
			PurposeSource purposeSource, BusinessCategory businessCategory, String note, boolean edited,
			boolean masked) {
	}

	/** 운행 상세 (GET /trips/{clientUuid}) */
	public record TripDetail(UUID clientUuid, LocalDate tripDate, VehicleRef vehicle, DriverRef driver,
			OffsetDateTime startedAt, OffsetDateTime endedAt, BigDecimal odoStartKm, BigDecimal odoEndKm,
			BigDecimal distanceKm, EndpointView origin, EndpointView dest, Purpose purpose,
			PurposeSource purposeSource, BusinessCategory businessCategory, String note, boolean masked,
			AutoValues auto, List<EditView> edits, OffsetDateTime recordedAt, OffsetDateTime syncedAt,
			String appVersion) {
	}

	public record AutoValues(Purpose purpose) {
	}

	public record EditView(int seq, ActorType actorType, String actorName, List<FieldChange> changes, String reason,
			OffsetDateTime editedAt, OffsetDateTime receivedAt) {
	}

	/** 수정 이력의 필드 변경 1건 */
	public record FieldChange(String field, Object before, Object after) {
	}

	/** 수정 응답 (POST /trips/{clientUuid}/edits) */
	public record EditResult(EditSummary edit, CurrentValues current) {
	}

	public record EditSummary(int seq, ActorType actorType, List<FieldChange> changes) {
	}

	public record CurrentValues(Purpose purpose, PurposeSource purposeSource, BusinessCategory businessCategory,
			String note, Long driverId) {
	}

	/** 수정 이력 1행 + 표시용 수정자 이름 */
	public record EditEntry(TripEditLog log, String actorName) {
	}
}
