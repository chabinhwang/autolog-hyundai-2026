package com.autolog.server.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 기준정보 API 요청·응답 (docs/contracts/api/master-data.md) */
public final class MasterDataDtos {

	private MasterDataDtos() {
	}

	// ----- 차량 -----

	public record VehicleView(Long id, String regNo, String modelName, DriverRef defaultDriver, boolean active,
			DeviceStatus device) {
	}

	public record DeviceStatus(boolean paired, Long deviceId, OffsetDateTime pairedAt, OffsetDateTime lastSyncedAt) {
	}

	public record VehicleCreateRequest(
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 20, message = "1~20자여야 합니다.") String regNo,
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 50, message = "1~50자여야 합니다.") String modelName,
			Long defaultDriverId) {
	}

	/** 보낸 필드만 바꾼다. null과 '보내지 않음'을 구분하기 위해 setter 호출 여부를 기록한다. */
	public static class VehiclePatchRequest {

		@Size(max = 20, message = "1~20자여야 합니다.")
		private String regNo;
		private boolean regNoPresent;
		@Size(max = 50, message = "1~50자여야 합니다.")
		private String modelName;
		private boolean modelNamePresent;
		private Long defaultDriverId;
		private boolean defaultDriverIdPresent;
		private Boolean active;
		private boolean activePresent;

		public String getRegNo() {
			return regNo;
		}

		public void setRegNo(String regNo) {
			this.regNo = regNo;
			this.regNoPresent = true;
		}

		public boolean hasRegNo() {
			return regNoPresent;
		}

		public String getModelName() {
			return modelName;
		}

		public void setModelName(String modelName) {
			this.modelName = modelName;
			this.modelNamePresent = true;
		}

		public boolean hasModelName() {
			return modelNamePresent;
		}

		public Long getDefaultDriverId() {
			return defaultDriverId;
		}

		public void setDefaultDriverId(Long defaultDriverId) {
			this.defaultDriverId = defaultDriverId;
			this.defaultDriverIdPresent = true;
		}

		public boolean hasDefaultDriverId() {
			return defaultDriverIdPresent;
		}

		public Boolean getActive() {
			return active;
		}

		public void setActive(Boolean active) {
			this.active = active;
			this.activePresent = true;
		}

		public boolean hasActive() {
			return activePresent;
		}
	}

	// ----- 직원 -----

	public record DriverView(Long id, String dept, String name, boolean active) {

		public static DriverView of(Driver driver) {
			return new DriverView(driver.getId(), driver.getDept(), driver.getName(), driver.isActive());
		}
	}

	public record DriverCreateRequest(
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 50, message = "1~50자여야 합니다.") String dept,
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 30, message = "1~30자여야 합니다.") String name) {
	}

	public static class DriverPatchRequest {

		@Size(max = 50, message = "1~50자여야 합니다.")
		private String dept;
		private boolean deptPresent;
		@Size(max = 30, message = "1~30자여야 합니다.")
		private String name;
		private boolean namePresent;
		private Boolean active;
		private boolean activePresent;

		public String getDept() {
			return dept;
		}

		public void setDept(String dept) {
			this.dept = dept;
			this.deptPresent = true;
		}

		public boolean hasDept() {
			return deptPresent;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
			this.namePresent = true;
		}

		public boolean hasName() {
			return namePresent;
		}

		public Boolean getActive() {
			return active;
		}

		public void setActive(Boolean active) {
			this.active = active;
			this.activePresent = true;
		}

		public boolean hasActive() {
			return activePresent;
		}
	}

	// ----- 장소 -----

	public record PlaceView(Long id, String name, PlaceType type, double lat, double lng, int radiusM,
			OffsetDateTime updatedAt) {
	}

	public record PlaceDeletedView(Long id, boolean deleted) {
	}

	public record PlaceCreateRequest(
			@NotBlank(message = "비어 있을 수 없습니다.") @Size(max = 100, message = "1~100자여야 합니다.") String name,
			@NotNull(message = "비어 있을 수 없습니다.")
			@Pattern(regexp = "COMPANY|CLIENT", message = "COMPANY 또는 CLIENT여야 합니다.") String type,
			@NotNull(message = "비어 있을 수 없습니다.")
			@DecimalMin(value = "-90", message = "-90 이상이어야 합니다.")
			@DecimalMax(value = "90", message = "90 이하여야 합니다.") BigDecimal lat,
			@NotNull(message = "비어 있을 수 없습니다.")
			@DecimalMin(value = "-180", message = "-180 이상이어야 합니다.")
			@DecimalMax(value = "180", message = "180 이하여야 합니다.") BigDecimal lng,
			@Min(value = 50, message = "50 이상이어야 합니다.")
			@Max(value = 2000, message = "2000 이하여야 합니다.") Integer radiusM) {
	}

	public static class PlacePatchRequest {

		@Size(max = 100, message = "1~100자여야 합니다.")
		private String name;
		private boolean namePresent;
		@Pattern(regexp = "COMPANY|CLIENT", message = "COMPANY 또는 CLIENT여야 합니다.")
		private String type;
		private boolean typePresent;
		@DecimalMin(value = "-90", message = "-90 이상이어야 합니다.")
		@DecimalMax(value = "90", message = "90 이하여야 합니다.")
		private BigDecimal lat;
		private boolean latPresent;
		@DecimalMin(value = "-180", message = "-180 이상이어야 합니다.")
		@DecimalMax(value = "180", message = "180 이하여야 합니다.")
		private BigDecimal lng;
		private boolean lngPresent;
		@Min(value = 50, message = "50 이상이어야 합니다.")
		@Max(value = 2000, message = "2000 이하여야 합니다.")
		private Integer radiusM;
		private boolean radiusMPresent;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
			this.namePresent = true;
		}

		public boolean hasName() {
			return namePresent;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
			this.typePresent = true;
		}

		public boolean hasType() {
			return typePresent;
		}

		public BigDecimal getLat() {
			return lat;
		}

		public void setLat(BigDecimal lat) {
			this.lat = lat;
			this.latPresent = true;
		}

		public boolean hasLat() {
			return latPresent;
		}

		public BigDecimal getLng() {
			return lng;
		}

		public void setLng(BigDecimal lng) {
			this.lng = lng;
			this.lngPresent = true;
		}

		public boolean hasLng() {
			return lngPresent;
		}

		public Integer getRadiusM() {
			return radiusM;
		}

		public void setRadiusM(Integer radiusM) {
			this.radiusM = radiusM;
			this.radiusMPresent = true;
		}

		public boolean hasRadiusM() {
			return radiusMPresent;
		}
	}
}
