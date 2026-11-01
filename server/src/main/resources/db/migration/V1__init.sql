-- AutoLog 1차 스키마 (docs/contracts/database.md)
-- 모든 테이블은 org_id로 회사 범위를 가지거나 상위 테이블로 회사가 정해진다.

CREATE TABLE organization (
	id          bigserial PRIMARY KEY,
	name        varchar(100) NOT NULL,
	biz_reg_no  varchar(12)  NOT NULL,
	created_at  timestamptz  NOT NULL DEFAULT now(),
	CONSTRAINT uq_organization_biz_reg_no UNIQUE (biz_reg_no)
);

-- 회사당 관리자 1명. 확장 시 org_id unique만 해제한다.
CREATE TABLE admin_user (
	id             bigserial PRIMARY KEY,
	org_id         bigint       NOT NULL REFERENCES organization (id),
	login_id       varchar(100) NOT NULL,
	password_hash  varchar(100) NOT NULL,
	display_name   varchar(30)  NOT NULL,
	role           varchar(20)  NOT NULL DEFAULT 'ADMIN',
	last_login_at  timestamptz,
	created_at     timestamptz  NOT NULL DEFAULT now(),
	CONSTRAINT uq_admin_user_org UNIQUE (org_id),
	CONSTRAINT uq_admin_user_login_id UNIQUE (login_id)
);

CREATE TABLE driver (
	id          bigserial PRIMARY KEY,
	org_id      bigint      NOT NULL REFERENCES organization (id),
	dept        varchar(50) NOT NULL,
	name        varchar(30) NOT NULL,
	active      boolean     NOT NULL DEFAULT true,
	created_at  timestamptz NOT NULL,
	updated_at  timestamptz NOT NULL
);
CREATE INDEX ix_driver_org_updated ON driver (org_id, updated_at);

CREATE TABLE vehicle (
	id                 bigserial PRIMARY KEY,
	org_id             bigint      NOT NULL REFERENCES organization (id),
	reg_no             varchar(20) NOT NULL,
	model_name         varchar(50) NOT NULL,
	default_driver_id  bigint REFERENCES driver (id),
	active             boolean     NOT NULL DEFAULT true,
	created_at         timestamptz NOT NULL,
	updated_at         timestamptz NOT NULL,
	CONSTRAINT uq_vehicle_org_reg_no UNIQUE (org_id, reg_no)
);

CREATE TABLE place (
	id          bigserial PRIMARY KEY,
	org_id      bigint       NOT NULL REFERENCES organization (id),
	name        varchar(100) NOT NULL,
	type        varchar(10)  NOT NULL,
	lat         numeric(9, 6) NOT NULL,
	lng         numeric(9, 6) NOT NULL,
	radius_m    integer      NOT NULL DEFAULT 200,
	deleted     boolean      NOT NULL DEFAULT false,
	created_at  timestamptz  NOT NULL,
	updated_at  timestamptz  NOT NULL,
	CONSTRAINT ck_place_type CHECK (type IN ('COMPANY', 'CLIENT')),
	CONSTRAINT ck_place_lat CHECK (lat BETWEEN -90 AND 90),
	CONSTRAINT ck_place_lng CHECK (lng BETWEEN -180 AND 180),
	CONSTRAINT ck_place_radius CHECK (radius_m BETWEEN 50 AND 2000)
);
CREATE INDEX ix_place_org_updated ON place (org_id, updated_at);

CREATE TABLE device (
	id               bigserial PRIMARY KEY,
	org_id           bigint      NOT NULL REFERENCES organization (id),
	vehicle_id       bigint      NOT NULL REFERENCES vehicle (id),
	installation_id  varchar(64) NOT NULL,
	token_hash       char(64)    NOT NULL,
	paired_at        timestamptz NOT NULL,
	last_synced_at   timestamptz,
	revoked_at       timestamptz,
	CONSTRAINT uq_device_token_hash UNIQUE (token_hash)
);
-- 차량 1대 = 활성 기기 1개
CREATE UNIQUE INDEX uq_device_active_vehicle ON device (vehicle_id) WHERE revoked_at IS NULL;

CREATE TABLE pairing_code (
	id               uuid PRIMARY KEY,
	code             char(6)     NOT NULL,
	installation_id  varchar(64) NOT NULL,
	token_hash       char(64)    NOT NULL,
	status           varchar(10) NOT NULL DEFAULT 'PENDING',
	vehicle_id       bigint REFERENCES vehicle (id),
	expires_at       timestamptz NOT NULL,
	created_at       timestamptz NOT NULL,
	paired_at        timestamptz,
	CONSTRAINT ck_pairing_code_status CHECK (status IN ('PENDING', 'PAIRED', 'EXPIRED'))
);
-- 대기 중인 코드끼리는 중복 없음
CREATE UNIQUE INDEX uq_pairing_code_pending ON pairing_code (code) WHERE status = 'PENDING';

