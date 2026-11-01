# 아키텍처

> [계약 문서 목차](README.md)

설계 초안은 2026-11-01 기준이며 구성 요소 내부 패키지 구조는 **(제안)**입니다. SDK 확인 사항은 2026-11-02에 갱신했습니다. 구현하면서 바뀌면 이 문서를 먼저 고칩니다.

## 1. 전체 구조

```text
┌──────────────── 차량 (Pleos Connect, AAOS) ────────────────┐
│  AutoLog 차량 앱 (app/:car, Kotlin)                         │
│                                                             │
│  Pleos SDK ──┐                                              │
│  (Odometer,  │   ┌──────────┐   ┌──────────┐   ┌─────────┐  │
│   위치, 내비, ├──▶│ 트립 감지 │──▶│ 용도 추정 │──▶│ 음성 확인│  │
│   기어,      │   └──────────┘   └──────────┘   └────┬────┘  │
│   STT/TTS) ──┘                                     ▼        │
│                      ┌───────────────────────────────────┐  │
│                      │ Room (로컬 DB, 오프라인 우선)       │  │
│                      │ 트립 · 수정 이력 · 집 위치 · 설정   │  │
│                      └──────────────┬────────────────────┘  │
│                                     │ WorkManager 업로드 큐  │
└─────────────────────────────────────┼───────────────────────┘
                    HTTPS, Bearer 기기 토큰
                                      ▼
┌──────────── Oracle VM (Docker) ─────────────────────────────┐
│  공통 Nginx (shared-infrastructure 리포 관리, HTTPS 인증서)          │
│     │  autolog-api.aihub.io.kr → autolog-api:8080           │
│     ▼          (Docker 네트워크 shared-proxy)               │
│  ┌──────────────────────────┐      ┌──────────────────┐     │
│  │ autolog-api              │─────▶│ PostgreSQL       │     │
│  │ Spring Boot 4 · Java 25  │      │ (자체 DB 네트워크) │     │
│  │ Flyway · JPA             │      └──────────────────┘     │
│  └──────────────────────────┘                               │
└────────────────────────────────▲────────────────────────────┘
                     HTTPS, Bearer 관리자 JWT, CORS
                                 │
┌────────────── Cloudflare Pages ┴────────────────────────────┐
│  관리자 대시보드 (dashboard/, React · TypeScript · Vite)      │
│  홈(업무비율) · 운행 목록/상세 · 차량/직원/장소 관리 · XLSX     │
└─────────────────────────────────────────────────────────────┘
```

| 구성 요소 | 사용자 | 역할 |
| --- | --- | --- |
| 차량 앱 | 운전자(직원) | 기록. 차가 아는 값(계기판 거리·시각·위치)은 자동으로 채우고, 사람에게는 용도 확인만 받음 |
| 서버 | 차량 앱·대시보드 | 수집·보호. 회사별 데이터 분리, 자동값 보호, 개인 운행 마스킹, 서식 생성 |
| 대시보드 | 회사 관리자(회사당 1명) | 조회·관리. 업무비율 확인, 기준정보 관리, 기록부 내보내기 |

