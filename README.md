# AutoLog

차가 쓰고, 한마디로 끝내는 업무용승용차 운행일지 앱 (Pleos Connect).

차량 앱이 계기판 주행거리·시간·출발/도착지를 자동으로 기록하고, 업무 여부는 도착할 때 음성으로 한 번만 확인받습니다. 기록은 서버로 동기화되고, 법인 관리자는 대시보드에서 업무사용비율 확인·월 마감·국세청 운행기록부 내보내기를 합니다.

## 구성

| 폴더 | 내용 | 기술 |
| --- | --- | --- |
| [`app/`](app) | 차량 앱 (모듈 `:car`) | Kotlin, Jetpack Compose, Room, WorkManager, Pleos Connect SDK |
| [`server/`](server) | 백엔드 API | Java 25, Spring Boot 4.1, PostgreSQL, Flyway |
| [`dashboard/`](dashboard) | 관리자 웹 | React 19, TypeScript, Vite 8 |
| [`apk/`](apk) | 제출용 APK (`팀명_앱이름_release.apk`) | |
| [`deploy/`](deploy/README.md) | Oracle 운영 Compose·배포 스크립트·환경변수·복구 안내 | Docker Compose, GitHub Actions |

## 실행 방법

### 서버

```sh
cd server
docker compose up -d                                         # 로컬 PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=local'    # http://localhost:8080
./gradlew test
```

