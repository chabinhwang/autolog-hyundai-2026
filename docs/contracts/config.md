# 서버 설정 (D12)

> [기술 문서 목차](README.md) · [개발·실행 안내](development.md) · [배포 안내](../../deploy/README.md)

서버 설정은 전부 환경변수로 받습니다. 비밀값은 저장소에 넣지 않고 운영 환경(배포 Actions secrets 또는 VM의 `.env`)에서만 관리합니다. 로컬 개발은 `application-local.yaml`의 개발용 값을 씁니다.

## 환경변수

| 이름 | 필수 | 예시 | 설명 |
| --- | --- | --- | --- |
| `DB_URL` | O | `jdbc:postgresql://autolog-db:5432/autolog` | PostgreSQL JDBC URL |
| `DB_USERNAME` | O | `autolog` | |
| `DB_PASSWORD` | O | (비밀) | |
| `PORT` | | `8080` | 기본 8080. 컨테이너 내부 수신 포트 |
| `JWT_SECRET` | O | (비밀, 32바이트 이상) | 관리자 JWT HS256 서명 키. 짧으면 서버 시작 실패 |
| `JWT_EXPIRES_HOURS` | | `12` | 기본 12 (D1) |
| `CORS_ALLOWED_ORIGINS` | O | `https://autolog.aihub.io.kr,https://autolog-5wr.pages.dev,http://localhost:5173` | 쉼표 구분. 운영 대시보드 주소와 Pages 기본 도메인 (D8) |
| `SEED_ORG_NAME` | | `오토로그 주식회사` | 시드 회사 상호명 |
| `SEED_BIZ_REG_NO` | | `123-45-67890` | 시드 회사 사업자등록번호 |
| `SEED_ADMIN_LOGIN_ID` | | (별도 지정) | 시드 관리자 아이디 (이메일 형식) |
| `SEED_ADMIN_PASSWORD` | | (비밀) | 시드 관리자 비밀번호. BCrypt로 저장 |
| `SEED_ADMIN_DISPLAY_NAME` | | `총무팀` | 기본값 `관리자` |
| `CONSENT_VERSION` | | `2026-11-01` | 기본 `2026-11-01`. 차량 설정에 전달하는 현재 동의 문서 버전 (D13) |

- 필수 값이 없으면 서버가 시작하지 않습니다 (설정 오류를 배포 직후에 발견).
- 로그에 비밀값·시드 비밀번호를 출력하지 않습니다.
- 운영 값을 넣는 방법과 Compose 전용 변수(`DB_NAME`, `API_MEMORY_LIMIT` 등)는 [deploy/README.md](../../deploy/README.md)와 `deploy/env.example`이 기준입니다. 위 변수는 모두 `deploy/env.example`·`deploy/compose.yaml`에 있습니다.
- 한글 시드 값(`SEED_ORG_NAME` 등)은 JVM이 UTF-8 로캘(`LANG=C.UTF-8` 등)에서 실행돼야 깨지지 않습니다. 서버 Docker 이미지는 `LANG=C.UTF-8`을 설정합니다.

## 대시보드 빌드 환경변수

대시보드는 정적 파일이라 값이 빌드 시점에 들어갑니다. 비밀값을 넣지 않습니다.

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `https://autolog-api.aihub.io.kr/api/v1` | `/api/v1`까지 포함한 API 주소. 비우면 `/api/v1`(개발 서버 프록시) |
| `AUTOLOG_SERVER_URL` | `http://localhost:8080` | `npm run dev` 프록시 대상. 기본 `http://localhost:8080` |

## 회사·관리자 시드

- 서버 시작 시 `SEED_*` 네 값(`SEED_ORG_NAME`, `SEED_BIZ_REG_NO`, `SEED_ADMIN_LOGIN_ID`, `SEED_ADMIN_PASSWORD`)이 모두 있으면 실행합니다. 하나라도 없으면 건너뜁니다.
- 같은 `SEED_BIZ_REG_NO`의 회사가 이미 있으면 아무것도 바꾸지 않습니다 (비밀번호 덮어쓰기 없음).
- `SEED_BIZ_REG_NO`가 `000-00-00000` 형식이 아니거나 `SEED_ADMIN_LOGIN_ID`가 이메일 형식이 아니면 서버가 시작하지 않습니다.
- 한 번에 회사 1곳만 시드합니다. 회사를 더 만들려면 값을 바꿔 다시 시작합니다. 심사용 회사·계정도 같은 방법으로 별도 생성합니다.
- 회사 추가·관리자 비밀번호 변경 API는 1차 범위 밖입니다.

## 프로필

| 프로필 | 용도 | DB |
| --- | --- | --- |
| (기본) | 운영 | 환경변수 |
| `local` | 로컬 개발 | `server/compose.yaml`의 PostgreSQL, 개발용 `JWT_SECRET`·CORS·시드 값 |
| `test` | 자동 테스트 | Testcontainers PostgreSQL (`jsonb` 때문에 H2 불가) |
