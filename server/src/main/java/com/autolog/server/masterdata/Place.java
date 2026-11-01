package com.autolog.server.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** 회사·거래처 장소. 소프트 삭제 (차량 동기화에 삭제 전달) */
@Entity
@Table(name = "place")
public class Place {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private Long orgId;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PlaceType type;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lat;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lng;

	@Column(name = "radius_m", nullable = false)
	private int radiusM;

	@Column(nullable = false)
	private boolean deleted;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Place() {
	}

	public Place(Long orgId, String name, PlaceType type, BigDecimal lat, BigDecimal lng, int radiusM, Instant now) {
		this.orgId = orgId;
		this.name = name;
		this.type = type;
		this.lat = lat;
		this.lng = lng;
		this.radiusM = radiusM;
		this.deleted = false;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void changeName(String name) {
		this.name = name;
	}

	public void changeType(PlaceType type) {
		this.type = type;
	}

	public void changeLat(BigDecimal lat) {
		this.lat = lat;
	}

	public void changeLng(BigDecimal lng) {
		this.lng = lng;
	}

	public void changeRadiusM(int radiusM) {
		this.radiusM = radiusM;
	}

	public void delete() {
		this.deleted = true;
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

	public String getName() {
		return name;
	}

	public PlaceType getType() {
		return type;
	}

	public BigDecimal getLat() {
		return lat;
	}

	public BigDecimal getLng() {
		return lng;
	}

	public int getRadiusM() {
		return radiusM;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
