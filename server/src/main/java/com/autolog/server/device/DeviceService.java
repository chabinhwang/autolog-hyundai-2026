package com.autolog.server.device;

import com.autolog.server.device.DeviceDtos.ConfigDriver;
import com.autolog.server.device.DeviceDtos.ConfigPlace;
import com.autolog.server.device.DeviceDtos.ConfigResponse;
import com.autolog.server.device.DeviceDtos.ConfigTripUpdate;
import com.autolog.server.device.DeviceDtos.ConfigVehicle;
import com.autolog.server.device.DeviceDtos.ConsentRequest;
import com.autolog.server.device.DeviceDtos.ConsentResponse;
import com.autolog.server.global.config.AppProperties;
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
import com.autolog.server.trip.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 차량 전용 API: 설정 내려받기(rules §5), 직원 동의 기록(rules §11) */
@Service
public class DeviceService {

	/** since 없이 요청할 때 tripUpdates로 보낼 관리자 수정 기간 = 차량 앱의 동기화 완료 트립 보관 기간 */
	static final Duration TRIP_UPDATES_WINDOW = Duration.ofDays(90);

	/** 차량 시각 허용 오차 (D11) */
	public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);

	private final VehicleRepository vehicleRepository;
	private final DriverRepository driverRepository;
	private final PlaceRepository placeRepository;
	private final DriverConsentRepository consentRepository;
	private final TripRepository tripRepository;
	private final AppProperties properties;
	private final Clock clock;

	public DeviceService(VehicleRepository vehicleRepository, DriverRepository driverRepository,
			PlaceRepository placeRepository, DriverConsentRepository consentRepository, TripRepository tripRepository,
			AppProperties properties, Clock clock) {
		this.vehicleRepository = vehicleRepository;
		this.driverRepository = driverRepository;
		this.placeRepository = placeRepository;
		this.consentRepository = consentRepository;
		this.tripRepository = tripRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * since가 없으면 전체(비활성 직원·삭제 장소 포함), 있으면 그 시각 이후 updated_at이 바뀐 직원·장소만.
	 * serverTime은 초 단위로 내림한 값이고, 비교는 이상(>=)으로 해서 같은 초의 변경도 다음 요청에 다시 실린다.
	 * tripUpdates는 이 차량 트립 중 since(없으면 최근 90일) 이후 관리자 수정이 생긴 트립의 현재 용도.
	 */
	@Transactional(readOnly = true)
	public ConfigResponse config(DevicePrincipal device, String sinceParam) {
		Instant since = parseSince(sinceParam);
		Instant serverTime = Times.now(clock);
		Vehicle vehicle = vehicleRepository.findByIdAndOrgId(device.vehicleId(), device.orgId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		List<Driver> drivers = since == null
				? driverRepository.findByOrgIdOrderByIdAsc(device.orgId())
				: driverRepository.findByOrgIdAndUpdatedAtGreaterThanEqualOrderByIdAsc(device.orgId(), since);
		List<Place> places = since == null
				? placeRepository.findByOrgIdOrderByIdAsc(device.orgId())
				: placeRepository.findByOrgIdAndUpdatedAtGreaterThanEqualOrderByIdAsc(device.orgId(), since);
		Instant editsSince = since != null ? since : serverTime.minus(TRIP_UPDATES_WINDOW);
		List<ConfigTripUpdate> tripUpdates = tripRepository
				.findAdminEditedSince(device.orgId(), device.vehicleId(), editsSince).stream()
				.map(t -> new ConfigTripUpdate(t.getClientUuid(), t.getPurpose(), t.getPurposeSource(),
						t.getBusinessCategory()))
				.toList();
		return new ConfigResponse(Times.toSeoul(serverTime), properties.consentVersion(),
				new ConfigVehicle(vehicle.getId(), vehicle.getRegNo(), vehicle.getModelName(),
						vehicle.getDefaultDriver() == null ? null : vehicle.getDefaultDriver().getId()),
				drivers.stream().map(d -> new ConfigDriver(d.getId(), d.getDept(), d.getName(), d.isActive())).toList(),
				places.stream().map(p -> new ConfigPlace(p.getId(), p.getName(), p.getType(), p.getLat().doubleValue(),
						p.getLng().doubleValue(), p.getRadiusM(), p.isDeleted())).toList(),
				tripUpdates);
	}

	/** 처음 기록이면 created=true(201), 같은 직원·버전이 이미 있으면 기존 기록(200) */
	@Transactional
	public ConsentResult recordConsent(DevicePrincipal device, ConsentRequest request) {
		Instant now = Times.now(clock);
		Instant consentedAt = request.consentedAt().toInstant();
		if (consentedAt.isAfter(clock.instant().plus(FUTURE_TOLERANCE))) {
			throw BusinessException.validation("consentedAt", "현재 시각 이후일 수 없습니다.");
		}
		Driver driver = driverRepository.findByIdAndOrgIdAndActiveTrue(request.driverId(), device.orgId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_NOT_FOUND));
		String version = request.consentVersion().trim();
		Optional<DriverConsent> existing = consentRepository.findByDriverIdAndConsentVersion(driver.getId(), version);
		if (existing.isPresent()) {
			return new ConsentResult(toResponse(existing.get()), false);
		}
		DriverConsent saved = consentRepository.save(new DriverConsent(device.orgId(), driver.getId(), version,
				consentedAt, device.deviceId(), now));
		return new ConsentResult(toResponse(saved), true);
	}

	private static ConsentResponse toResponse(DriverConsent consent) {
		return new ConsentResponse(consent.getDriverId(), consent.getConsentVersion(),
				Times.toSeoul(consent.getConsentedAt()));
	}

	private static Instant parseSince(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		// 쿼리에서 '+'를 인코딩하지 않으면 공백으로 들어오므로 되돌린다.
		String normalized = value.trim().replace(' ', '+');
		try {
			return OffsetDateTime.parse(normalized).toInstant();
		} catch (DateTimeParseException e) {
			throw BusinessException.validation("since", "ISO-8601 형식이어야 합니다.");
		}
	}

	public record ConsentResult(ConsentResponse response, boolean created) {
	}
}
