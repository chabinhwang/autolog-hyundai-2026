# 배포·운영 안내

> [서비스 소개](../README.md) · [서버 환경변수](../docs/contracts/config.md)

API·PostgreSQL은 Oracle VM, 대시보드는 Cloudflare Pages, 차량 앱은 APK로 배포합니다. 운영 비밀값과 체험 로그인 정보는 저장소 문서에 기록하지 않습니다.

## 운영 구성

| 항목 | 구성 |
| --- | --- |
| 대시보드 | `https://autolog.aihub.io.kr` · Pages 프로젝트 `autolog` |
| API | `https://autolog-api.aihub.io.kr` |
| API·DB 배포 위치 | 서버의 `~/autolog-hyundai-2026/`, Compose 프로젝트 `autolog` |
| 프록시 | 공통 Nginx → 외부 네트워크 `shared-proxy` → `autolog-api:8080` |
| DB | `autolog-db:5432`, 전용 네트워크·영속 볼륨 |

공통 Nginx·도메인·인증서는 별도 공통 인프라에서 관리합니다. API·DB는 호스트 포트를 공개하지 않습니다. 서비스 이름·내부 포트·공유 네트워크를 바꾸면 프록시 설정도 함께 맞춰야 합니다.

## 배포 파일

| 파일 | 역할 |
| --- | --- |
| [compose.yaml](compose.yaml) | API·DB·볼륨·healthcheck·자원/로그 제한 |
| [deploy.sh](deploy.sh) | 이미지 pull → 상태 확인 → 성공 버전 기록, 실패 시 이전 이미지 복구 |
| [env.example](env.example) | 서버 `.env` 작성용 변수 목록 |
| [서버 Dockerfile](../server/Dockerfile) | Java 25 기반 ARM64 서버 이미지 |
| [GitHub Actions](../.github/workflows/deploy-server.yml) | 테스트·이미지 발행·Oracle 배포 |

`server/compose.yaml`은 로컬 DB용이며 운영에서는 이 폴더의 `compose.yaml`을 사용합니다.

## 최초 설정

1. 서버에 Bash·Docker Engine/Compose·curl·flock을 준비하고 `shared-proxy` 네트워크에 공통 Nginx를 연결합니다.
2. `compose.yaml`·`deploy.sh`·`env.example`·이 안내를 배포 폴더에 복사합니다. 서버에서 `env.example`을 `.env`로 복사하고 권한을 `600`으로 제한합니다.
3. DB 연결·JWT 키·CORS와 필요한 시드 값을 [환경변수 계약](../docs/contracts/config.md)에 맞춰 채웁니다. DB 비밀번호·JWT 키는 각각 별도로 생성하고 `.env`는 Git에 넣지 않습니다.
4. GitHub Actions Secrets에 `SSH_HOST`·`SSH_USER`·`SSH_PRIVATE_KEY`를 설정합니다. SSH 호스트 공개키는 [.github/server-host-key.pub](../.github/server-host-key.pub)로 검증합니다.
5. Actions에서 `deploy=true`로 최초 배포한 뒤 `AUTOLOG_DEPLOY_ENABLED=true`를 설정하면 main의 `server/`·`deploy/` 변경이 자동 배포됩니다.

같은 사업자등록번호의 회사가 이미 있으면 시드가 기존 계정·비밀번호를 덮어쓰지 않습니다. 기존 DB의 비밀번호도 `.env` 수정만으로 변경되지 않습니다. dotenv 특수문자 값은 작은따옴표로 감싸고 `.env`를 셸 스크립트로 실행하지 않습니다.

## 자동·수동 배포

서버 CI는 배포 스크립트 검사·서버 테스트를 통과한 뒤 ARM64 이미지를 GHCR에 발행하고 digest로 버전을 고정합니다. 실제 배포에서는 `.env`·상태 파일·DB 볼륨을 보존하며 내부·공개 health를 확인합니다. PR은 테스트·이미지 빌드만 수행합니다.

서버에서 수동으로 배포하려면 발행된 40자리 커밋 SHA 또는 digest를 지정합니다. `latest`는 허용하지 않습니다.

```sh
cd ~/autolog-hyundai-2026
AUTOLOG_API_IMAGE='ghcr.io/chabinhwang/autolog-api:<40자리 커밋 SHA>' bash ./deploy.sh
```

비공개 이미지의 인증은 CI가 처리합니다. 수동 실행 시에는 읽기 권한 토큰을 표준입력으로 전달합니다.

```sh
read -rs GHCR_READ_TOKEN
printf '%s' "$GHCR_READ_TOKEN" | \
  AUTOLOG_GHCR_USERNAME=chabinhwang \
  AUTOLOG_API_IMAGE="$AUTOLOG_API_IMAGE" bash ./deploy.sh
unset GHCR_READ_TOKEN
```

이 경우 `AUTOLOG_API_IMAGE`는 미리 발행된 이미지 주소로 지정합니다. 임시 registry 설정과 배포 잠금은 `deploy.sh`가 관리합니다.

## 상태 확인·복구

```sh
cd ~/autolog-hyundai-2026
docker compose ps
docker compose logs --tail 100 autolog-api
curl -fsS https://autolog-api.aihub.io.kr/actuator/health
tail -n 10 .state/history.tsv
```

| 증상 | 확인할 항목 |
| --- | --- |
| `/nginx-health` 실패 | DNS·공통 Nginx·인증서 |
| Nginx 정상, API 실패 | API 기동·`shared-proxy`·서비스 이름·8080 |
| DB unhealthy | `.env`·DB 로그·디스크·볼륨 권한 |
| Flyway/JPA 오류 | 마이그레이션·DB 계약, 운영은 `ddl-auto: validate` |
| 브라우저만 실패 | API 주소·CORS 허용 출처·preflight |
| 이미지 pull 실패 | GHCR 읽기 권한·이미지 버전 |

성공 버전은 `.state/current-image`, 이전 버전은 `.state/previous-image`에 기록합니다. 이전 이미지로 되돌리려면 다음을 실행합니다.

```sh
AUTOLOG_API_IMAGE="$(cat .state/previous-image)" bash ./deploy.sh
```

이미지 롤백은 DB를 되돌리지 않습니다. 스키마 변경은 이전 버전과 호환되게 관리하고, 변경 전 백업합니다. 일반 배포·복구에서 DB 볼륨을 삭제하지 않습니다.

```sh
umask 077
mkdir -p backups
docker compose exec -T autolog-db sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "backups/autolog-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

## Cloudflare Pages와 차량 앱

Pages는 main의 `dashboard/*` 변경을 감시합니다. 루트는 `dashboard`, 빌드는 `npm ci && npm run build`, 출력은 `dist`입니다. 빌드 환경변수 `VITE_API_BASE_URL`은 `https://autolog-api.aihub.io.kr/api/v1`로 지정합니다.

서버 CORS에는 운영 대시보드와 필요한 Pages·개발 출처를 등록합니다. Pages 기본 도메인은 `https://autolog-5wr.pages.dev`입니다. `VITE_*` 값은 공개되므로 비밀값을 넣지 않습니다.

차량 앱의 release와 remoteDebug는 운영 API에 연결합니다. remoteDebug 설치·페어링은 [개발·실행 안내](../docs/contracts/development.md#차량-앱)를 참고하세요.

아래 검사는 로컬 저장소 루트에서 실제 서버에 접속하지 않고 실행합니다.

```sh
bash -n deploy/deploy.sh
node --test deploy/tests/deploy.test.mjs
```
