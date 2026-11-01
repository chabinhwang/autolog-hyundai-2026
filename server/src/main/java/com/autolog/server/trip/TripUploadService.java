package com.autolog.server.trip;

import com.autolog.server.device.Device;
import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.security.DevicePrincipal;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.Driver;
import com.autolog.server.masterdata.DriverRepository;
import com.autolog.server.masterdata.Place;
import com.autolog.server.masterdata.PlaceRepository;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.masterdata.VehicleRepository;
import com.autolog.server.trip.TripDtos.EndpointInput;
import com.autolog.server.trip.TripDtos.TripBatchRequest;
import com.autolog.server.trip.TripDtos.TripBatchResponse;
import com.autolog.server.trip.TripDtos.TripUploadItem;
import com.autolog.server.trip.TripDtos.UploadResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트립 일괄 업로드 (rules §3). 건별로 CREATED / DUPLICATE / REJECTED(errorCode)를 돌려주고,
 * 일부가 거절돼도 요청은 성공(200)이다. 회사·차량은 기기 토큰에서 정하고 요청 본문의 값은 믿지 않는다.
 */
@Service
public class TripUploadService {

	/** 서버 수신 시각보다 이만큼 넘게 미래면 거절 (D11) */
	public static final Duration FUTURE_LIMIT = Duration.ofMinutes(10);
	/** 서버 수신 시각보다 이만큼 넘게 과거면 거절 (D11) */
	public static final Duration PAST_LIMIT = Duration.ofDays(366);

	private static final BigDecimal MAX_ODO_KM = new BigDecimal("99999999.9");
	private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("9999999.9");
	private static final int MAX_LABEL_LENGTH = 100;
	private static final int MAX_NOTE_LENGTH = 200;

	private final TripRepository tripRepository;
	private final DeviceRepository deviceRepository;
	private final VehicleRepository vehicleRepository;
	private final DriverRepository driverRepository;
	private final PlaceRepository placeRepository;
	private final Clock clock;

	public TripUploadService(TripRepository tripRepository, DeviceRepository deviceRepository,
			VehicleRepository vehicleRepository, DriverRepository driverRepository, PlaceRepository placeRepository,
			Clock clock) {
		this.tripRepository = tripRepository;
		this.deviceRepository = deviceRepository;
		this.vehicleRepository = vehicleRepository;
		this.driverRepository = driverRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional
	public TripBatchResponse upload(DevicePrincipal principal, TripBatchRequest request) {
		Instant receivedAt = clock.instant();
		Instant syncedAt = Times.now(clock);
		Device device = deviceRepository.findById(principal.deviceId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_TOKEN_INVALID));
		Vehicle vehicle = vehicleRepository.getReferenceById(principal.vehicleId());
		String appVersion = blankToNull(request.appVersion());

		Set<UUID> requested = new HashSet<>();
		for (TripUploadItem item : request.trips()) {
			if (item != null) {
				parseUuid(item.clientUuid()).ifPresent(requested::add);
			}
		}
		Set<UUID> known = new HashSet<>(requested.isEmpty() ? Set.of() : tripRepository.findExistingClientUuids(requested));
		Lookups lookups = new Lookups(principal.orgId());

		List<UploadResult> results = new ArrayList<>();
		for (TripUploadItem item : request.trips()) {
			results.add(uploadOne(item, principal, device, vehicle, appVersion, receivedAt, syncedAt, known, lookups));
		}
		tripRepository.flush();
		device.markSynced(syncedAt);
		return new TripBatchResponse(results);
	}

