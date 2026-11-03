# 개발·실행 안내

> [서비스 소개](../../README.md) · [기술 문서 목차](README.md)

심사용 서비스 흐름은 서비스 소개에, 개발 환경에서의 실행·검증 방법은 이 문서에 정리합니다. 체험 로그인 정보와 비밀값은 대회 제출 문서·개별 환경에서 관리합니다.

## 준비 환경

| 구성 | 필요 환경 |
| --- | --- |
| 차량 앱 | JDK 21, Android SDK Platform 37, Pleos Connect 전용 AVD |
| 서버 | JDK 25, Docker/PostgreSQL |
| 대시보드 | Node.js 24.15 이상 권장 |

차량 앱은 minSdk 28·targetSdk 35입니다. 일반 Android·Automotive 이미지에는 Pleos Vehicle Service가 없으므로 전용 AVD가 필요합니다. Android SDK 경로는 `app/local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 지정합니다.

## 서버

```sh
cd server
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬 API는 `http://localhost:8080`입니다. `local` 프로필은 개발용 DB·JWT·CORS 설정을 사용합니다. 관리자 정보는 개발 환경의 `SEED_ADMIN_LOGIN_ID`·`SEED_ADMIN_PASSWORD`로 지정합니다. 운영 환경변수는 [서버 설정](config.md)을 참고하세요.

```sh
./gradlew test
```

서버 테스트는 Testcontainers로 PostgreSQL을 실행하므로 Docker가 필요합니다. 직접 준비한 테스트 DB를 쓰려면 `AUTOLOG_TEST_DB_URL`·`AUTOLOG_TEST_DB_USERNAME`·`AUTOLOG_TEST_DB_PASSWORD`를 지정합니다. JaCoCo 보고서는 `server/build/reports/jacoco/test/html/index.html`에 생성됩니다.

## 대시보드

```sh
cd dashboard
npm ci
npm run dev
```

`http://localhost:5173`으로 접속합니다. 기본 `/api/v1` 요청은 `http://localhost:8080`으로 프록시됩니다. 서버의 CORS 허용 목록과 동일한 주소로 접속해야 합니다.

| 설정 | 역할 |
| --- | --- |
| `AUTOLOG_SERVER_URL` | 로컬 개발 프록시 대상, 기본 `http://localhost:8080` |
| `VITE_API_BASE_URL` | 빌드에 포함할 API 주소. 운영은 `https://autolog-api.aihub.io.kr/api/v1` |

```sh
npm run lint
npm run test
npm run build
```

정적 빌드 결과는 `dashboard/dist/`입니다. `VITE_*` 값은 브라우저에 공개되므로 비밀값을 넣지 않습니다.

## 차량 앱

```sh
cd app
./gradlew :car:assembleDebug :car:testDebugUnitTest :car:lintDebug
./gradlew :car:installDebug
```

로컬 서버용 debug는 `http://10.0.2.2:8080`으로 연결합니다. 배포 서버와 연결하려면 다음 빌드를 사용하고, 새 앱에 표시된 코드로 대시보드에서 페어링합니다.

```sh
./gradlew :car:assembleRemoteDebug :car:lintRemoteDebug
./gradlew :car:installRemoteDebug
```

운영 테스트 앱은 'AutoLog 서버 테스트'(`com.autolog.app.remote`)로 별도 설치됩니다. APK는 `app/car/build/outputs/apk/{빌드유형}/`에 생성됩니다. 빌드별 주소·주차 대기 시간은 [차량 앱](car-app.md#5-빌드csv)을 참고하세요.

### Pleos 에뮬레이터

[공식 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 AVD를 준비합니다. 검증 환경은 Pleos Connect Emulator 2.1.0·Android 14/API 34·arm64이며 Cold boot·RAM 4GB를 사용했습니다.

```sh
adb forward tcp:8088 tcp:8088
```

브라우저에서 `http://127.0.0.1:8088/`을 열어 Virtual Console을 활성화한 뒤 기어·계기판을 조작합니다. HTTP 입력 예시는 다음과 같습니다.

```sh
curl -X PUT 'http://127.0.0.1:8088/vehicle/gear?gear=Drive'
curl -X PUT 'http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=1012.4'
curl -X PUT 'http://127.0.0.1:8088/vehicle/gear?gear=Park'
```

앱의 날짜·용도 추정은 기기 시간대와 관계없이 `Asia/Seoul`을 사용합니다. 실제 마이크와 통신 복구의 확인 범위는 [검증 결과](validation.md)에 있습니다.

## 아이콘

차량 앱의 adaptive icon, [스토어용 512px PNG](../../apk/app-icon-512.png), 대시보드의 `LogoMark`·`favicon.svg`는 남색 `#002C5F`와 같은 계기판 바늘 도안을 사용합니다. 런처·스토어의 시스템 마스크에 따라 외곽 모양은 달라질 수 있습니다.
