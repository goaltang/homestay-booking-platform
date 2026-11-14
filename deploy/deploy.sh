#!/usr/bin/env bash
# ============================================
# 生产部署脚本（在服务器仓库根目录执行）
# 用法：./deploy/deploy.sh
# ============================================
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE_FILE="docker-compose.prod.yml"

if [ ! -f .env ]; then
  echo "[deploy] 缺少 .env，请先 cp .env.prod.example .env 并填写真实值" >&2
  exit 1
fi

echo "[deploy] 1/5 拉取代码"
git pull --ff-only

echo "[deploy] 2/5 构建镜像"
docker compose -f "$COMPOSE_FILE" build

echo "[deploy] 3/5 启动服务"
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

echo "[deploy] 4/5 等待后端就绪"
for i in $(seq 1 60); do
  cid="$(docker compose -f "$COMPOSE_FILE" ps -q backend || true)"
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$cid" 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then
    echo "[deploy] backend healthy"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "[deploy] backend 未在 5 分钟内就绪，最近日志：" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail 80 backend >&2
    exit 1
  fi
  sleep 5
done

echo "[deploy] 5/5 清理旧镜像"
docker image prune -f

docker compose -f "$COMPOSE_FILE" ps
site_port="$(grep -E '^SITE_PORT=' .env | cut -d= -f2 || true)"
admin_port="$(grep -E '^ADMIN_PORT=' .env | cut -d= -f2 || true)"
echo "[deploy] 完成：用户端 http://<服务器IP>:${site_port:-80}，管理端 http://<服务器IP>:${admin_port:-8089}"
