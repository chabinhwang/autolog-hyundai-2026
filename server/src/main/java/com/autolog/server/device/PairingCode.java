package com.autolog.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 페어링 코드 (6자리, 10분 유효, 1회용) */
@Entity
@Table(name = "pairing_code")
public class PairingCode {

	@Id
	private UUID id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 6)
	private String code;

	@Column(name = "installation_id", nullable = false, length = 64)
	private String installationId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PairingStatus status;

	@Column(name = "vehicle_id")
	private Long vehicleId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "paired_at")
	private Instant pairedAt;

	protected PairingCode() {
	}

	public PairingCode(UUID id, String code, String installationId, String tokenHash, Instant createdAt,
			Instant expiresAt) {
		this.id = id;
		this.code = code;
		this.installationId = installationId;
		this.tokenHash = tokenHash;
		this.status = PairingStatus.PENDING;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** 만료 판정은 조회 시점의 expires_at으로 한다. */
	public PairingStatus effectiveStatus(Instant now) {
		if (status == PairingStatus.PENDING && !now.isBefore(expiresAt)) {
			return PairingStatus.EXPIRED;
		}
		return status;
	}

	public void expire() {
		this.status = PairingStatus.EXPIRED;
	}

	public void pair(Long vehicleId, Instant at) {
		this.status = PairingStatus.PAIRED;
		this.vehicleId = vehicleId;
		this.pairedAt = at;
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getInstallationId() {
		return installationId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public PairingStatus getStatus() {
		return status;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