	private UploadResult uploadOne(TripUploadItem item, DevicePrincipal principal, Device device, Vehicle vehicle,
			String appVersion, Instant receivedAt, Instant syncedAt, Set<UUID> known, Lookups lookups) {
		if (item == null) {
			return UploadResult.rejected(null, ErrorCode.VALIDATION_FAILED.name());
		}
		Optional<UUID> uuid = parseUuid(item.clientUuid()).filter(value -> value.version() == 4);
		if (uuid.isEmpty()) {
			return UploadResult.rejected(item.clientUuid(), ErrorCode.VALIDATION_FAILED.name());
		}
		UUID clientUuid = uuid.get();
		if (known.contains(clientUuid)) {
			// 이미 있는 clientUuid는 내용 비교 없이 DUPLICATE (저장된 값 유지)
			return UploadResult.duplicate(clientUuid);
		}
		Parsed parsed = parse(item);
		if (parsed == null) {
			return UploadResult.rejected(clientUuid.toString(), ErrorCode.VALIDATION_FAILED.name());
		}
		if (!principal.vehicleId().equals(item.vehicleId())) {
			return UploadResult.rejected(clientUuid.toString(), "VEHICLE_MISMATCH");
		}
		Instant futureLimit = receivedAt.plus(FUTURE_LIMIT);
		if (parsed.startedAt().isAfter(futureLimit) || parsed.endedAt().isAfter(futureLimit)
				|| parsed.recordedAt().isAfter(futureLimit) || parsed.startedAt().isBefore(receivedAt.minus(PAST_LIMIT))) {
			return UploadResult.rejected(clientUuid.toString(), "TIMESTAMP_OUT_OF_RANGE");
		}
		Driver driver = null;
		if (item.driverId() != null) {
			Optional<Driver> found = lookups.activeDriver(item.driverId());
			if (found.isEmpty()) {
				return UploadResult.rejected(clientUuid.toString(), ErrorCode.DRIVER_NOT_FOUND.name());
			}
			driver = found.get();
		}
		Optional<Trip.Endpoint> origin = resolveEndpoint(parsed.originType(), item.origin(), lookups);
		Optional<Trip.Endpoint> dest = resolveEndpoint(parsed.destType(), item.dest(), lookups);
		if (origin.isEmpty() || dest.isEmpty()) {
			return UploadResult.rejected(clientUuid.toString(), ErrorCode.PLACE_NOT_FOUND.name());
		}
		tripRepository.save(Trip.create(clientUuid, principal.orgId(), vehicle, device.getId(), driver,
				parsed.startedAt(), parsed.endedAt(), parsed.odoStartKm(), parsed.odoEndKm(), origin.get(), dest.get(),
				parsed.autoPurpose(), parsed.purpose(), parsed.purposeSource(), parsed.businessCategory(),
				parsed.note(), parsed.recordedAt(), syncedAt, appVersion));
		known.add(clientUuid);
		return UploadResult.created(clientUuid);
	}

	/** 필드 규칙 검증. 하나라도 어기면 null (→ VALIDATION_FAILED) */
	private static Parsed parse(TripUploadItem item) {
		if (item.vehicleId() == null) {
			return null;
		}
		Instant startedAt = parseInstant(item.startedAt());
		Instant endedAt = parseInstant(item.endedAt());
		Instant recordedAt = parseInstant(item.recordedAt());
		if (startedAt == null || endedAt == null || recordedAt == null || !endedAt.isAfter(startedAt)) {
			return null;
		}
		if (item.odoStartKm() == null || item.odoEndKm() == null) {
			return null;
		}
		// Odometer는 km Float로 오므로 소수 첫째 자리로 반올림해 저장한다.
		BigDecimal odoStart = item.odoStartKm().setScale(1, RoundingMode.HALF_UP);
		BigDecimal odoEnd = item.odoEndKm().setScale(1, RoundingMode.HALF_UP);
		if (odoStart.signum() < 0 || odoEnd.compareTo(odoStart) < 0 || odoEnd.compareTo(MAX_ODO_KM) > 0
				|| odoEnd.subtract(odoStart).compareTo(MAX_DISTANCE_KM) > 0) {
			return null;
		}
		EndpointType originType = endpointType(item.origin());
		EndpointType destType = endpointType(item.dest());
		if (originType == null || destType == null) {
			return null;
		}
		Purpose autoPurpose = parseEnum(Purpose.class, item.autoPurpose());
		Purpose purpose = parseEnum(Purpose.class, item.purpose());
		PurposeSource purposeSource = parseEnum(PurposeSource.class, item.purposeSource());
		if (autoPurpose == null || purpose == null || purposeSource == null || purposeSource == PurposeSource.ADMIN) {
			return null;
		}
		if (purpose == Purpose.UNCONFIRMED && purposeSource != PurposeSource.AUTO) {
			return null;
		}
		BusinessCategory businessCategory = null;
		if (item.businessCategory() != null) {
			businessCategory = parseEnum(BusinessCategory.class, item.businessCategory());
			if (businessCategory == null || purpose != Purpose.BUSINESS) {
				return null;
			}
		}
		String note = blankToNull(item.note());
		if (note != null && note.length() > MAX_NOTE_LENGTH) {
			return null;
		}
		return new Parsed(startedAt, endedAt, recordedAt, odoStart, odoEnd, originType, destType, autoPurpose,
				purpose, purposeSource, businessCategory, note);
	}

