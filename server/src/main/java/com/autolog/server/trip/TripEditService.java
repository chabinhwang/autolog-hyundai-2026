package com.autolog.server.trip;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.global.security.DevicePrincipal;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.Driver;
import com.autolog.server.masterdata.DriverRepository;
import com.autolog.server.trip.TripDtos.TripEditRequest;
import com.autolog.server.trip.TripViews.EditResult;
import com.autolog.server.trip.TripViews.FieldChange;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운행 수정 (rules §4). 차량·관리자 공용. 덮어쓰지 않고 요청 1건마다 이력 1행을 추가한다.
 * <ul>
 * <li>수정 가능: purpose, businessCategory, note, driverId. 그 외 필드 → AUTO_FIELD_IMMUTABLE (D3)</li>
 * <li>purpose가 BUSINESS가 아니면 businessCategory는 자동 null, BUSINESS로 바꾸면서 없으면 OTHER</li>
 * <li>판정 출처: 차량은 요청의 MANUAL/VOICE, 관리자는 ADMIN. 용도를 바꾸지 않으면 유지</li>
 * <li>차량: 자기 차량 트립만, clientEditUuid로 멱등. 관리자: reason 필수, 개인 운행·PERSONAL로 변경 금지 (D4)</li>
 * </ul>
 */
@Service
public class TripEditService {

	static final String FIELD_PURPOSE = "purpose";
	static final String FIELD_BUSINESS_CATEGORY = "businessCategory";
	static final String FIELD_NOTE = "note";
	static final String FIELD_DRIVER_ID = "driverId";
	private static final Set<String> EDITABLE_FIELDS = Set.of(FIELD_PURPOSE, FIELD_BUSINESS_CATEGORY, FIELD_NOTE,
			FIELD_DRIVER_ID);
	private static final int MAX_NOTE_LENGTH = 200;
	private static final int MAX_REASON_LENGTH = 200;

	private final TripRepository tripRepository;
	private final TripEditLogRepository editLogRepository;
	private final DriverRepository driverRepository;
	private final TripViewMapper mapper;
	private final Clock clock;

