# 아키텍처

> [계약 문서 목차](README.md)

설계 초안은 2026-11-01 기준입니다. 차량 앱 내부 구조·2~6단계 구현·실측은 **확인됨 (2026-11-02)**이며 코드 `6cdbd35`·`25c6dc3`·`226fbb9`를 반영했습니다. 상세는 [차량 앱 구현 계약](car-app.md)을 따릅니다. 구현하면서 바뀌면 이 문서를 먼저 고칩니다.

## 1. 전체 구조

```text
┌──────────────── 차량 (Pleos Connect, AAOS) ────────────────┐
│  AutoLog 차량 앱 (app/:car, Kotlin)                         │
│                                                             │
│  Pleos SDK ──┐                                              │
│  (Odometer,  │   ┌──────────┐   ┌──────────┐   ┌─────────┐  │
│   위치,      ├──▶│ 트립 감지 │──▶│ 용도 추정 │──▶│ 음성 확인│  │
│   기어,      │   └──────────┘   └──────────┘   └────┬────┘  │
│   STT/TTS) ──┘                                     ▼        │
│                      ┌───────────────────────────────────┐  │
│                      │ Room (로컬 DB, 오프라인 우선)       │  │
│                      │ 트립 · 수정 · 장소 · 직원 · 동의   │  │
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
기어 P→D/R (시작 Odometer·시각) → 주행 → P 3분 (debug 시연은 30초, 종료 Odometer)
→ 용도 추정 (집/회사/거래처 + Asia/Seoul 시간대)
→ Room에 UNCONFIRMED/AUTO 먼저 저장 (PENDING, 답변 대기)
→ 공유 음성 인스턴스 TTS 질문 → STT 수집 (10초, 완료 조각 쉼 1.5초·부분 결과 무변화 3초)
→ 음성 결과 반영 → WorkManager PUT /trips/batch → SYNCED
```

- 오프라인 우선: 네트워크가 없어도 기록은 차에 남고 나중에 올라감
- 질문 중 앱이 꺼지면 다음 실행 때 답변 대기를 해제해 미확인으로 업로드. 진행 중 시작 값은 DataStore에서 복구
- 주행 중 프로세스 강제 종료 → START_STICKY 즉시 재시작·시작 계기판 값 유지로 9.6km 한 건, debug 30초 미만 정차 후 재출발은 7.5km 한 건으로 기록 **확인됨 (2026-11-02)**
- 트립 좌표는 장소 판정 후 버리고 Room에 저장하지 않음. 1차 NaviHelper 미사용, 미등록 장소 라벨은 `null`
- 중복 방지: 차량이 만든 `clientUuid`. 다시 보내도 `DUPLICATE`
- 회사·차량은 서버가 토큰으로 결정. 요청 본문의 차량 ID는 신뢰하지 않음
- 1차 종료 확정은 P 유지 시간만 사용. 0.5km 미만은 직전 트립이 `PENDING`이면 병합하고, 이미 서버에 올라갔으면 D3에 따라 기록하지 않고 버림 (구현 기준, 결정됨 2026-11-02)
- 용도 추정·사용일자 계산의 시간대는 기기 설정과 무관하게 `Asia/Seoul`을 명시 (구현 기준 2026-11-02)

### ③ 기준정보 내려받기 (관리자 → 차량) — [rules §5](rules.md#5-설정-내려받기-get-devicesmeconfig)

```text
관리자가 거래처 추가·직원 비활성·미확인 운행 확정
차량 앱: 앱 시작·주행 시작·저장/수정·주기 동기화에서 GET /devices/me/config?since=…
→ 바뀐 장소·직원 및 관리자 수정 tripUpdates 수신
→ 비활성 선택 이용자 해제·로컬 운행 용도 갱신
→ 다음 도착 때 "○○ 방문, 업무로 기록할까요?"
```

