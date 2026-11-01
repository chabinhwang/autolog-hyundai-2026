package com.autolog.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 직원 동의 기록 (D13) */
@Entity
@Table(name = "driver_consent")
public class DriverConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(name = "driver_id", nullable = false)
	private Long driverId;

	@Column(name = "consent_version", nullable = false, length = 20)
	private String consentVersion;

	@Column(name = "consented_at", nullable = false)
	private Instant consentedAt;

	@Column(name = "device_id", nullable = false)
	private Long deviceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DriverConsent() {
	}

	public DriverConsent(Long orgId, Long driverId, String consentVersion, Instant consentedAt, Long deviceId,
			Instant createdAt) {
		this.orgId = orgId;
		this.driverId = driverId;
		this.consentVersion = consentVersion;
		this.consentedAt = consentedAt;
		this.deviceId = deviceId;
		this.createdAt = createdAt;
	}

	public Long getDriverId() {
		return driverId;
	}

	public String getConsentVersion() {
		return consentVersion;
	}

	public Instant getConsentedAt() {
		return consentedAt;
	}
}