CREATE TABLE trip (
	id                 bigserial PRIMARY KEY,
	client_uuid        uuid         NOT NULL,
	org_id             bigint       NOT NULL REFERENCES organization (id),
	vehicle_id         bigint       NOT NULL REFERENCES vehicle (id),
	device_id          bigint       NOT NULL REFERENCES device (id),
	driver_id          bigint REFERENCES driver (id),
	trip_date          date         NOT NULL,
	started_at         timestamptz  NOT NULL,
	ended_at           timestamptz  NOT NULL,
	odo_start_km       numeric(9, 1) NOT NULL,
	odo_end_km         numeric(9, 1) NOT NULL,
	distance_km        numeric(8, 1) NOT NULL,
	origin_type        varchar(12)  NOT NULL,
	dest_type          varchar(12)  NOT NULL,
	origin_label       varchar(100),
	dest_label         varchar(100),
	origin_place_id    bigint REFERENCES place (id),
	dest_place_id      bigint REFERENCES place (id),
	auto_purpose       varchar(12)  NOT NULL,
	purpose            varchar(12)  NOT NULL,
	purpose_source     varchar(8)   NOT NULL,
	business_category  varchar(12),
	note               varchar(200),
	recorded_at        timestamptz  NOT NULL,
	synced_at          timestamptz  NOT NULL DEFAULT now(),
	updated_at         timestamptz  NOT NULL,
	app_version        varchar(20),
	CONSTRAINT uq_trip_client_uuid UNIQUE (client_uuid),
	CONSTRAINT ck_trip_time_order CHECK (ended_at > started_at),
	CONSTRAINT ck_trip_odo_order CHECK (odo_end_km >= odo_start_km),
	CONSTRAINT ck_trip_odo_start CHECK (odo_start_km >= 0),
	CONSTRAINT ck_trip_distance CHECK (distance_km >= 0),
	CONSTRAINT ck_trip_origin_type CHECK (origin_type IN ('HOME', 'COMPANY', 'CLIENT', 'UNREGISTERED')),
	CONSTRAINT ck_trip_dest_type CHECK (dest_type IN ('HOME', 'COMPANY', 'CLIENT', 'UNREGISTERED')),
	CONSTRAINT ck_trip_auto_purpose CHECK (auto_purpose IN ('COMMUTE', 'BUSINESS', 'PERSONAL', 'UNCONFIRMED')),
	CONSTRAINT ck_trip_purpose CHECK (purpose IN ('COMMUTE', 'BUSINESS', 'PERSONAL', 'UNCONFIRMED')),
	CONSTRAINT ck_trip_purpose_source CHECK (purpose_source IN ('AUTO', 'VOICE', 'MANUAL', 'ADMIN')),
	CONSTRAINT ck_trip_business_category CHECK (
		business_category IS NULL
		OR (purpose = 'BUSINESS' AND business_category IN ('VISIT', 'MEETING', 'PROMOTION', 'TRAINING', 'OTHER'))
	)
);
CREATE INDEX ix_trip_org_trip_date ON trip (org_id, trip_date);
CREATE INDEX ix_trip_vehicle_started ON trip (vehicle_id, started_at);

CREATE TABLE trip_edit_log (
	id                bigserial PRIMARY KEY,
	trip_id           bigint      NOT NULL REFERENCES trip (id),
	seq               integer     NOT NULL,
	client_edit_uuid  uuid,
	actor_type        varchar(8)  NOT NULL,
	actor_id          bigint      NOT NULL,
	changes           jsonb       NOT NULL,
	reason            varchar(200),
	edited_at         timestamptz NOT NULL,
	created_at        timestamptz NOT NULL DEFAULT now(),
	CONSTRAINT uq_trip_edit_log_seq UNIQUE (trip_id, seq),
	CONSTRAINT uq_trip_edit_log_client_edit_uuid UNIQUE (client_edit_uuid),
	CONSTRAINT ck_trip_edit_log_actor_type CHECK (actor_type IN ('DEVICE', 'ADMIN'))
);

CREATE TABLE driver_consent (
	id               bigserial PRIMARY KEY,
	org_id           bigint      NOT NULL REFERENCES organization (id),
	driver_id        bigint      NOT NULL REFERENCES driver (id),
	consent_version  varchar(20) NOT NULL,
	consented_at     timestamptz NOT NULL,
	device_id        bigint      NOT NULL REFERENCES device (id),
	created_at       timestamptz NOT NULL DEFAULT now(),
	CONSTRAINT uq_driver_consent_version UNIQUE (driver_id, consent_version)
);