주행 직전 추가한 '그린물류' 6.8km 인식·업무 저장, 관리자 확정 후 차량 확인 목록에서 제외, 비활성 이용자 없는 업로드·과거 `DRIVER_NOT_FOUND` 실패 건 복구는 **확인됨 (2026-11-02)**. 재전송 대상 밖의 실패는 상단 빨간 '전송 실패 N건'으로 표시합니다.

### ④ 조회·내보내기 (관리자) — [rules §7~9](rules.md#7-마스킹-관리자에게-보이는-값)

```text
대시보드 → GET /dashboard/summary · /trips · /reports/logbook
서버: 회사 범위 필터 → 마스킹(개인 운행 장소·시각 가림) → JSON 또는 XLSX
```

## 3. 구성 요소 내부 구조

### 차량 앱 (실제 구현, 확인됨 2026-11-02)

```text
com.autolog.app
├── domain/    열거값·모델
├── trip/      TripDetector, ShortTripPolicy, TripRecorder, TripRecorderService, BootReceiver
├── purpose/   PlaceMatcher, PurposeEstimator, VoiceAnswerParser
├── data/
│   ├── local/     Room (trip, trip_edit, place, driver, consent)
│   ├── remote/    Retrofit API·기기 인증 인터셉터
│   ├── settings/  DataStore (기기·차량·동의 버전·진행 중 시작 값)
│   └── TripRepository, ConsentRepository
├── sync/      SyncEngine, SyncWorker, TripMapper
├── pairing/   PairingController, DeviceTokens, KeystoreCipher
├── vehicle/   VehicleSource ← PleosVehicleSource
├── location/  LocationSource ← PleosLocationSource
├── voice/     VoiceIO·ListenResult ← PleosVoiceIO, SegmentCollector
├── export/    CsvExporter
└── ui/        Compose (정차 중 화면)
```

- `VehicleSource`·`LocationSource`·`VoiceIO` 인터페이스는 유지. Pleos SDK가 동작해 Android 표준 TTS·위치 대체 구현은 만들지 않음
- 화면·서비스는 `AppContainer.voice` 하나를 공유하고 TTS/STT 작업을 직렬화. 복수 인스턴스의 TTS 완료 콜백 충돌로 질문 후 듣기가 20초 늦던 문제 수정 후 5ms 전환 **확인됨 (2026-11-02)**
- 즉시 동기화는 REPLACE로 새 요청이 백오프 대기 작업을 대체. 최근·미확인 목록 새 운행 맨 위 표시, 등록 거래처 도착의 화면 '업무'는 VISIT **확인됨 (2026-11-02)**
- DO 미선언이며 플랫폼이 주행 중 화면 차단. `CarUxRestrictions` 리스너는 두지 않고 도착 확인은 음성으로 처리
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

다음은 실제 SDK 아티팩트·공식 문서 및 0단계 debug 점검 화면 실측으로 **확인됨 (2026-11-02)**. 실측 환경은 Pleos Connect Emulator 2.1.0(arm64), AVD `Pleos_Connect_v2.1`, 기기 프로필 id `Pleos_Connect_v2_0`입니다. API·권한 확인, 에뮬레이터 실측, 아직 미확인인 입력 경로를 구분합니다.

### Vehicle SDK 2.3.2

- 기어는 `Vehicle.getGear()`로 접근합니다. 기어 조회에 AAOS `CarPropertyManager`가 필요하다는 기존 전제는 정정합니다.
- 현재 값: `Gear.getCurrentGearSelectionState(onComplete, onFailed)`.
- 변경 구독·해제: `registerGearSelection` / `unregisterGearSelection`, 콜백 `GearSelectionListener.onSelected(VehicleGear?)`.
- `VehicleGear` 값에는 `GEAR_PARK`, `GEAR_DRIVE`, `GEAR_REVERSE`, `GEAR_NEUTRAL`, `GEAR_UNKNOWN` 등이 있습니다.
- Odometer·Gear·Brake·DrivingMode의 권한은 `pleos.car.permission.CAR_DRIVE`입니다.
- 기어 `getCurrentGearSelectionState`·`registerGearSelection`에서 `GEAR_PARK` / `GEAR_DRIVE` 값과 변경 알림 수신 **확인됨 (2026-11-02)**. 기어 읽기 전제는 해소됐으며 AAOS `CarPropertyManager`가 불필요합니다.
- Odometer `getCurrentDistanceDriven`·`registerDistanceDriven`의 km 값·알림 수신 **확인됨 (2026-11-02)**. 값이 바뀌지 않아도 약 4초마다 같은 값이 재통지됩니다. 이 두 도메인 조회·구독에 사용한 권한은 `CAR_DRIVE`뿐입니다.