	/** 출발·도착 유형별 placeId·label 규칙. 형식이 틀리면 null */
	private static EndpointType endpointType(EndpointInput input) {
		if (input == null) {
			return null;
		}
		EndpointType type = parseEnum(EndpointType.class, input.type());
		if (type == null) {
			return null;
		}
		if (type.requiresPlace() != (input.placeId() != null)) {
			// COMPANY·CLIENT는 placeId 필수, HOME·UNREGISTERED는 null
			return null;
		}
		if (type == EndpointType.UNREGISTERED && input.label() != null && input.label().trim().length() > MAX_LABEL_LENGTH) {
			return null;
		}
		return type;
	}

	/**
	 * 서버 장소를 확인하고 라벨을 정한다. COMPANY·CLIENT는 장소 이름(요청 라벨 무시), HOME은 '집',
	 * UNREGISTERED는 요청 라벨(내비 목적지명) 또는 null. 같은 회사 장소가 아니면 빈 값 (→ PLACE_NOT_FOUND)
	 */
	private static Optional<Trip.Endpoint> resolveEndpoint(EndpointType type, EndpointInput input, Lookups lookups) {
		return switch (type) {
			case HOME -> Optional.of(new Trip.Endpoint(type, null, TripViewMapper.HOME_LABEL));
			case UNREGISTERED -> Optional.of(new Trip.Endpoint(type, null, blankToNull(input.label())));
			case COMPANY, CLIENT -> lookups.place(input.placeId())
					.map(place -> new Trip.Endpoint(type, place.getId(), place.getName()));
		};
	}

	private static Instant parseInstant(String value) {
		if (value == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Optional<UUID> parseUuid(String value) {
		if (value == null || value.length() != 36) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record Parsed(Instant startedAt, Instant endedAt, Instant recordedAt, BigDecimal odoStartKm,
			BigDecimal odoEndKm, EndpointType originType, EndpointType destType, Purpose autoPurpose, Purpose purpose,
			PurposeSource purposeSource, BusinessCategory businessCategory, String note) {
	}

	/** 한 요청 안에서 직원·장소 조회를 한 번씩만 하도록 캐시 */
	private final class Lookups {

		private final Long orgId;
		private final Map<Long, Optional<Driver>> drivers = new HashMap<>();
		private final Map<Long, Optional<Place>> places = new HashMap<>();

		Lookups(Long orgId) {
			this.orgId = orgId;
		}

		Optional<Driver> activeDriver(Long id) {
			return drivers.computeIfAbsent(id, key -> driverRepository.findByIdAndOrgIdAndActiveTrue(key, orgId));
		}

		/** 삭제된 장소도 같은 회사 장소면 받는다 (오프라인 중 삭제된 경우) */
		Optional<Place> place(Long id) {
			return places.computeIfAbsent(Objects.requireNonNull(id),
					key -> placeRepository.findByIdAndOrgId(key, orgId));
		}
	}
}
