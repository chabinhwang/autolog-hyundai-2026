#!/usr/bin/env bash
# Oracle에서 실행합니다. 배포 파일은 ~/autolog-hyundai-2026/에 평탄하게 복사됩니다.
# 사용: AUTOLOG_API_IMAGE=ghcr.io/chabinhwang/autolog-api@sha256:... bash ./deploy.sh
# 재실행: 이전 성공 이미지가 .state/current-image에 있으면 이미지 인자를 생략할 수 있습니다.
set -euo pipefail
umask 077

DEPLOY_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$DEPLOY_DIR"
ENV_FILE=${AUTOLOG_ENV_FILE:-$DEPLOY_DIR/.env}
HEALTH_URL=${AUTOLOG_HEALTH_URL:-https://autolog-api.aihub.io.kr/actuator/health}
WAIT_TIMEOUT=${AUTOLOG_WAIT_TIMEOUT:-180}
REGISTRY_CONFIG=

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
for tool in docker curl flock; do
  command -v "$tool" >/dev/null || fail "필요한 명령이 없습니다: $tool"
done
[[ -r "$ENV_FILE" ]] || fail "환경변수 파일이 없습니다. env.example을 $ENV_FILE 로 복사해 값을 채우세요."
[[ "$WAIT_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || fail 'AUTOLOG_WAIT_TIMEOUT은 양의 정수여야 합니다.'
docker compose version >/dev/null
compose_help=$(docker compose up --help)
[[ "$compose_help" == *'--wait-timeout'* ]] || fail 'Docker Compose의 --wait/--wait-timeout 지원 버전이 필요합니다.'
docker network inspect shared-proxy >/dev/null 2>&1 || fail 'shared-proxy가 없습니다. 공통 Nginx를 관리하는 shared-infrastructure 설정을 확인하세요.'

# GitHub concurrency는 리포별입니다. 여기서는 수동 실행까지 포함해 AutoLog 배포를 서버에서 직렬화합니다.
exec 9>"$DEPLOY_DIR/.deploy.lock"
flock -n 9 || fail '다른 AutoLog 배포가 실행 중입니다.'
mkdir -p .state

current_image=
if [[ -s .state/current-image ]]; then IFS= read -r current_image < .state/current-image; fi
AUTOLOG_API_IMAGE=${AUTOLOG_API_IMAGE:-$current_image}
[[ -n "$AUTOLOG_API_IMAGE" ]] || fail '첫 배포에는 AUTOLOG_API_IMAGE가 필요합니다. 커밋 SHA 태그 또는 digest를 지정하세요.'
# 이미지 출처와 인자 형식을 제한합니다. latest는 사용하지 않습니다.
if [[ ! "$AUTOLOG_API_IMAGE" =~ ^ghcr\.io/chabinhwang/autolog-api(@sha256:[a-f0-9]{64}|:[a-f0-9]{40})$ ]]; then
  fail 'AUTOLOG_API_IMAGE는 이 리포의 GHCR 이미지와 40자리 커밋 SHA 태그 또는 sha256 digest여야 합니다.'
fi
export AUTOLOG_API_IMAGE

compose() {
  docker compose --project-name autolog --project-directory "$DEPLOY_DIR" \
    --env-file "$ENV_FILE" -f "$DEPLOY_DIR/compose.yaml" "$@" </dev/null
}
# config를 그대로 출력하면 비밀번호·JWT 키가 노출됩니다. 검증 결과만 확인합니다.
compose config --quiet

cleanup() {
  if [[ -n "$REGISTRY_CONFIG" ]]; then
    # mktemp로 이번 실행에서 만든 경로만 정리합니다. 다른 리포의 ~/.docker 로그인과 분리됩니다.
    docker --config "$REGISTRY_CONFIG" logout ghcr.io >/dev/null 2>&1 || true
    rm -rf -- "$REGISTRY_CONFIG"
  fi
}
trap cleanup EXIT

if [[ -n "${AUTOLOG_GHCR_USERNAME:-}" ]]; then
  # CI는 GitHub 토큰을 SSH 표준입력으로 보냅니다. 인자/환경변수/로그에 토큰을 남기지 않습니다.
  # docker compose도 이 임시 DOCKER_CONFIG를 상속해 pull합니다.
  REGISTRY_CONFIG=$(mktemp -d "${TMPDIR:-/tmp}/autolog-ghcr.XXXXXXXX")
  export DOCKER_CONFIG="$REGISTRY_CONFIG"
  docker login ghcr.io --username "$AUTOLOG_GHCR_USERNAME" --password-stdin >/dev/null
fi

# 현재 실행 중인 이미지가 있으면 state 파일보다 실제 컨테이너를 복구 기준으로 삼습니다.
previous_image=$current_image
container_id=$(compose ps --all --quiet autolog-api)
if [[ -n "$container_id" ]]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$container_id")
fi

printf 'Pulling AutoLog API image: %s\n' "$AUTOLOG_API_IMAGE"
compose pull autolog-api
# 기존 DB 이미지는 자동 갱신하지 않습니다. 첫 설치에서만 가져옵니다.
# PostgreSQL 업데이트는 별도 백업·유지보수 작업으로 진행하세요.
if ! docker image inspect postgres:18 >/dev/null 2>&1; then compose pull autolog-db; fi

public_health() {
  local attempt
  # Nginx의 Docker DNS 재조회 간격(10초)과 컨테이너 교체 직후의 연결 지연을 허용합니다.
  for attempt in $(seq 1 12); do
    if curl --fail --silent --show-error --max-time 5 --output /dev/null "$HEALTH_URL"; then return 0; fi
    sleep 2
  done
  return 1
}

requested_image=$AUTOLOG_API_IMAGE
if compose up --detach --wait --wait-timeout "$WAIT_TIMEOUT" && public_health; then
  if [[ -n "$previous_image" && "$previous_image" != "$requested_image" ]]; then
    printf '%s\n' "$previous_image" > .state/previous-image
  fi
  printf '%s\n' "$requested_image" > .state/current-image.tmp
  mv .state/current-image.tmp .state/current-image
  printf '%s\tSUCCESS\t%s\n' "$(date -u +%FT%TZ)" "$requested_image" >> .state/history.tsv
  compose ps
  printf 'AutoLog deployment healthy: %s\n' "$HEALTH_URL"
  exit 0
fi

printf '%s\tFAILED\t%s\n' "$(date -u +%FT%TZ)" "$requested_image" >> .state/history.tsv
printf 'Deployment failed. Check deploy/README.md for server-side log commands.\n' >&2
# 실제 서버 로그에는 개인정보가 있을 수 있으므로 CI에 자동으로 쏟아내지 않습니다.
compose ps || true
if [[ -n "$previous_image" ]]; then
  printf 'Restoring the previous API image: %s\n' "$previous_image" >&2
  export AUTOLOG_API_IMAGE="$previous_image"
  if compose pull autolog-api && compose up --detach --wait --wait-timeout "$WAIT_TIMEOUT"; then
    printf 'Previous containers are healthy. Database schema/data were not rolled back.\n' >&2
  else
    printf 'Automatic image rollback failed; inspect the server before retrying.\n' >&2
  fi
else
  # 첫 설치 실패 시 DB·볼륨은 보존하고, 준비되지 않은 API만 중지합니다.
  compose stop autolog-api || true
fi
exit 1
