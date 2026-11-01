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
- `./gradlew test`는 Testcontainers로 PostgreSQL을 띄우므로 Docker가 실행 중이어야 합니다.
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
- Cloudflare Pages: 루트 `dashboard`, 빌드 명령 `npm run build`, 출력 `dist`, 환경변수 `VITE_API_BASE_URL`. Pages 도메인은 서버 `CORS_ALLOWED_ORIGINS`에 추가합니다.

### 차량 앱

```sh
cd app
./gradlew :car:assembleDebug        # car/build/outputs/apk/debug/
./gradlew :car:installDebug         # 실행 중인 Pleos Connect AVD에 설치
./gradlew :car:testDebugUnitTest
```

Android Studio에서 `app/` 폴더를 열어도 됩니다. debug 빌드는 에뮬레이터에서 호스트의 로컬 서버(`http://10.0.2.2:8080`)에 연결합니다.

## 테스트 환경·필요한 설정

- JDK 25(서버) / JDK 17 이상(차량 앱 Gradle 실행)
- Node.js 24 (대시보드 테스트의 jsdom 30은 Node 22.22.2 이상 또는 24.15.0 이상)
- Android SDK Platform 37 (compileSdk 37, targetSdk 35, minSdk 28)
- **Pleos Connect 전용 AVD** (`Pleos_Connect_v2.1`, Android 14). 일반 Android·Automotive 이미지에는 Pleos Vehicle Service가 없어 Vehicle SDK가 동작하지 않습니다.
- 차량 상태는 Pleos Virtual Console로 시뮬레이션합니다.
- Docker (로컬 PostgreSQL)

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
