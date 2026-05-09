#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    log "필수 명령어가 없습니다: $cmd"
    exit 1
  fi
}

resolve_file_owner_uid_gid() {
  local file_path="$1"

  # 리눅스(GNU stat)와 macOS(BSD stat) 모두 대응한다.
  if stat --version >/dev/null 2>&1; then
    local uid gid
    uid="$(stat -c '%u' "$file_path")"
    gid="$(stat -c '%g' "$file_path")"
    echo "${uid}:${gid}"
    return
  fi

  local uid gid
  uid="$(stat -f '%u' "$file_path")"
  gid="$(stat -f '%g' "$file_path")"
  echo "${uid}:${gid}"
}

is_container_running() {
  local container_name="$1"
  docker ps --format '{{.Names}}' | grep -q "^${container_name}$"
}

APP_NAME="${APP_NAME:-bridgework-backend}"
APP_ROOT="${APP_ROOT:-$HOME/bridgework}"
CONFIG_FILE="${CONFIG_FILE:-$APP_ROOT/application-prod.yml}"
STATE_DIR="${STATE_DIR:-$APP_ROOT/state}"
ACTIVE_SLOT_FILE="${ACTIVE_SLOT_FILE:-$STATE_DIR/active_slot}"
DOCKER_NETWORK="${DOCKER_NETWORK:-bridgework-network}"
리UPSTREAM_SWITCH_SCRIPT="${UPSTREAM_SWITCH_SCRIPT:-$HOME/bridgework-infra/deploy/spring_blue_green_switch.sh}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-bridgework-redis}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7.2-alpine}"
REDIS_VOLUME="${REDIS_VOLUME:-bridgework-redis-data}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

BLUE_PORT="${BLUE_PORT:-18080}"
GREEN_PORT="${GREEN_PORT:-18081}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"

HEALTH_ENDPOINT="${HEALTH_ENDPOINT:-/actuator/health}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
HEALTH_CONNECT_TIMEOUT_SECONDS="${HEALTH_CONNECT_TIMEOUT_SECONDS:-2}"
HEALTH_REQUEST_TIMEOUT_SECONDS="${HEALTH_REQUEST_TIMEOUT_SECONDS:-3}"

IMAGE_URI="${IMAGE_URI:-}"
IMAGE_RETENTION_COUNT="${IMAGE_RETENTION_COUNT:-5}"
if [[ -z "$IMAGE_URI" ]]; then
  log "IMAGE_URI 환경변수는 필수입니다."
  exit 1
fi
if [[ -z "$REDIS_PASSWORD" ]]; then
  log "REDIS_PASSWORD 환경변수는 필수입니다."
  exit 1
fi

require_command docker
require_command curl

if [[ ! -f "$CONFIG_FILE" ]]; then
  log "외부 설정 파일이 없습니다: $CONFIG_FILE"
  exit 1
fi

# 설정 파일 소유자와 동일한 uid/gid로 앱을 실행해 600 권한 파일도 읽을 수 있게 한다.
APP_CONTAINER_USER="${APP_CONTAINER_USER:-$(resolve_file_owner_uid_gid "$CONFIG_FILE")}"
if [[ -z "$APP_CONTAINER_USER" ]]; then
  log "설정 파일 소유자(uid:gid) 조회에 실패했습니다: $CONFIG_FILE"
  exit 1
fi
log "애플리케이션 컨테이너 실행 사용자(uid:gid): $APP_CONTAINER_USER"

mkdir -p "$STATE_DIR"

if ! docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1; then
  log "도커 네트워크 생성: $DOCKER_NETWORK"
  docker network create "$DOCKER_NETWORK" >/dev/null
fi

if ! docker volume inspect "$REDIS_VOLUME" >/dev/null 2>&1; then
  log "Redis 볼륨 생성: $REDIS_VOLUME"
  docker volume create "$REDIS_VOLUME" >/dev/null
fi

# Redis 보안 설정(requirepass) 일관성을 위해 배포 시마다 재생성한다.
if docker ps -a --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER_NAME}$"; then
  log "기존 Redis 컨테이너 재생성: $REDIS_CONTAINER_NAME"
  docker rm -f "$REDIS_CONTAINER_NAME" >/dev/null 2>&1 || true
fi