- `local` 프로필은 개발용 JWT 키·CORS(`http://localhost:5173`)·시드 계정을 씁니다. 시드 계정: `admin@autolog-demo.kr` / `autolog-local-1234` (회사 「오토로그 주식회사」).
- `./gradlew test`는 Testcontainers로 PostgreSQL을 띄우므로 Docker가 실행 중이어야 합니다. Docker가 없으면 PostgreSQL 18을 직접 띄우고 `AUTOLOG_TEST_DB_URL`·`AUTOLOG_TEST_DB_USERNAME`·`AUTOLOG_TEST_DB_PASSWORD`로 지정하면 컨테이너 대신 그 DB를 씁니다(빈 DB 권장). 테스트가 끝나면 JaCoCo 커버리지 보고서가 `build/reports/jacoco/test/html/index.html`에 생깁니다.
- 운영 환경변수: 필수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`(32바이트 이상), `CORS_ALLOWED_ORIGINS` / 선택 `PORT`, `JWT_EXPIRES_HOURS`, `CONSENT_VERSION`, `SEED_*`. 설명은 [config.md](docs/contracts/config.md)에 있습니다.

### 대시보드

```sh
cd dashboard
npm ci
npm run dev      # http://localhost:5173, /api는 localhost:8080으로 프록시
npm run lint
npm run test     # vitest (jsdom)
npm run build    # dist/
```

- `VITE_API_BASE_URL`: `/api/v1`까지 포함한 API 주소(빌드 시점 값). 비우면 `/api/v1`로 호출해 개발 서버 프록시를 씁니다. 운영은 `https://autolog-api.aihub.io.kr/api/v1`. 예시는 `dashboard/.env.example`.
- `AUTOLOG_SERVER_URL`: 개발 서버 프록시 대상. 기본 `http://localhost:8080`.
- 개발 서버는 `http://localhost:5173`으로 접속합니다. 프록시가 브라우저의 `Origin`을 그대로 넘기므로, 서버 `CORS_ALLOWED_ORIGINS`에 없는 주소(예: `127.0.0.1:5173`)로 열면 403이 납니다.
- 운영: `https://autolog.aihub.io.kr` (Cloudflare Pages 프로젝트 `autolog`, 기본 도메인 `autolog-5wr.pages.dev`). main에 `dashboard/` 변경을 푸시하면 자동 배포됩니다. 빌드 설정은 [deploy/README.md](deploy/README.md#cloudflare-pages와-차량-앱).

### 차량 앱

```sh
cd app
./gradlew :car:assembleDebug :car:testDebugUnitTest :car:lintDebug
./gradlew :car:installDebug         # 실행 중인 Pleos Connect AVD에 설치
```

Android Studio에서 `app/` 폴더를 열어도 됩니다. Android SDK 경로는 `app/local.properties`의 `sdk.dir` (이 PC는 `~/Library/Android/sdk`) 또는 `ANDROID_HOME`으로 지정합니다. debug APK는 `app/car/build/outputs/apk/debug/`에 생성됩니다. 차량 앱 단위 테스트 97개·lint 오류 0 **확인됨 (2026-11-02)**.

화면은 [Pleos Connect 디자인 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)의 색 토큰·글자 위계·버튼·팝업 기준을 적용하고 시스템 라이트/다크 설정을 따릅니다. 앱 창 너비 1200dp 이상은 두 단, 그보다 좁은 창·세로 분할 창은 한 단으로 배치합니다. 이번 달 요약은 운행 수·총 주행거리·업무사용비율(`(출퇴근 거리 + 업무 거리) ÷ 총 주행거리 × 100`)을 표시하며 음성 답변 대기 트립은 제외합니다. 다시 등록 화면에서 시스템 뒤로가기는 취소 후 홈 복귀입니다. 라이트/다크·미확인 3버튼·이용자 선택/다시 등록 팝업·등록 코드·세로 분할 창 정상 동작은 사용자 AVD 실측으로 **확인됨 (2026-11-02, 3dfe60f·08e850e)**.

- release는 API 호스트 `https://autolog-api.aihub.io.kr`, P 유지 종료 확정 180초입니다. debug는 `http://10.0.2.2:8080`, 30초입니다.
- debug 전용 `ProbeActivity`와 `filesDir/voice_answer.pcm`(16kHz·16bit·mono)을 마이크 대신 주입하는 검증 기능이 있습니다. 파일은 한 번 읽어 지우고 STT 오류 재질문에는 캐시한 같은 PCM을 한 번 더 사용합니다. PCM 주입과 호스트 마이크 검증은 구분합니다.
- Pleos AVD에서 화면 시작 Foreground Service(`specialUse|microphone`, `types=0x40000080`)와 트립 저장·화면 수정 직후 WorkManager 로컬 서버 업로드는 **확인됨 (2026-11-02)**. AVD 재부팅 후에는 BOOT_COMPLETED로 `specialUse`만 실행(`types=0x40000000`). 앱 uid 녹음 AppOp은 `mode=1`(무시)이지만 Pleos 음성 서비스가 자기 uid(`ai.pleos.playground.caas`)로 녹음 시작·허용, PCM 응답 인식·확정까지 **확인됨 (2026-11-02)**. 실제 마이크 음성 입력은 **미확인**입니다.
- 페어링 → 동의·집 등록 → 12.4km 업무 운행 음성 확인 → 서버·대시보드 표시, 무응답 마스킹 → 차량 화면 개인 수정 → 서버 수정 이력, 이번 달 CSV 저장까지 **확인됨 (2026-11-02)**.
- 주행 시작에도 동기화합니다. 직전 추가한 거래처 '그린물류' 6.8km 인식·업무 저장, 관리자 미확인 운행 확정의 `tripUpdates` 수신·차량 목록 반영, 비활성 직원 선택 해제·이용자 없는 업로드와 과거 `FAILED(DRIVER_NOT_FOUND)` 재전송, 상단 빨간 '전송 실패 N건' 표시는 **확인됨 (2026-11-02)**.
- 즉시 동기화는 `REPLACE`입니다. 서버 중단 중 '전송 대기 N건' → 서버 복구 후 다음 주행 시작·저장 요청 때 백오프 대기 작업을 대체해 바로 전송합니다. 이전 `APPEND_OR_REPLACE`의 약 3분 지연이 해소된 것을 **확인됨 (2026-11-02)**.
- 새 운행은 최근·미확인 목록 맨 위에 바로 표시됩니다. '확인이 필요한 운행' 제목은 0건이면 일반색, 1건 이상이면 경고색입니다. 화면 '업무'는 등록 거래처 도착이면 `BUSINESS/MANUAL/VISIT`(기록부 비고 '거래처 방문 / 장소명'), 그 외는 `OTHER`입니다 (**확인됨 2026-11-02**).
- debug 30초 미만 정차 후 재출발은 7.5km 한 건으로 이어 기록, 주행 중 프로세스 강제 종료 후 `START_STICKY` 재시작·출발 계기판 값 유지로 9.6km 한 건 기록, '차량 다시 등록'의 새 코드·`replacedDeviceId`·이용자 선택 해제·새 토큰 업로드까지 **확인됨 (2026-11-02)**.
- CSV는 14개 열의 UTF-8 BOM 파일이며 수식처럼 보이는 문자열 앞에 `'`를 붙입니다. API 29 이상은 다운로드 폴더, API 28은 앱 전용 다운로드 폴더에 저장합니다.

화면과 서비스는 `AppContainer.voice` 하나를 공유하고 음성 작업을 직렬화합니다. TTS를 각각 만들 때 완료 콜백이 나중 인스턴스로만 가 질문 후 20초 동안 듣지 못하던 문제를 수정했고, 질문 끝 → 듣기 시작 5ms를 **확인됨 (2026-11-02)**으로 기록했습니다.

STT 완료 조각이 없으면 마지막 부분 결과를 사용합니다 (부분 결과 3초 무변화·오류·10초 종료). 완료·부분 결과가 모두 없을 때 오류 콜백은 한 번 재질문, 콜백 없는 크래시·무응답은 미확인으로 남깁니다. 재질문 경로는 단위 테스트 검증이며 에뮬레이터 실측은 아직 **미확인**입니다. "네, 맞아요"에서도 음성 서비스 SIGSEGV가 발생했습니다 (**확인됨 2026-11-02, 누적 약 12회 중 3회**). 긴 응답은 짧은 "업무"의 오인식을 줄이기 위한 권장이며 크래시를 막지는 못합니다. 응답 전체 '너무'의 업무 보정은 [계약 §3](docs/contracts/car-app.md#3-음성용도-추정주행-제한)을 따릅니다.

출근·퇴근 추정은 평일 06:00~10:59·16:00~22:59 KST 기준이므로 PT 리허설에서 해당 조건을 확인합니다. release APK 파일명·서명키는 미해결입니다.

실제 패키지·저장·동기화·음성·권한·CSV 열·남은 구현은 [차량 앱 구현 계약](docs/contracts/car-app.md)에 정리했습니다. 화면은 DO 미선언으로 정차 중에만 사용하며 주행 중 차단은 플랫폼이 담당합니다. 도착 확인은 음성으로 처리하고 실패하면 미확인으로 남겨 정차 중 3버튼으로 정리합니다.

## 테스트 환경·필요한 설정

- JDK 25(서버) / JDK 17 이상(차량 앱 Gradle 실행)
- Node.js 24 (대시보드 테스트의 jsdom 30은 Node 22.22.2 이상 또는 24.15.0 이상)
- Android SDK Platform 37 (compileSdk 37, targetSdk 35, minSdk 28). 설치 패키지명은 `platforms;android-37.0` (**확인됨 2026-11-02**). SDK 경로는 `app/local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 지정합니다. 이 PC의 SDK 경로는 `~/Library/Android/sdk` (`app/local.properties` 기준, **확인됨 2026-11-02**).
- **Pleos Connect 전용 AVD** (Emulator 2.1.0, Android 14/API 34). 일반 Android·Automotive 이미지에는 Pleos Vehicle Service가 없어 Vehicle SDK가 동작하지 않습니다.
- Docker (로컬 PostgreSQL)

Pleos Connect Emulator 설정과 이 PC의 이미지 설치·AVD 생성·Virtual Console 연결 완료는 **확인됨 (2026-11-02)**. 0단계 실측 환경은 Emulator 2.1.0(arm64), AVD 이름 `Pleos_Connect_v2.1`, 기기 프로필 id `Pleos_Connect_v2_0`입니다.

- Apple Silicon(arm64) 이미지가 있습니다. 패키지는 `system-images;android-34;pleos_car_emulator;arm64-v8a`, 다운로드 크기는 약 4.7GB입니다.
- SDK Update Site: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`. Android Studio SDK Manager에 등록하거나 `~/.android/repositories.cfg`에 사이트를 등록해 명령줄 `sdkmanager`로 설치할 수 있습니다.
- AVD는 **Cold boot**, RAM **4GB**로 설정합니다. Quick boot에서는 VHAL을 사용할 수 없습니다.
- Virtual Console은 `adb forward tcp:8088 tcp:8088` 실행 후 [로컬 웹 페이지](http://127.0.0.1:8088/)를 브라우저에서 한 번 열어야 활성화됩니다. 열기 전 HTTP 쓰기는 `{"error":"emulator_inactive"}`입니다. 활성화 후 `PUT /vehicle/gear?gear=Drive`(`Park` / `Reverse` / `Neutral`도 가능), `PUT /vehicle/odometer?traveled_distance_km=12.4`로 제어할 수 있습니다. Odometer 소수 입력이 가능하며 화면 슬라이더만 1km 단위입니다 (**확인됨 2026-11-02**).
- 기어 P/D·Odometer km 값·알림, FusedLocation 위치 수신, TTS ON_DEVICE 재생, STT ON_DEVICE의 `sendAudio` PCM 한국어 인식은 **확인됨 (2026-11-02)**. 호스트 마이크 경로·macOS 마이크 권한은 미확인입니다. TTS 3.3.1 리스너는 `initialize` 성공 콜백 이후 등록하고, STT는 EPD별 `completed=true` 조각을 모아 해석합니다.
- 에뮬레이터 시간대는 환경에 따라 `America/Los_Angeles`일 수 있습니다 (**확인됨 2026-11-02**). 용도 추정과 CSV 사용일자·시각은 `Asia/Seoul`을 명시합니다 (코드 확인됨 2026-11-02). 주말 출퇴근 추정은 하지 않습니다.

공식 안내: [Pleos Connect Emulator 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator). Vehicle SDK 2.3.2의 기어 API·권한과 TTS/STT SDK 3.3.1 생성자·모드·권한은 [아키텍처의 SDK 확인 사항](docs/contracts/architecture.md#5-sdk-확인-사항)에 정리했습니다.

## APK 위치

`apk/` 폴더 또는 GitHub Releases

## Oracle 배포 연결

상세 설정과 최초 실행·로그 확인·롤백 방법은 [배포용 README](deploy/README.md)에 정리했습니다. 기존 `server/compose.yaml`은 로컬 DB용이며, 운영은 `deploy/compose.yaml`을 사용합니다.

- API 주소: `https://autolog-api.aihub.io.kr`
- 공통 Nginx·인증서: `shared-infrastructure` 리포에서 관리
- 이 리포의 운영 Compose 프로젝트 이름: `autolog`
- API 서비스 이름·수신 주소: `autolog-api`, 컨테이너 내부 `0.0.0.0:8080`
- API는 외부 Docker 네트워크 `shared-proxy`와 자체 DB 네트워크에 연결합니다. 공통 Nginx가 `autolog-api:8080`으로 전달하므로 API·DB의 호스트 포트 매핑은 필요 없습니다.
- DB·볼륨·이미지·환경변수·배포 Actions는 이 리포에서 관리합니다. 대시보드는 Cloudflare Pages에서 배포합니다.
- `/nginx-health`는 도메인·Nginx 연결 상태를 확인하는 주소입니다. API·DB 상태는 `/actuator/health`로 확인합니다.

## 계약 문서

[`docs/contracts/`](docs/contracts/README.md)에 서버·차량 앱·대시보드가 함께 지키는 API·DB·동작 규칙을 둡니다. 구현은 이 문서를 기준으로 하고, 바꿀 때는 문서부터 고칩니다.

## Pleos 개발 문서

Pleos SDK·앱 개발 문서는 저장소에 포함하지 않습니다. 개발할 때는 [Pleos 공식 개발자 문서](https://document.pleos.ai/docs/pleos-only/getting-started)를 참고합니다.