	public TripEditService(TripRepository tripRepository, TripEditLogRepository editLogRepository,
			DriverRepository driverRepository, TripViewMapper mapper, Clock clock) {
		this.tripRepository = tripRepository;
		this.editLogRepository = editLogRepository;
		this.driverRepository = driverRepository;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional
	public EditResult editByDevice(DevicePrincipal device, String clientUuid, TripEditRequest request) {
		Changes changes = parseChanges(request);
		UUID clientEditUuid = parseUuid(request.clientEditUuid())
				.orElseThrow(() -> BusinessException.validation("clientEditUuid", "UUID 형식이어야 합니다."));
		Instant editedAt = parseEditedAt(request.editedAt());
		PurposeSource purposeSource = parseDevicePurposeSource(request.purposeSource());

		Trip trip = lockTrip(clientUuid, device.orgId());
		if (!trip.getVehicle().getId().equals(device.vehicleId())) {
			// 다른 차량의 트립은 존재 여부를 드러내지 않는다.
			throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
		}
		// 같은 clientEditUuid 재전송 → 처음 결과 (트립 잠금 뒤에 확인해 동시 재전송도 1건만 남긴다)
		Optional<TripEditLog> previous = editLogRepository.findByClientEditUuid(clientEditUuid);
		if (previous.isPresent()) {
			if (!previous.get().getTripId().equals(trip.getId())) {
				throw BusinessException.validation("clientEditUuid", "다른 운행에 이미 사용된 값입니다.");
			}
			return mapper.toEditResult(trip, previous.get(), false);
		}
		Target target = target(trip, changes, device.orgId());
		if (target.purposeChanged() && purposeSource == null) {
			throw BusinessException.validation("purposeSource", "용도를 바꿀 때는 MANUAL 또는 VOICE여야 합니다.");
		}
		PurposeSource newSource = target.purposeChanged() ? purposeSource : trip.getPurposeSource();
		TripEditLog log = apply(trip, target, newSource, ActorType.DEVICE, device.deviceId(), clientEditUuid, null,
				editedAt);
		return mapper.toEditResult(trip, log, false);
	}

	@Transactional
	public EditResult editByAdmin(AdminPrincipal admin, String clientUuid, TripEditRequest request) {
		Changes changes = parseChanges(request);
		String reason = request.reason() == null ? null : request.reason().trim();
		if (reason == null || reason.isEmpty()) {
			throw new BusinessException(ErrorCode.EDIT_REASON_REQUIRED);
		}
		if (reason.length() > MAX_REASON_LENGTH) {
			throw BusinessException.validation("reason", "1~200자여야 합니다.");
		}
		Trip trip = lockTrip(clientUuid, admin.orgId());
		// D4: 개인 운행은 운전자만 수정. 관리자가 PERSONAL로 바꿔서 숨기는 것도 막는다.
		if (trip.getPurpose() == Purpose.PERSONAL || changes.purpose() == Purpose.PERSONAL) {
			throw new BusinessException(ErrorCode.PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN);
		}
		Target target = target(trip, changes, admin.orgId());
		PurposeSource newSource = target.purposeChanged() ? PurposeSource.ADMIN : trip.getPurposeSource();
		Instant now = Times.now(clock);
		TripEditLog log = apply(trip, target, newSource, ActorType.ADMIN, admin.adminUserId(), null, reason, now);
		return mapper.toEditResult(trip, log, true);
	}

	/** 바뀐 필드만 이력에 남기고 트립 현재값을 바꾼다. 바뀐 게 없으면 이력 없이 null */
	private TripEditLog apply(Trip trip, Target target, PurposeSource newSource, ActorType actorType, Long actorId,
			UUID clientEditUuid, String reason, Instant editedAt) {
		List<FieldChange> diff = new ArrayList<>();
		addIfChanged(diff, FIELD_PURPOSE, name(trip.getPurpose()), name(target.purpose()));
		addIfChanged(diff, FIELD_BUSINESS_CATEGORY, name(trip.getBusinessCategory()), name(target.businessCategory()));
		addIfChanged(diff, FIELD_NOTE, trip.getNote(), target.note());
		addIfChanged(diff, FIELD_DRIVER_ID, trip.getDriver() == null ? null : trip.getDriver().getId(),
				target.driver() == null ? null : target.driver().getId());
		if (diff.isEmpty()) {
			return null;
		}
		Instant now = Times.now(clock);
		int seq = editLogRepository.findMaxSeq(trip.getId()) + 1;
		TripEditLog log = editLogRepository.save(new TripEditLog(trip.getId(), seq, clientEditUuid, actorType, actorId,
				mapper.writeChanges(diff), reason, editedAt, now));
		trip.applyEditable(target.purpose(), newSource, target.businessCategory(), target.note(), target.driver(), now);
		return log;
	}

	/** 요청 변경 + 자동 규칙을 적용한 수정 후 값 */
	private Target target(Trip trip, Changes changes, Long orgId) {
		Purpose purpose = changes.hasPurpose() ? changes.purpose() : trip.getPurpose();
		boolean purposeChanged = purpose != trip.getPurpose();
		BusinessCategory category = changes.hasBusinessCategory() ? changes.businessCategory()
				: trip.getBusinessCategory();
		if (purpose != Purpose.BUSINESS) {
			if (changes.hasBusinessCategory() && changes.businessCategory() != null) {
				throw BusinessException.validation(FIELD_BUSINESS_CATEGORY, "용도가 BUSINESS일 때만 지정할 수 있습니다.");
			}
			category = null;
		} else if (category == null && (purposeChanged || changes.hasBusinessCategory())) {
			category = BusinessCategory.OTHER;
		}
		String note = changes.hasNote() ? changes.note() : trip.getNote();
		Driver driver = trip.getDriver();
		if (changes.hasDriverId()) {
			Long currentId = driver == null ? null : driver.getId();
			if (changes.driverId() == null) {
				driver = null;
			} else if (!changes.driverId().equals(currentId)) {
				driver = driverRepository.findByIdAndOrgIdAndActiveTrue(changes.driverId(), orgId)
						.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_NOT_FOUND));
			}
		}
		return new Target(purpose, purposeChanged, category, note, driver);
	}

	private Trip lockTrip(String clientUuid, Long orgId) {
		return parseUuid(clientUuid).flatMap(uuid -> tripRepository.findForUpdate(uuid, orgId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
	}

	/** changes 검증: 수정 불가 필드 → AUTO_FIELD_IMMUTABLE, 값 형식 오류 → VALIDATION_FAILED */
	static Changes parseChanges(TripEditRequest request) {
		Map<String, Object> raw = request.changes();
		if (raw == null) {
			throw BusinessException.validation("changes", "비어 있을 수 없습니다.");
		}
		for (String key : raw.keySet()) {
			if (!EDITABLE_FIELDS.contains(key)) {
				throw new BusinessException(ErrorCode.AUTO_FIELD_IMMUTABLE,
						key + ": " + ErrorCode.AUTO_FIELD_IMMUTABLE.defaultMessage());
			}
		}
		Purpose purpose = null;
		if (raw.containsKey(FIELD_PURPOSE)) {
			purpose = enumValue(Purpose.class, FIELD_PURPOSE, raw.get(FIELD_PURPOSE));
			if (purpose == null) {
				throw BusinessException.validation(FIELD_PURPOSE, "비어 있을 수 없습니다.");
			}
			if (purpose == Purpose.UNCONFIRMED) {
				throw BusinessException.validation(FIELD_PURPOSE, "UNCONFIRMED로 바꿀 수 없습니다.");
			}
		}
		BusinessCategory category = raw.containsKey(FIELD_BUSINESS_CATEGORY)
				? enumValue(BusinessCategory.class, FIELD_BUSINESS_CATEGORY, raw.get(FIELD_BUSINESS_CATEGORY))
				: null;
		String note = null;
		if (raw.containsKey(FIELD_NOTE)) {
			Object value = raw.get(FIELD_NOTE);
			if (value != null && !(value instanceof String)) {
				throw BusinessException.validation(FIELD_NOTE, "문자열이어야 합니다.");
			}
			note = value == null || ((String) value).isBlank() ? null : ((String) value).trim();
			if (note != null && note.length() > MAX_NOTE_LENGTH) {
				throw BusinessException.validation(FIELD_NOTE, "200자 이하여야 합니다.");
			}
		}
		Long driverId = null;
		if (raw.containsKey(FIELD_DRIVER_ID)) {
			Object value = raw.get(FIELD_DRIVER_ID);
			if (value != null && !(value instanceof Integer || value instanceof Long)) {
				throw BusinessException.validation(FIELD_DRIVER_ID, "정수여야 합니다.");
			}
			driverId = value == null ? null : ((Number) value).longValue();
		}
		return new Changes(raw.containsKey(FIELD_PURPOSE), purpose, raw.containsKey(FIELD_BUSINESS_CATEGORY), category,
				raw.containsKey(FIELD_NOTE), note, raw.containsKey(FIELD_DRIVER_ID), driverId);
	}

	private Instant parseEditedAt(String value) {
		if (value == null) {
			throw BusinessException.validation("editedAt", "비어 있을 수 없습니다.");
		}
		Instant editedAt;
		try {
			editedAt = OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException e) {
			throw BusinessException.validation("editedAt", "ISO-8601 형식이어야 합니다.");
		}
		if (editedAt.isAfter(clock.instant().plus(TripUploadService.FUTURE_LIMIT))) {
			throw BusinessException.validation("editedAt", "현재 시각 이후일 수 없습니다.");
		}
		return editedAt;
	}

	private static PurposeSource parseDevicePurposeSource(String value) {
		if (value == null) {
			return null;
		}
		if (PurposeSource.MANUAL.name().equals(value)) {
			return PurposeSource.MANUAL;
		}
		if (PurposeSource.VOICE.name().equals(value)) {
			return PurposeSource.VOICE;
		}
		throw BusinessException.validation("purposeSource", "MANUAL 또는 VOICE여야 합니다.");
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String field, Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String text) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equals(text)) {
					return constant;
				}
			}
		}
		throw BusinessException.validation(field, "허용되지 않는 값입니다.");
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

	private static void addIfChanged(List<FieldChange> diff, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			diff.add(new FieldChange(field, before, after));
		}
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	record Changes(boolean hasPurpose, Purpose purpose, boolean hasBusinessCategory, BusinessCategory businessCategory,
			boolean hasNote, String note, boolean hasDriverId, Long driverId) {
	}

	private record Target(Purpose purpose, boolean purposeChanged, BusinessCategory businessCategory, String note,
			Driver driver) {
	}
}
