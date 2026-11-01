package com.autolog.server.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** 운행 수정 이력. 요청 1건 = 1행, 덮어쓰지 않고 추가만 한다. */
@Entity
@Table(name = "trip_edit_log")
public class TripEditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private Long tripId;

	@Column(nullable = false, updatable = false)
	private int seq;

	@Column(name = "client_edit_uuid", updatable = false)
	private UUID clientEditUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, updatable = false, length = 8)
	private ActorType actorType;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private Long actorId;

	/** [{"field":"purpose","before":"UNCONFIRMED","after":"BUSINESS"}] (JSON 문자열로 보관) */
	@ColumnTransformer(write = "?::jsonb")
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private String changes;

	@Column(updatable = false, length = 200)
	private String reason;

	@Column(name = "edited_at", nullable = false, updatable = false)
	private Instant editedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TripEditLog() {
	}

	public TripEditLog(Long tripId, int seq, UUID clientEditUuid, ActorType actorType, Long actorId, String changes,
			String reason, Instant editedAt, Instant createdAt) {
		this.tripId = tripId;
		this.seq = seq;
		this.clientEditUuid = clientEditUuid;
		this.actorType = actorType;
		this.actorId = actorId;
		this.changes = changes;
		this.reason = reason;
		this.editedAt = editedAt;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getTripId() {
		return tripId;
	}

	public int getSeq() {
		return seq;
	}

	public UUID getClientEditUuid() {
		return clientEditUuid;
	}

	public ActorType getActorType() {
		return actorType;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getChanges() {
		return changes;
	}

	public String getReason() {
		return reason;
	}

	public Instant getEditedAt() {
		return editedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