log "Redis 컨테이너 실행: $REDIS_CONTAINER_NAME"
docker run -d \
  --name "$REDIS_CONTAINER_NAME" \
  --restart unless-stopped \
  --network "$DOCKER_NETWORK" \
  -v "${REDIS_VOLUME}:/data" \
  "$REDIS_IMAGE" \
  redis-server \
  --appendonly yes \
  --save 60 1000 \
  --loglevel warning \
  --requirepass "$REDIS_PASSWORD" >/dev/null

wait_for_redis() {
  local timeout_seconds="$1"
  local interval_seconds=1
  local waited=0

  while (( waited < timeout_seconds )); do
    if docker exec "$REDIS_CONTAINER_NAME" redis-cli -a "$REDIS_PASSWORD" ping | grep -q "PONG"; then
      return 0
    fi
    sleep "$interval_seconds"
    waited=$((waited + interval_seconds))
  done

  return 1
}

cleanup_old_app_images() {
  local image_ref="$1"
  local keep_count="$2"
  local repository running_image_ids candidate_ids deleted=0
  repository="${image_ref%%:*}"

  if [[ -z "$repository" || "$repository" == "$image_ref" ]]; then
    log "이미지 정리를 건너뜁니다. 저장소 파싱 실패: $image_ref"
    return 0
  fi

  if ! [[ "$keep_count" =~ ^[0-9]+$ ]] || (( keep_count < 1 )); then
    keep_count=5
  fi

  # 실행 중 컨테이너가 참조한 이미지는 삭제 대상에서 제외한다.
  running_image_ids="$(docker ps --format '{{.Image}}' | xargs -r docker image inspect --format '{{.Id}}' 2>/dev/null | sort -u || true)"
  candidate_ids="$(
    docker image ls "$repository" --format '{{.ID}}' | awk '!seen[$0]++' | tail -n +"$((keep_count + 1))"
  )"

  if [[ -z "$candidate_ids" ]]; then
    log "이미지 정리 대상이 없습니다. repository=$repository keep=$keep_count"
    return 0
  fi

  while IFS= read -r image_id; do
    [[ -z "$image_id" ]] && continue
    if grep -q "$image_id" <<<"$running_image_ids"; then
      continue
    fi
    if docker image rm "$image_id" >/dev/null 2>&1; then
      deleted=$((deleted + 1))
    fi
  done <<<"$candidate_ids"

  # dangling 레이어는 별도로 정리해 overlay 공간을 회수한다.
  docker image prune -f >/dev/null 2>&1 || true
  log "이미지 정리 완료: repository=$repository deleted=$deleted keep=$keep_count"
}

if ! wait_for_redis 30; then
  log "Redis 헬스체크 실패. 배포를 중단합니다."
  docker logs --tail 120 "$REDIS_CONTAINER_NAME" || true
  exit 1
fi

BLUE_CONTAINER="${APP_NAME}-blue"
GREEN_CONTAINER="${APP_NAME}-green"

resolve_current_slot() {
  if [[ -f "$ACTIVE_SLOT_FILE" ]]; then
    local slot
    slot="$(tr -d '[:space:]' < "$ACTIVE_SLOT_FILE")"
    if [[ "$slot" == "blue" || "$slot" == "green" ]]; then
      echo "$slot"
      return
    fi
  fi

  # 상태 파일이 없으면 현재 실행 중인 컨테이너를 기준으로 슬롯을 판별한다.
  if is_container_running "$BLUE_CONTAINER"; then
    echo "blue"
    return
  fi

  if is_container_running "$GREEN_CONTAINER"; then
    echo "green"
    return
  fi

  echo "blue"
}

CURRENT_SLOT="$(resolve_current_slot)"
if [[ "$CURRENT_SLOT" == "blue" ]]; then
  TARGET_SLOT="green"
  TARGET_CONTAINER="$GREEN_CONTAINER"
  TARGET_PORT="$GREEN_PORT"
  OLD_CONTAINER="$BLUE_CONTAINER"
else
  TARGET_SLOT="blue"
  TARGET_CONTAINER="$BLUE_CONTAINER"
  TARGET_PORT="$BLUE_PORT"
  OLD_CONTAINER="$GREEN_CONTAINER"
fi

