#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NGINX_CONF_DIR="${NGINX_CONF_DIR:-/etc/nginx/conf.d}"

CONF_TARGET="${NGINX_CONF_DIR}/bridgework.conf"
UPSTREAM_TARGET="${NGINX_CONF_DIR}/bridgework-upstream.inc"

if [[ ! -d "$NGINX_CONF_DIR" ]]; then
  echo "nginx conf 디렉터리가 없습니다: $NGINX_CONF_DIR"
  exit 1
fi

# 기존 파일이 있으면 보존하고, 없을 때만 기본 템플릿을 설치한다.
if [[ ! -f "$CONF_TARGET" ]]; then
  sudo cp "$SCRIPT_DIR/nginx/bridgework.conf" "$CONF_TARGET"
fi

if [[ ! -f "$UPSTREAM_TARGET" ]]; then
  sudo cp "$SCRIPT_DIR/nginx/bridgework-upstream.inc" "$UPSTREAM_TARGET"
fi

sudo nginx -t
sudo systemctl reload nginx

echo "nginx 설정 적용 완료: $CONF_TARGET"
echo "upstream 파일: $UPSTREAM_TARGET"
