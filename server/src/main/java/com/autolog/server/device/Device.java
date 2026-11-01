package com.autolog.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 차량 앱 기기. 토큰 원문은 저장하지 않고 SHA-256 hex만 둔다. */
@Entity
@Table(name = "device")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Column(name = "installation_id", nullable = false, length = 64)
	private String installationId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "paired_at", nullable = false)
	private Instant pairedAt;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected Device() {
	}

	public Device(Long orgId, Long vehicleId, String installationId, String tokenHash, Instant pairedAt) {
		this.orgId = orgId;
		this.vehicleId = vehicleId;
		this.installationId = installationId;
		this.tokenHash = tokenHash;
		this.pairedAt = pairedAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void revoke(Instant at) {
		this.revokedAt = at;
	}

	public void markSynced(Instant at) {
		this.lastSyncedAt = at;
	}

	public Long getId() {
		return id;
	}

	public Long getOrgId() {
		return orgId;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public String getInstallationId() {
		return installationId;
	}

	public Instant getPairedAt() {
		return pairedAt;
	}

	public Instant getLastSyncedAt() {
		return lastSyncedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}
}
