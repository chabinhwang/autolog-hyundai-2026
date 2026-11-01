package com.autolog.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회사 관리자 (회사당 1명) */
@Entity
@Table(name = "admin_user")
public class AdminUser {

	public static final String ROLE_ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(name = "login_id", nullable = false, length = 100)
	private String loginId;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 30)
	private String displayName;

	@Column(nullable = false, length = 20)
	private String role;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AdminUser() {
	}

	public AdminUser(Long orgId, String loginId, String passwordHash, String displayName, Instant createdAt) {
		this.orgId = orgId;
		this.loginId = loginId;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.role = ROLE_ADMIN;
		this.createdAt = createdAt;
	}

	public void recordLogin(Instant at) {
		this.lastLoginAt = at;
	}

	public Long getId() {
		return id;
	}

	public Long getOrgId() {
		return orgId;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getRole() {
		return role;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}
}
