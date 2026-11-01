package com.autolog.server.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 차량. 삭제 대신 비활성 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(name = "reg_no", nullable = false, length = 20)
	private String regNo;

	@Column(name = "model_name", nullable = false, length = 50)
	private String modelName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_driver_id")
	private Driver defaultDriver;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Vehicle() {
	}

	public Vehicle(Long orgId, String regNo, String modelName, Driver defaultDriver, Instant now) {
		this.orgId = orgId;
		this.regNo = regNo;
		this.modelName = modelName;
		this.defaultDriver = defaultDriver;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void changeRegNo(String regNo) {
		this.regNo = regNo;
	}

	public void changeModelName(String modelName) {
		this.modelName = modelName;
	}

	public void changeDefaultDriver(Driver defaultDriver) {
		this.defaultDriver = defaultDriver;
	}

	public void changeActive(boolean active) {
		this.active = active;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getOrgId() {
		return orgId;
	}

	public String getRegNo() {
		return regNo;
	}

	public String getModelName() {
		return modelName;
	}

	public Driver getDefaultDriver() {
		return defaultDriver;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
