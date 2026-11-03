# 데이터베이스 (PostgreSQL)

> [기술 문서 목차](README.md)

운행·차량·직원·장소·동의·수정 이력을 저장하는 구조입니다. [아키텍처](architecture.md)에서 흐름을 먼저 확인한 뒤 필요한 테이블을 참고하세요.

- 마이그레이션은 Flyway(`server/src/main/resources/db/migration`)로만 합니다. JPA는 `ddl-auto: validate`.
- 모든 테이블에 회사 범위(`org_id`)가 있거나 상위 테이블로 회사가 정해집니다.
- 서버 테스트도 PostgreSQL을 사용합니다. Testcontainers 또는 직접 준비한 테스트 DB의 지정 방법은 [개발·실행 안내](development.md#서버)에 있습니다.

## 열거값

| 이름 | 값 |
| --- | --- |
| 용도 `purpose` | `COMMUTE`(출퇴근, 서식 ⑧) · `BUSINESS`(일반 업무, ⑨) · `PERSONAL`(개인) · `UNCONFIRMED`(미확인) |
| 판정 출처 `purpose_source` | `AUTO`(추정값, 확인 안 됨) · `VOICE`(음성 확인) · `MANUAL`(차량 화면 선택) · `ADMIN`(관리자 수정) |
| 업무 세부 `business_category` | `VISIT`(거래처 방문) · `MEETING`(회의) · `PROMOTION`(판촉) · `TRAINING`(교육) · `OTHER`. 용도가 BUSINESS일 때만 값 있음 |
| 장소 유형 `place.type` | `COMPANY` · `CLIENT` |
| 출발·도착 유형 | `HOME` · `COMPANY` · `CLIENT` · `UNREGISTERED` |
| 페어링 상태 | `PENDING` · `PAIRED` · `EXPIRED` |
| 수정 주체 | `DEVICE` · `ADMIN` |

## organization (회사)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `name` | varchar(100) | not null | 서식 상단 상호명 |
| `biz_reg_no` | varchar(12) | not null, unique | 서식 상단 사업자등록번호 (`000-00-00000`) |
| `created_at` | timestamptz | not null default now() | |

## admin_user (관리자, 회사당 1개)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, unique, FK organization | 회사당 1개. 확장 시 unique만 해제 |
| `login_id` | varchar(100) | not null, unique | 이메일 형식 |
| `password_hash` | varchar(100) | not null | BCrypt |
| `display_name` | varchar(30) | not null | |
| `role` | varchar(20) | not null default 'ADMIN' | |
| `last_login_at` | timestamptz | null | |
| `created_at` | timestamptz | not null default now() | |

## driver (직원 = 차량이용자, 회사당 여러 명)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, FK | 인덱스 (org_id, updated_at) |
| `dept` | varchar(50) | not null | 서식 ④ 부서 |
| `name` | varchar(30) | not null | 서식 ④ 성명 |
| `active` | boolean | not null default true | 삭제 대신 비활성 |
| `created_at`, `updated_at` | timestamptz | not null | `updated_at`은 차량 동기화 기준 |

## vehicle (차량, 회사당 여러 대)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, FK | unique (org_id, reg_no) |
| `reg_no` | varchar(20) | not null | 서식 ② 자동차등록번호 |
| `model_name` | varchar(50) | not null | 서식 ① 차종 |
| `default_driver_id` | bigint | null, FK driver | 차량 앱 이용자 선택 기본값 |
| `active` | boolean | not null default true | |
| `created_at`, `updated_at` | timestamptz | not null | |

## place (회사·거래처)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, FK | 인덱스 (org_id, updated_at) |
| `name` | varchar(100) | not null | 서식 ⑩ 방문지 |
| `type` | varchar(10) | not null, COMPANY/CLIENT | |
| `lat` | numeric(9,6) | not null, -90~90 | 차량 도착 판정용 |
| `lng` | numeric(9,6) | not null, -180~180 | |
| `radius_m` | integer | not null default 200, 50~2000 | 도착 판정 반경 |
| `deleted` | boolean | not null default false | 소프트 삭제 (차량 동기화에 삭제 전달) |
| `created_at`, `updated_at` | timestamptz | not null | |

## device (차량 앱 기기)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, FK | |
| `vehicle_id` | bigint | not null, FK | 부분 unique (vehicle_id) where revoked_at is null |
| `installation_id` | varchar(64) | not null | 앱 설치 ID |
| `token_hash` | char(64) | not null, unique | 기기 토큰 SHA-256 hex |
| `paired_at` | timestamptz | not null | |
| `last_synced_at` | timestamptz | null | 대시보드 표시·경고 |
| `revoked_at` | timestamptz | null | 연결 해제 |

## pairing_code

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | uuid | PK | = pairingId |
| `code` | char(6) | not null | 부분 unique (code) where status = 'PENDING' |
| `installation_id` | varchar(64) | not null | |
| `token_hash` | char(64) | not null | 페어링 시 device로 복사 |
| `status` | varchar(10) | not null default 'PENDING' | |
| `vehicle_id` | bigint | null, FK | 페어링된 차량 |
| `expires_at` | timestamptz | not null | 발급 + 10분 |
| `created_at` | timestamptz | not null | |
| `paired_at` | timestamptz | null | |

- 만료 판정은 조회 시점의 `expires_at`을 기준으로 합니다. 상태 정리 작업 유무와 관계없이 만료된 코드는 사용할 수 없습니다.

## trip (운행)

| 컬럼 | 타입 | 제약 | 서식 / 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | 내부용, 외부 비노출 |
| `client_uuid` | uuid | not null, unique | 중복 방지 키·외부 식별자 |
| `org_id`, `vehicle_id`, `device_id` | bigint | not null, FK | 기기 토큰에서 결정. 인덱스 (org_id, trip_date), (vehicle_id, started_at) |
| `driver_id` | bigint | null, FK | ④ 차량이용자. null = 미지정 |
| `trip_date` | date | not null | ③ 서버 계산 (Asia/Seoul) |
| `started_at`, `ended_at` | timestamptz | not null, ended_at > started_at | 불변 (D3) |
| `odo_start_km` | numeric(9,1) | not null | ⑤ 불변 |
| `odo_end_km` | numeric(9,1) | not null, ≥ odo_start_km | ⑥ 불변 |
| `distance_km` | numeric(8,1) | not null | ⑦ 서버 계산, 불변 |
| `origin_type`, `dest_type` | varchar(12) | not null | HOME/COMPANY/CLIENT/UNREGISTERED |
| `origin_label`, `dest_label` | varchar(100) | null | ⑩ 방문지 (마스킹 대상). HOME은 '집' |
| `origin_place_id`, `dest_place_id` | bigint | null, FK place | COMPANY·CLIENT일 때 |
| `auto_purpose` | varchar(12) | not null | 앱 추정값, 불변 |
| `purpose` | varchar(12) | not null | 현재값 |
| `purpose_source` | varchar(8) | not null | |
| `business_category` | varchar(12) | null | ⑩ 업무 세부 |
| `note` | varchar(200) | null | ⑩ 메모 |
| `recorded_at` | timestamptz | not null | 차량 로컬 저장 시각 |
| `synced_at` | timestamptz | not null default now() | 서버 수신 시각 |
| `updated_at` | timestamptz | not null | 마지막 수정 반영 시각 |
| `app_version` | varchar(20) | null | |

- Odometer는 Vehicle SDK가 km 단위 `Float`로 전달합니다. 서버에는 계기판 양끝을 소수 첫째 자리로 반올림해 저장하고 그 차이로 주행거리를 계산합니다.

## trip_edit_log (수정 이력)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `trip_id` | bigint | not null, FK | unique (trip_id, seq) |
| `seq` | integer | not null | 트립별 1부터 |
| `client_edit_uuid` | uuid | null, unique | 차량 수정의 중복 방지 키 |
| `actor_type` | varchar(8) | not null | DEVICE/ADMIN |
| `actor_id` | bigint | not null | device.id 또는 admin_user.id |
| `changes` | jsonb | not null | `[{"field":"purpose","before":"UNCONFIRMED","after":"BUSINESS"}]` |
| `reason` | varchar(200) | null | 관리자 수정은 필수 |
| `edited_at` | timestamptz | not null | 수정한 시각 (차량은 차량 시각) |
| `created_at` | timestamptz | not null default now() | 서버 수신 시각 |

## driver_consent (직원 동의 기록, D13)

| 컬럼 | 타입 | 제약 | 비고 |
| --- | --- | --- | --- |
| `id` | bigserial | PK | |
| `org_id` | bigint | not null, FK | |
| `driver_id` | bigint | not null, FK | unique (driver_id, consent_version) |
| `consent_version` | varchar(20) | not null | 동의 문서 버전 (예: `2026-11-01`) |
| `consented_at` | timestamptz | not null | 차량에서 동의한 시각 |
| `device_id` | bigint | not null, FK | 동의를 받은 차량 기기 |
| `created_at` | timestamptz | not null default now() | 서버 수신 시각 |

- 철회는 1차 범위 밖 (관리자 문의로 수동 처리). 확장 시 `withdrawn_at` 추가

## 2차에 추가할 것

- `monthly_close`(월 마감)
- 해시 체인 컬럼: `trip.auto_hash`, `trip_edit_log.prev_hash`·`hash`
