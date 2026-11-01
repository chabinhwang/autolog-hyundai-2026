package com.autolog.server.trip;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 운행 API 요청 (docs/contracts/api/trips.md).
 * 업로드 항목의 UUID·열거값·시각은 문자열로 받아 건별로 검증한다 (형식 오류도 요청 전체 400이 아니라 건별 REJECTED).
 */
public final class TripDtos {

	private TripDtos() {
	}

	public record TripBatchRequest(
			@Size(max = 20, message = "20자 이하여야 합니다.") String appVersion,
			@NotNull(message = "1~50건이어야 합니다.")
			@Size(min = 1, max = 50, message = "1~50건이어야 합니다.") List<TripUploadItem> trips) {
	}

	public record TripUploadItem(String clientUuid, Long vehicleId, String startedAt, String endedAt,
			BigDecimal odoStartKm, BigDecimal odoEndKm, EndpointInput origin, EndpointInput dest, String autoPurpose,
			String purpose, String purposeSource, String businessCategory, String note, Long driverId,
			String recordedAt) {

		@Override
		public String toString() {
			// 라벨·메모가 로그에 섞이지 않도록 식별자만 남긴다.
			return "TripUploadItem[clientUuid=" + clientUuid + "]";
		}
	}

	public record EndpointInput(String type, Long placeId, String label) {

		@Override
		public String toString() {
			return "EndpointInput[type=" + type + "]";
		}
	}

	public record TripBatchResponse(List<UploadResult> results) {
	}

	public record UploadResult(String clientUuid, UploadResultType result, String errorCode) {

		static UploadResult created(UUID uuid) {
			return new UploadResult(uuid.toString(), UploadResultType.CREATED, null);
		}

		static UploadResult duplicate(UUID uuid) {
			return new UploadResult(uuid.toString(), UploadResultType.DUPLICATE, null);
		}

		static UploadResult rejected(String clientUuid, String errorCode) {
			return new UploadResult(clientUuid, UploadResultType.REJECTED, errorCode);
		}
	}

	public enum UploadResultType {
		CREATED,
		DUPLICATE,
		REJECTED
	}

	/**
	 * 수정 요청. 차량은 clientEditUuid·editedAt·purposeSource, 관리자는 reason을 쓴다.
	 * changes는 키 존재 여부(null로 지우기 vs 보내지 않음)를 구분해야 해서 Map으로 받는다.
	 */
	public record TripEditRequest(String clientEditUuid, String editedAt, String purposeSource, String reason,
			Map<String, Object> changes) {

		@Override
		public String toString() {
			return "TripEditRequest[clientEditUuid=" + clientEditUuid + "]";
		}
	}
}
