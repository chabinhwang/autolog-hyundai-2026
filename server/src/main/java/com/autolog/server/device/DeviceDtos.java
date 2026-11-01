package com.autolog.server.device;

import com.autolog.server.masterdata.PlaceType;
import com.autolog.server.trip.BusinessCategory;
import com.autolog.server.trip.Purpose;
import com.autolog.server.trip.PurposeSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 페어링·기기 API 요청·응답 (docs/contracts/api/devices.md) */
public final class DeviceDtos {

	private DeviceDtos() {
	}

	public record PairingCodeRequest(
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 64, message = "1~64자여야 합니다.") String installationId,
			@NotNull(message = "비어 있을 수 없습니다.")
			@Pattern(regexp = "[0-9a-f]{64}", message = "64자리 16진수여야 합니다.") String tokenHash) {
	}

	public record PairingCodeResponse(UUID pairingId, String code, OffsetDateTime expiresAt) {
	}

	public record PairingStatusResponse(PairingStatus status, OffsetDateTime expiresAt, PairedVehicle vehicle) {
	}

	public record PairedVehicle(String regNo, String modelName) {
	}

	public record PairRequest(
			@NotNull(message = "비어 있을 수 없습니다.")
			@Pattern(regexp = "\\d{6}", message = "숫자 6자리여야 합니다.") String code,
			@NotNull(message = "비어 있을 수 없습니다.") Long vehicleId) {
	}

	public record PairResponse(Long deviceId, Long vehicleId, OffsetDateTime pairedAt, Long replacedDeviceId) {
	}

	public record UnpairResponse(Long vehicleId, Long deviceId, OffsetDateTime revokedAt) {
	}

	public record ConfigResponse(OffsetDateTime serverTime, String consentVersion, ConfigVehicle vehicle,
			List<ConfigDriver> drivers, List<ConfigPlace> places, List<ConfigTripUpdate> tripUpdates) {
	}

	/** 관리자가 수정한 이 차량 트립의 현재 용도 (rules §5 tripUpdates) */
	public record ConfigTripUpdate(UUID clientUuid, Purpose purpose, PurposeSource purposeSource,
			BusinessCategory businessCategory) {
	}

	public record ConfigVehicle(Long id, String regNo, String modelName, Long defaultDriverId) {
	}

	public record ConfigDriver(Long id, String dept, String name, boolean active) {
	}

	public record ConfigPlace(Long id, String name, PlaceType type, double lat, double lng, int radiusM,
			boolean deleted) {
	}

	public record ConsentRequest(
			@NotNull(message = "비어 있을 수 없습니다.") Long driverId,
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 20, message = "1~20자여야 합니다.") String consentVersion,
			@NotNull(message = "비어 있을 수 없습니다.") OffsetDateTime consentedAt) {
	}

	public record ConsentResponse(Long driverId, String consentVersion, OffsetDateTime consentedAt) {
	}
}