공식 문서: [Vehicle API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry), [Vehicle 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission).

### 음성·위치 SDK

| 기능 | 확인된 API·권한 (2026-11-02) | 실행 검증 |
| --- | --- | --- |
| TTS SDK 3.3.1 | `TextToSpeech(context, Mode)`, `pleos.car.permission.TTS_SERVICE` | ON_DEVICE 초기화·재생 `onReady` → `onStart` → `onDone` **확인됨 2026-11-02** |
| STT SDK 3.3.1 | `SpeechToText(context, Mode)`, `pleos.car.permission.STT_SERVICE` | ON_DEVICE + sendAudio PCM 입력으로 한국어 인식 **확인됨 2026-11-02**. 호스트 마이크 경로는 미확인 |
| FusedLocation | `pleos.car.permission.FUSED_LOCATION`, 보호 수준 `dangerous`이므로 매니페스트 선언과 런타임 요청 필요 | `isSupported=true`, `getLastKnownLocation`·`registerFusedLocationCallback` 약 1초 간격 위치 수신 **확인됨 2026-11-02**. 에뮬 기본 좌표 `37.422,-122.084` |

- TTS/STT의 `Mode`는 `SERVER` / `ON_DEVICE` / `HYBRID`. `ON_DEVICE`는 앱 등록(`registerApp`)이 불필요합니다 (**확인됨 2026-11-02**).
- TTS 3.3.1에서 `TextToSpeech.addEventListener`를 `initialize` 성공 콜백 전에 호출하면 SDK 내부 `lateinit` 예외로 앱이 종료되는 현상 **확인됨 (2026-11-02)**. 리스너는 초기화 성공 콜백 이후 등록합니다.
- STT `sendAudio`에 16kHz·16bit·mono PCM을 입력해 "네" / "거래처 방문이에요" 인식 **확인됨 (2026-11-02)**. 쉼(EPD) 단위로 여러 조각이 각각 `completed=true`로 도착하므로 조각을 모아 응답을 해석해야 합니다. `completed=true` 하나를 전체 응답 종료로 취급하지 않습니다.
- 완료 조각 없이 끝나면 마지막 `completed=false` 부분 결과를 사용합니다 (3초 무변화·오류·10초 종료). 콜백 없는 크래시도 부분 결과가 있으면 사용하고, 완료·부분 결과 모두 없을 때 무응답·미확인으로 처리 (구현 확인됨 2026-11-02)
- 이 PC의 에뮬레이터 호스트 마이크 경로에서는 아직 인식 결과가 없습니다. macOS 마이크 권한·이 경로의 정상 동작은 **미확인**입니다.
- App Market 승인 절차는 별도 **미확인**입니다.

