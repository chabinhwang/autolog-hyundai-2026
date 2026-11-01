package com.autolog.server.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 직원 (= 차량이용자). 삭제 대신 비활성 */
@Entity
@Table(name = "driver")
public class Driver {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(nullable = false, length = 50)
	private String dept;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 차량 설정 내려받기(since)의 기준 */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Driver() {
	}

	public Driver(Long orgId, String dept, String name, Instant now) {
		this.orgId = orgId;
		this.dept = dept;
		this.name = name;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void changeDept(String dept) {
		this.dept = dept;
	}

	public void changeName(String name) {
		this.name = name;
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

	public String getDept() {
		return dept;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