# 비정상 종료 등으로 두 슬롯이 동시에 떠있으면 현재 활성 슬롯 기준으로 비활성 슬롯을 정리한다.
if is_container_running "$BLUE_CONTAINER" && is_container_running "$GREEN_CONTAINER"; then
  if [[ "$CURRENT_SLOT" == "blue" ]]; then
    log "이중 실행 감지. 비활성 슬롯 정리: $GREEN_CONTAINER"
    docker rm -f "$GREEN_CONTAINER" >/dev/null 2>&1 || true
  else
    log "이중 실행 감지. 비활성 슬롯 정리: $BLUE_CONTAINER"
    docker rm -f "$BLUE_CONTAINER" >/dev/null 2>&1 || true
  fi
fi

log "현재 슬롯: $CURRENT_SLOT"
log "대상 슬롯: $TARGET_SLOT (container=$TARGET_CONTAINER, hostPort=$TARGET_PORT)"
log "이미지 pull: $IMAGE_URI"
docker pull "$IMAGE_URI" >/dev/null

# 대상 슬롯 컨테이너가 기존에 있으면 제거 후 새 버전을 올린다.
docker rm -f "$TARGET_CONTAINER" >/dev/null 2>&1 || true

log "새 컨테이너 실행: $TARGET_CONTAINER"
docker run -d \
  --name "$TARGET_CONTAINER" \
  --restart no \
  --network "$DOCKER_NETWORK" \
  --add-host host.docker.internal:host-gateway \
  --user "$APP_CONTAINER_USER" \
  -v "${CONFIG_FILE}:/app/config/application-prod.yml:ro" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION="optional:file:/app/config/" \
  -e SERVER_PORT="$CONTAINER_PORT" \
  -e TZ="${TZ:-UTC}" \
  -p "${TARGET_PORT}:${CONTAINER_PORT}" \
  "$IMAGE_URI" >/dev/null

wait_for_health() {
  local url="$1"
  local timeout="$2"
  local interval="$3"
  local container_name="$4"
  local waited=0

  while (( waited < timeout )); do
    # 대상 컨테이너가 먼저 죽었는지 확인해 불필요한 대기를 막는다.
    if ! docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
      return 2
    fi

    # curl 기본 타임아웃은 무한대라서 각 요청에 상한을 둔다.
    if curl \
      --connect-timeout "$HEALTH_CONNECT_TIMEOUT_SECONDS" \
      --max-time "$HEALTH_REQUEST_TIMEOUT_SECONDS" \
      -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$interval"
    waited=$((waited + interval))
  done

  return 1
}

HEALTH_URL="http://127.0.0.1:${TARGET_PORT}${HEALTH_ENDPOINT}"
log "헬스체크 대기: $HEALTH_URL"
if ! wait_for_health "$HEALTH_URL" "$HEALTH_TIMEOUT_SECONDS" "$HEALTH_INTERVAL_SECONDS" "$TARGET_CONTAINER"; then
  log "헬스체크 실패. 새 컨테이너를 제거하고 배포를 중단합니다."
  docker ps -a --filter "name=${TARGET_CONTAINER}" --format 'table {{.Names}}\t{{.Status}}' || true
  docker logs --tail 120 "$TARGET_CONTAINER" || true
  docker rm -f "$TARGET_CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi

if [[ ! -f "$UPSTREAM_SWITCH_SCRIPT" ]]; then
  log "공통 인프라 전환 스크립트가 없습니다: $UPSTREAM_SWITCH_SCRIPT"
  docker rm -f "$TARGET_CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi

log "공통 인프라 전환 스크립트 실행: ${UPSTREAM_SWITCH_SCRIPT} ${TARGET_SLOT}"
if ! bash "$UPSTREAM_SWITCH_SCRIPT" "$TARGET_SLOT"; then
  log "공통 인프라 전환 스크립트 실행 실패"
  docker rm -f "$TARGET_CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi

echo "$TARGET_SLOT" > "$ACTIVE_SLOT_FILE"

# 트래픽 전환 이후에만 자동 재시작을 활성화한다.
docker update --restart unless-stopped "$TARGET_CONTAINER" >/dev/null

if docker ps -a --format '{{.Names}}' | grep -q "^${OLD_CONTAINER}$"; then
  log "이전 슬롯 컨테이너 정리: $OLD_CONTAINER"
  docker rm -f "$OLD_CONTAINER" >/dev/null 2>&1 || true
fi

log "배포 완료: active_slot=$TARGET_SLOT"
cleanup_old_app_images "$IMAGE_URI" "$IMAGE_RETENTION_COUNT"