공식 문서: [TTS 생성자](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/-text-to-speech), [TTS 앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/app-registration), [TTS 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions), [STT 생성자](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/-speech-to-text), [STT 앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration), [STT 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [FusedLocation 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions).

### 에뮬레이터·연결 실측 (확인됨 2026-11-02)

- debug 앱 → 호스트 로컬 서버 `http://10.0.2.2:8080` HTTP 200, 외부 HTTPS 통신을 확인했습니다. 2~6단계에서는 트립 저장·화면 수정 직후 WorkManager 업로드 성공도 **확인됨 (2026-11-02)**.
- Virtual Console은 `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`을 한 번 열어 활성화해야 합니다. 열기 전 HTTP 쓰기는 `{"error":"emulator_inactive"}`입니다.
- 활성화 후 `PUT /vehicle/gear?gear=Drive`로 제어할 수 있으며 `Park` / `Reverse` / `Neutral`도 받습니다. `PUT /vehicle/odometer?traveled_distance_km=12.4`는 소수를 받습니다. 1km 단위인 것은 화면 슬라이더이며, HTTP로 12.4km 시연이 가능합니다.
- 에뮬레이터 기본 시간대는 호스트 환경에 따라 `America/Los_Angeles`일 수 있음을 확인했습니다. 용도 추정과 CSV 사용일자·시각은 `Asia/Seoul`을 명시한 것을 **확인됨 (2026-11-02)**. 서버 `tripDate`는 별도로 시작 시각의 서울 날짜를 계산합니다.

이미지 설치·AVD 생성·Virtual Console 연결은 **완료 (확인됨 2026-11-02)**. 이미지·부팅·Virtual Console 설정은 루트 [README](../../README.md#테스트-환경필요한-설정)를 따릅니다.

### 2~6단계 실측 (확인됨 2026-11-02)

- 화면에서 Foreground Service를 `specialUse|microphone`으로 실행 (`types=0x40000080`). AVD 재부팅 뒤 BOOT_COMPLETED는 `specialUse`만 실행(`types=0x40000000`). 앱 uid 녹음 AppOp `mode=1`(무시)이지만 Pleos 음성 서비스가 자기 uid(`ai.pleos.playground.caas`)로 녹음 시작·허용, PCM 응답 인식·확정까지 **확인됨 (2026-11-02)**. 실제 마이크 음성 입력은 미확인
- 페어링 → 설정·이용자·동의·집 등록 → 12.4km 업무 운행 음성 확인 → 서버 `HOME→CLIENT`, `BUSINESS/VOICE/VISIT` → 대시보드 표시 → 이번 달 CSV 저장까지 확인
- 무응답 `UNCONFIRMED/AUTO` 업로드·마스킹 → 차량 화면 '개인' → `PERSONAL/MANUAL` 수정 이력 → 대시보드 '장소 비공개·수정됨' 확인
- 저장·수정·실패 정책·권한·CSV 열은 [차량 앱 구현 계약](car-app.md)을 따름

## 6. 확인 안 된 전제

| 항목 | 영향 | 확인 방법 |
| --- | --- | --- |
| 부팅 경로의 실제 마이크 음성 입력 (미확인) | 도착 질문 입력 경로 | specialUse·음성 서비스 자기 uid 녹음·PCM 인식/확정은 확인됨. 실제 마이크는 별도이며 실패 시 미확인·정차 중 3버튼 |
| 에뮬레이터 호스트 마이크 입력·macOS 마이크 권한 (미확인) | `voice/` 입력 경로 | 이 PC 권한·에뮬레이터 마이크 경로 확인. sendAudio PCM 인식은 확인됨 |
| STT 엔진 오류 콜백 후 한 번 재질문의 에뮬레이터 실측 (미확인) | 음성 오류 복구 | 단위 테스트는 검증됨. 완료·부분 결과 모두 없을 때 오류 콜백은 재질문, 콜백 없는 크래시는 미확인 |

온디바이스 STT 오류·오인식과 "네, 맞아요"에서도 발생한 SIGSEGV는 **확인됨 (2026-11-02, 누적 약 12회 중 3회)**. 긴 응답은 짧은 "업무" 오인식을 줄이는 권장이며 크래시 방지는 보장하지 않습니다. 응답 전체 '너무' 보정·재질문 범위는 [car-app §3](car-app.md#3-음성용도-추정주행-제한)을 따릅니다.

모두 차량 앱 내부 구현에만 영향이 있고, 서버·대시보드 구조와 API 계약은 바뀌지 않습니다.