배포 세부(운영 Compose 프로젝트 `autolog`, 공통 Nginx 연결, `/nginx-health`)는 루트 [README](../../README.md#oracle-배포-연결)를 따릅니다.

## 2. 데이터 흐름

### ① 페어링 (차량 1대당 1번) — [rules §2](rules.md#2-페어링-d6)

```text
차량 앱: 토큰 원문 생성·보관 → 해시만 서버로 → 6자리 코드 표시
관리자: 대시보드에서 코드 입력 → 서버가 차량과 기기 연결
차량 앱: PAIRED 확인 → 설정 내려받기 → 이후 모든 요청에 기기 토큰 사용
```

- 차량에서 키보드 입력 없음 (주행 제한 정책)
- 토큰 원문은 차 밖으로 나가지 않음 → 서버 DB가 유출돼도 토큰 재사용 불가

### ② 운행 기록 → 업로드 (핵심 흐름) — [rules §3](rules.md#3-트립-업로드-put-tripsbatch)

```text
기어 P→D (시작 Odometer·시각) → 주행 → P 3분 (종료 Odometer)
→ 용도 추정 (집/회사/거래처 + 시간대 + 내비 목적지)
→ TTS "출근으로 기록할까요?" → STT "응" (10초 무응답 = 미확인)
→ Room 저장 (PENDING) → 네트워크 되면 PUT /trips/batch → SYNCED
```

- 오프라인 우선: 네트워크가 없어도 기록은 차에 남고 나중에 올라감
- 중복 방지: 차량이 만든 `clientUuid`. 다시 보내도 `DUPLICATE`
- 회사·차량은 서버가 토큰으로 결정. 요청 본문의 차량 ID는 신뢰하지 않음

### ③ 기준정보 내려받기 (관리자 → 차량) — [rules §5](rules.md#5-설정-내려받기-get-devicesmeconfig)

```text
관리자가 거래처 추가 → updated_at 갱신
차량 앱: GET /devices/me/config?since=… → 바뀐 장소·직원만 수신
→ 다음 도착 때 "○○ 방문, 업무로 기록할까요?"
```

### ④ 조회·내보내기 (관리자) — [rules §7~9](rules.md#7-마스킹-관리자에게-보이는-값)

```text
대시보드 → GET /dashboard/summary · /trips · /reports/logbook
서버: 회사 범위 필터 → 마스킹(개인 운행 장소·시각 가림) → JSON 또는 XLSX
```

## 3. 구성 요소 내부 구조 (제안)

### 차량 앱

```text
com.autolog.app
├── vehicle/   Pleos Vehicle SDK 2.3.2(Odometer·Gear) 래퍼
├── location/  LocationSource ← FusedLocation SDK | Android 위치 API
├── voice/     VoiceIO       ← Pleos STT/TTS | Android TextToSpeech/SpeechRecognizer
├── trip/      트립 감지 상태 머신 (Foreground Service), 0.5km 병합
├── purpose/   용도 추정 규칙, 음성 응답 해석
├── data/      Room (trip, trip_edit, place[집 포함], config)
├── sync/      WorkManager 작업 + Retrofit API 클라이언트
├── pairing/   토큰 생성·보관, 페어링 화면
└── ui/        Compose 화면, CarUxRestrictions(주행 중 제한)
```

- `LocationSource`, `VoiceIO`는 인터페이스. Pleos SDK가 에뮬레이터에서 안 되면 Android 표준 API 구현으로 교체
- 트립 감지·용도 추정은 SDK에 의존하지 않는 순수 Kotlin으로 두어 단위 테스트

### 서버

```text
com.autolog.server
├── global/      response(ApiResponse), error(예외 처리), security(JWT·기기 토큰 필터, CORS), config
├── auth/        관리자 로그인, /auth/me
├── device/      페어링 코드, 차량 페어링·연결 해제, 설정 내려받기
├── trip/        업로드, 수정 이력, 목록·상세, TripViewMapper(마스킹 단일 지점)
├── masterdata/  vehicle · driver · place
└── report/      월간 집계, 기록부 XLSX (Apache POI)
```

- Controller → Service → Repository(JPA). DB 변경은 Flyway로만
- 인증 필터: Bearer 값이 JWT 형식이면 관리자, 아니면 기기 토큰. 호출 주체가 안 맞으면 403
- 관리자에게 나가는 JSON·XLSX는 모두 `TripViewMapper`를 거침 (마스킹 단위 테스트 필수)

### 대시보드

```text
src/
├── api/        fetch 클라이언트 (JWT 헤더, 401이면 로그인으로), 엔드포인트별 함수
├── pages/      로그인 · 홈 · 운행 목록/상세 · 차량 · 직원 · 장소 · 내보내기
└── components/
```

- Cloudflare Pages에 정적 배포. 로컬 개발은 Vite 프록시로 `localhost:8080`

## 4. 설계 원칙

| 원칙 | 적용 위치 | 이유 |
| --- | --- | --- |
| 오프라인 우선 | 차량 앱 Room + 업로드 큐 | 차량은 네트워크가 끊길 수 있음 |
| 차가 쓴 값은 불변 | 서버가 자동값 수정 거부 (D3) | 위변조 방지 어필 |
| 회사 범위는 서버가 결정 | 토큰의 `orgId`·차량으로 필터, 다른 회사 리소스는 404 | 여러 회사가 한 서버를 같이 씀 |
| 위치 최소 저장 | 집은 차에만 (D2), 서버 트립은 좌표 없이 장소명 | 위치정보 부담 축소 |
| 개인 운행 비공개 | 마스킹 단일 지점 (D4) | 직원 감시 우려 대응 |
| 계약 문서 먼저 | `docs/contracts/`를 고친 다음 코드 | 세 프로젝트가 따로 움직여도 어긋나지 않게 |

## 5. SDK 확인 사항

다음은 실제 SDK 아티팩트·공식 문서로 **확인됨 (2026-11-02)**. API의 존재·권한 확인과 에뮬레이터에서의 실제 동작 확인을 구분합니다.

### Vehicle SDK 2.3.2

- 기어는 `Vehicle.getGear()`로 접근합니다. 기어 조회에 AAOS `CarPropertyManager`가 필요하다는 기존 전제는 정정합니다.
- 현재 값: `Gear.getCurrentGearSelectionState(onComplete, onFailed)`.
- 변경 구독·해제: `registerGearSelection` / `unregisterGearSelection`, 콜백 `GearSelectionListener.onSelected(VehicleGear?)`.
- `VehicleGear` 값에는 `GEAR_PARK`, `GEAR_DRIVE`, `GEAR_REVERSE`, `GEAR_NEUTRAL`, `GEAR_UNKNOWN` 등이 있습니다.
- Odometer·Gear·Brake·DrivingMode의 권한은 `pleos.car.permission.CAR_DRIVE`입니다.
- 에뮬레이터에서 실제 기어 값·콜백이 전달되는지는 **미확인**입니다.

공식 문서: [Vehicle API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry), [Vehicle 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission).

### 음성·위치 SDK

| 기능 | 확인된 API·권한 (2026-11-02) | 실행 검증 |
| --- | --- | --- |
| TTS SDK 3.3.1 | `TextToSpeech(context, Mode)`, `pleos.car.permission.TTS_SERVICE` | 에뮬레이터 동작 미확인 |
| STT SDK 3.3.1 | `SpeechToText(context, Mode)`, `pleos.car.permission.STT_SERVICE` | 에뮬레이터 동작 미확인 |
| FusedLocation | `pleos.car.permission.FUSED_LOCATION`, 보호 수준 `dangerous`이므로 매니페스트 선언과 런타임 요청 필요 | 위치 콜백·지원 여부 미확인 |

- TTS/STT의 `Mode`는 `SERVER` / `ON_DEVICE` / `HYBRID`. `ON_DEVICE`는 앱 등록(`registerApp`)이 불필요합니다 (**확인됨 2026-11-02**).
- 권한명 확인이 App Market 승인 여부나 에뮬레이터 동작 확인을 뜻하지는 않습니다.

공식 문서: [TTS 생성자](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/-text-to-speech), [TTS 앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/app-registration), [TTS 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions), [STT 생성자](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/-speech-to-text), [STT 앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration), [STT 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [FusedLocation 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions).

에뮬레이터 이미지·부팅·Virtual Console 설정은 루트 [README](../../README.md#테스트-환경필요한-설정)를 따릅니다.

## 6. 확인 안 된 전제

| 항목 | 영향 | 확인 방법 |
| --- | --- | --- |
| Vehicle SDK Gear의 실제 값·콜백 수신 (API·CAR_DRIVE 권한은 2026-11-02 확인됨, 실행은 미확인) | 트립 감지 방식 (`trip/`) | Pleos AVD + Virtual Console |
| 외부 인터넷 연결, Foreground Service, STT/TTS ON_DEVICE 실행 (미확인) | `sync/`, `trip/`, `voice/` 구현 방식 | Pleos AVD |
| 로컬 서버 주소가 `10.0.2.2`로 보이는지 | debug 빌드 서버 주소 | Pleos AVD |

모두 차량 앱 내부 구현에만 영향이 있고, 서버·대시보드 구조와 API 계약은 바뀌지 않습니다.
