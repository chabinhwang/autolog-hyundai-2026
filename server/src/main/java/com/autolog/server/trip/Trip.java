package com.autolog.server.trip;

import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.Driver;
import com.autolog.server.masterdata.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 운행. 시각·계기판 거리·주행거리·자동 추정 용도는 저장 후 바꾸지 않는다 (D3).
 * 바꿀 수 있는 값(purpose, purposeSource, businessCategory, note, driver)은 수정 이력과 함께만 바꾼다.
 */
@Entity
@Table(name = "trip")
public class Trip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "client_uuid", nullable = false, updatable = false)
	private UUID clientUuid;

	@Column(name = "org_id", nullable = false, updatable = false)
	private Long orgId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
	private Vehicle vehicle;

	@Column(name = "device_id", nullable = false, updatable = false)
	private Long deviceId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Column(name = "trip_date", nullable = false, updatable = false)
	private LocalDate tripDate;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "ended_at", nullable = false, updatable = false)
	private Instant endedAt;

	@Column(name = "odo_start_km", nullable = false, updatable = false, precision = 9, scale = 1)
	private BigDecimal odoStartKm;

	@Column(name = "odo_end_km", nullable = false, updatable = false, precision = 9, scale = 1)
	private BigDecimal odoEndKm;

	@Column(name = "distance_km", nullable = false, updatable = false, precision = 8, scale = 1)
	private BigDecimal distanceKm;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin_type", nullable = false, updatable = false, length = 12)
	private EndpointType originType;

	@Enumerated(EnumType.STRING)
	@Column(name = "dest_type", nullable = false, updatable = false, length = 12)
	private EndpointType destType;

	@Column(name = "origin_label", updatable = false, length = 100)
	private String originLabel;

	@Column(name = "dest_label", updatable = false, length = 100)
	private String destLabel;

	@Column(name = "origin_place_id", updatable = false)
	private Long originPlaceId;

	@Column(name = "dest_place_id", updatable = false)
	private Long destPlaceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "auto_purpose", nullable = false, updatable = false, length = 12)
	private Purpose autoPurpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Purpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose_source", nullable = false, length = 8)
	private PurposeSource purposeSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "business_category", length = 12)
	private BusinessCategory businessCategory;

	@Column(length = 200)
	private String note;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@Column(name = "synced_at", nullable = false, updatable = false)
	private Instant syncedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "app_version", updatable = false, length = 20)
	private String appVersion;

	protected Trip() {
	}

	/** 업로드 값으로 만든다. 검증은 업로드 서비스가 끝낸 상태여야 한다. */
	public static Trip create(UUID clientUuid, Long orgId, Vehicle vehicle, Long deviceId, Driver driver,
			Instant startedAt, Instant endedAt, BigDecimal odoStartKm, BigDecimal odoEndKm,
			Endpoint origin, Endpoint dest, Purpose autoPurpose, Purpose purpose, PurposeSource purposeSource,
			BusinessCategory businessCategory, String note, Instant recordedAt, Instant syncedAt, String appVersion) {
		Trip trip = new Trip();
		trip.clientUuid = clientUuid;
		trip.orgId = orgId;
		trip.vehicle = vehicle;
		trip.deviceId = deviceId;
		trip.driver = driver;
		trip.tripDate = Times.seoulDate(startedAt);
		trip.startedAt = startedAt;
		trip.endedAt = endedAt;
		trip.odoStartKm = odoStartKm;
		trip.odoEndKm = odoEndKm;
		trip.distanceKm = odoEndKm.subtract(odoStartKm);
		trip.originType = origin.type();
		trip.originLabel = origin.label();
		trip.originPlaceId = origin.placeId();
		trip.destType = dest.type();
		trip.destLabel = dest.label();
		trip.destPlaceId = dest.placeId();
		trip.autoPurpose = autoPurpose;
		trip.purpose = purpose;
		trip.purposeSource = purposeSource;
		trip.businessCategory = businessCategory;
		trip.note = note;
		trip.recordedAt = recordedAt;
		trip.syncedAt = syncedAt;
		trip.updatedAt = syncedAt;
		trip.appVersion = appVersion;
		return trip;
	}

	/** 수정 가능한 값만 바꾼다. 호출 전에 수정 이력을 남겨야 한다. */
	public void applyEditable(Purpose purpose, PurposeSource purposeSource, BusinessCategory businessCategory,
			String note, Driver driver, Instant now) {
		this.purpose = purpose;
		this.purposeSource = purposeSource;
		this.businessCategory = businessCategory;
		this.note = note;
		this.driver = driver;
		this.updatedAt = now;
	}

	/** 출발·도착 (유형, 서버 장소 ID, 라벨) */
	public record Endpoint(EndpointType type, Long placeId, String label) {
	}

	public Long getId() {
		return id;
	}

	public UUID getClientUuid() {
		return clientUuid;
	}

	public Long getOrgId() {
		return orgId;
	}

	public Vehicle getVehicle() {
		return vehicle;
	}

	public Long getDeviceId() {
		return deviceId;
	}

	public Driver getDriver() {
		return driver;
	}

	public LocalDate getTripDate() {
		return tripDate;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BigDecimal getOdoStartKm() {
		return odoStartKm;
	}

	public BigDecimal getOdoEndKm() {
		return odoEndKm;
	}

	public BigDecimal getDistanceKm() {
		return distanceKm;
	}

	public Endpoint getOrigin() {
		return new Endpoint(originType, originPlaceId, originLabel);
	}

	public Endpoint getDest() {
		return new Endpoint(destType, destPlaceId, destLabel);
	}

	public Purpose getAutoPurpose() {
		return autoPurpose;
	}

	public Purpose getPurpose() {
		return purpose;
	}

	public PurposeSource getPurposeSource() {
		return purposeSource;
	}

	public BusinessCategory getBusinessCategory() {
		return businessCategory;
	}

	public String getNote() {
		return note;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public Instant getSyncedAt() {
		return syncedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getAppVersion() {
		return appVersion;
	}
}
