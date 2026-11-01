package com.autolog.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회사 */
@Entity
@Table(name = "organization")
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "biz_reg_no", nullable = false, length = 12)
	private String bizRegNo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Organization() {
	}

	public Organization(String name, String bizRegNo, Instant createdAt) {
		this.name = name;
		this.bizRegNo = bizRegNo;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getBizRegNo() {
		return bizRegNo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
