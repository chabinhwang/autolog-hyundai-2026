package com.autolog.server.trip;

import com.autolog.server.auth.AdminUser;
import com.autolog.server.auth.AdminUserRepository;
import com.autolog.server.device.Device;
import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.response.PageResponse;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.masterdata.VehicleRepository;
import com.autolog.server.trip.TripViews.EditEntry;
import com.autolog.server.trip.TripViews.TripDetail;
import com.autolog.server.trip.TripViews.TripListItem;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 운행 조회. 응답은 전부 TripViewMapper(마스킹)를 거친다. */
@Service
public class TripQueryService {

	/** 조회 기간 최대 일수 (날짜 포함) */
	public static final int MAX_RANGE_DAYS = 366;
	public static final int MAX_PAGE_SIZE = 100;

	private final TripRepository tripRepository;
	private final TripEditLogRepository editLogRepository;
	private final DeviceRepository deviceRepository;
	private final VehicleRepository vehicleRepository;
	private final AdminUserRepository adminUserRepository;
	private final TripViewMapper mapper;

	public TripQueryService(TripRepository tripRepository, TripEditLogRepository editLogRepository,
			DeviceRepository deviceRepository, VehicleRepository vehicleRepository,
			AdminUserRepository adminUserRepository, TripViewMapper mapper) {
		this.tripRepository = tripRepository;
		this.editLogRepository = editLogRepository;
		this.deviceRepository = deviceRepository;
		this.vehicleRepository = vehicleRepository;
		this.adminUserRepository = adminUserRepository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public PageResponse<TripListItem> list(Long orgId, TripSearch search) {
		validateRange(search.from(), search.to());
		if (search.page() < 0) {
			throw BusinessException.validation("page", "0 이상이어야 합니다.");
		}
		if (search.size() < 1 || search.size() > MAX_PAGE_SIZE) {
			throw BusinessException.validation("size", "1~100이어야 합니다.");
		}
		Purpose purpose = null;
		if (search.purpose() != null && !search.purpose().isBlank()) {
			try {
				purpose = Purpose.valueOf(search.purpose());
			} catch (IllegalArgumentException e) {
				throw BusinessException.validation("purpose", "COMMUTE·BUSINESS·PERSONAL·UNCONFIRMED 중 하나여야 합니다.");
			}
		}
		Specification<Trip> spec = byOrg(orgId).and(between(search.from(), search.to()));
		if (search.vehicleId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("vehicle").get("id"), search.vehicleId()));
		}
		if (search.driverId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("driver").get("id"), search.driverId()));
		}
		if (purpose != null) {
			Purpose filter = purpose;
			spec = spec.and((root, query, cb) -> cb.equal(root.get("purpose"), filter));
		}
		Page<Trip> page = tripRepository.findAll(spec, PageRequest.of(search.page(), search.size(),
				Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
		Set<Long> edited = page.isEmpty() ? Set.of()
				: editLogRepository.findEditedTripIds(page.getContent().stream().map(Trip::getId).toList());
		return PageResponse.of(page, trips -> trips.stream()
				.map(trip -> mapper.toListItem(trip, edited.contains(trip.getId())))
				.toList());
	}

	@Transactional(readOnly = true)
	public TripDetail detail(Long orgId, String clientUuid) {
		Trip trip = parseUuid(clientUuid).flatMap(uuid -> tripRepository.findByClientUuidAndOrgId(uuid, orgId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
		List<TripEditLog> logs = editLogRepository.findByTripIdOrderBySeqAsc(trip.getId());
		Map<Long, String> deviceNames = new HashMap<>();
		Map<Long, String> adminNames = new HashMap<>();
		List<EditEntry> entries = logs.stream()
				.map(log -> new EditEntry(log, log.getActorType() == ActorType.DEVICE
						? deviceNames.computeIfAbsent(log.getActorId(), this::deviceName)
						: adminNames.computeIfAbsent(log.getActorId(), this::adminName)))
				.toList();
		return mapper.toDetail(trip, entries);
	}

	/** 기록부·목록 공용 기간 검증: to ≥ from, 날짜 포함 최대 366일 */
	public static void validateRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
			throw BusinessException.validation("to", "from 이후 366일 이내여야 합니다.");
		}
	}

	/** 차량 수정은 '12가3456 차량'처럼 차량 번호로 표시 */
	private String deviceName(Long deviceId) {
		return deviceRepository.findById(deviceId)
				.map(Device::getVehicleId)
				.flatMap(vehicleRepository::findById)
				.map(Vehicle::getRegNo)
				.map(regNo -> regNo + " 차량")
				.orElse("차량");
	}

	private String adminName(Long adminUserId) {
		return adminUserRepository.findById(adminUserId).map(AdminUser::getDisplayName).orElse("관리자");
	}

	private static Specification<Trip> byOrg(Long orgId) {
		return (root, query, cb) -> cb.equal(root.get("orgId"), orgId);
	}

	private static Specification<Trip> between(LocalDate from, LocalDate to) {
		return (root, query, cb) -> cb.between(root.get("tripDate"), from, to);
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

	public record TripSearch(LocalDate from, LocalDate to, Long vehicleId, Long driverId, String purpose, int page,
			int size) {
	}
}
