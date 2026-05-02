#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
resolve_nginx_conf_dir() {
  local nginx_main_conf="${NGINX_MAIN_CONF:-/etc/nginx/nginx.conf}"

  # 주요 배포 환경의 include 경로를 우선 감지한다.
  if [[ -f "$nginx_main_conf" ]]; then
    if grep -Eq "^[[:space:]]*include[[:space:]]+/etc/nginx/conf\\.d/\\*\\.conf;" "$nginx_main_conf"; then
      echo "/etc/nginx/conf.d"
      return
    fi

    if grep -Eq "^[[:space:]]*include[[:space:]]+/etc/nginx/sites-enabled/\\*([^;]*)?;" "$nginx_main_conf"; then
      echo "/etc/nginx/sites-enabled"
      return
    fi
  fi

  if [[ -d "/etc/nginx/conf.d" ]]; then
    echo "/etc/nginx/conf.d"
    return
  fi

  if [[ -d "/etc/nginx/sites-enabled" ]]; then
    echo "/etc/nginx/sites-enabled"
    return
  fi

  # 감지 실패 시 표준 경로를 기본값으로 사용한다.
  echo "/etc/nginx/conf.d"
}

NGINX_CONF_DIR="${NGINX_CONF_DIR:-$(resolve_nginx_conf_dir)}"

CONF_TARGET="${NGINX_CONF_DIR}/bridgework.conf"
UPSTREAM_TARGET="${NGINX_CONF_DIR}/bridgework-upstream.inc"

if [[ ! -d "$NGINX_CONF_DIR" ]]; then
  sudo mkdir -p "$NGINX_CONF_DIR"
fi

# 라우팅 정책은 배포 리포 설정을 기준으로 항상 동기화한다.
sudo cp "$SCRIPT_DIR/nginx/bridgework.conf" "$CONF_TARGET"

# 서버마다 nginx conf 경로가 다를 수 있으므로 include 경로를 실제 경로로 고정한다.
escaped_upstream_target="$(printf '%s\n' "$UPSTREAM_TARGET" | sed 's/[\\/&]/\\&/g')"
sudo sed -i "s|^include .*bridgework-upstream\\.inc;|include ${escaped_upstream_target};|" "$CONF_TARGET"

if [[ ! -f "$UPSTREAM_TARGET" ]]; then
  sudo cp "$SCRIPT_DIR/nginx/bridgework-upstream.inc" "$UPSTREAM_TARGET"
fi

sudo nginx -t
sudo systemctl reload nginx

echo "nginx 설정 적용 완료: $CONF_TARGET"
echo "upstream 파일: $UPSTREAM_TARGET"
