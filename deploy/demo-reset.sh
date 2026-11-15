#!/usr/bin/env bash
# ============================================
# 公开演示站每晚重置：把业务数据恢复为演示快照，保留管理员账号（含其当前密码）
#
# 只用于 .env 中配置了 DEMO_GUEST_USERNAME / DEMO_HOST_USERNAME 的演示站，
# 会删除并重建 homestay_db，所有访客产生的订单、评价、消息都会丢失。
#
# 手动执行：./deploy/demo-reset.sh --yes
# 定时执行（每天 03:30）：
#   30 3 * * * cd /opt/homestay3 && ./deploy/demo-reset.sh --yes >> /var/log/homestay-demo-reset.log 2>&1
# ============================================
set -euo pipefail

cd "$(dirname "$0")/.."
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE=(docker compose)

log() { echo "[demo-reset $(date '+%F %T')] $*"; }
fail() { echo "[demo-reset $(date '+%F %T')] $*" >&2; exit 1; }

[ "${1:-}" = "--yes" ] || fail "会清空并重建演示数据库，确认请加 --yes"
[ -f .env ] || fail "未找到 .env"

env_value() { { grep -E "^$1=" .env || true; } | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
DB_USER="$(env_value DB_USERNAME)"
DB_PASS="$(env_value DB_PASSWORD)"
DB_ROOT_PASS="$(env_value DB_ROOT_PASSWORD)"
REDIS_PASS="$(env_value REDIS_PASSWORD)"
[ -n "$DB_USER" ] && [ -n "$DB_PASS" ] && [ -n "$DB_ROOT_PASS" ] || fail ".env 中缺少数据库账号或密码"
if [ -z "$(env_value DEMO_GUEST_USERNAME)$(env_value DEMO_HOST_USERNAME)" ]; then
  fail ".env 未配置 DEMO_GUEST_USERNAME / DEMO_HOST_USERNAME，这不是演示站，拒绝重置"
fi

# 防止 cron 与手动执行重叠
exec 9>"${TMPDIR:-/tmp}/homestay-demo-reset.lock"
flock -n 9 || fail "已有重置任务在运行"

ADMINS_FILE="$(mktemp)"
chmod 600 "$ADMINS_FILE"
cleanup() {
  rm -f "$ADMINS_FILE"
  # 任何一步失败都尽量把后端拉起来，避免演示站整晚不可用
  "${COMPOSE[@]}" start backend >/dev/null 2>&1 || true
}
trap cleanup EXIT

export MYSQL_PWD="$DB_PASS"
db_query() {
  "${COMPOSE[@]}" exec -T -e MYSQL_PWD mysql mysql -u"$DB_USER" homestay_db -N -B -e "$1" | tr -d '\r'
}
root_query() {
  MYSQL_PWD="$DB_ROOT_PASS" "${COMPOSE[@]}" exec -T -e MYSQL_PWD mysql mysql -uroot -N -B -e "$1"
}
wait_backend() {
  for _ in $(seq 1 90); do
    if "${COMPOSE[@]}" exec -T backend curl -fs -o /dev/null http://127.0.0.1:8081/api/homestays/featured 2>/dev/null; then
      return 0
    fi
    sleep 5
  done
  "${COMPOSE[@]}" logs --tail 80 backend >&2
  fail "后端未在 7.5 分钟内就绪"
}

log "1/6 记录管理员账号"
db_query "SELECT username, email, password FROM users WHERE role = 'ROLE_ADMIN' ORDER BY id" > "$ADMINS_FILE"
ADMIN_COUNT="$(grep -c . "$ADMINS_FILE" || true)"
log "    共 $ADMIN_COUNT 个管理员"

log "2/6 停止后端并重建数据库"
"${COMPOSE[@]}" stop backend
root_query "DROP DATABASE IF EXISTS homestay_db; CREATE DATABASE homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

log "3/6 启动后端执行 Flyway 迁移"
"${COMPOSE[@]}" start backend
wait_backend
"${COMPOSE[@]}" stop backend

log "4/6 导入演示快照并恢复管理员"
export MYSQL_DATABASE=homestay_db MYSQL_USER="$DB_USER" MYSQL_PASSWORD="$DB_PASS"
MYSQL_CMD="docker compose exec -T -e MYSQL_PWD mysql mysql"
python3 tools/database/import_data.py --mysql-command "$MYSQL_CMD" demo-full
while IFS=$'\t' read -r username email password_hash; do
  [ -n "$username" ] || continue
  ADMIN_PASSWORD_HASH="$password_hash" python3 tools/database/import_data.py \
    --mysql-command "$MYSQL_CMD" admin --username "$username" --email "$email" >/dev/null </dev/null
done < "$ADMINS_FILE"

log "5/6 清空缓存与消息队列"
"${COMPOSE[@]}" exec -T -e REDISCLI_AUTH="$REDIS_PASS" redis redis-cli FLUSHALL >/dev/null
# 旧订单的超时/通知消息指向已删除的数据，直接清掉
"${COMPOSE[@]}" exec -T rabbitmq rabbitmqctl list_queues -q name 2>/dev/null | tr -d '\r' | while read -r queue; do
  [ -n "$queue" ] && [ "$queue" != "name" ] || continue
  "${COMPOSE[@]}" exec -T rabbitmq rabbitmqctl purge_queue -q "$queue" >/dev/null </dev/null || true
done

log "6/6 启动后端"
"${COMPOSE[@]}" start backend
wait_backend
log "完成：房源 $(db_query 'SELECT COUNT(*) FROM homestays') 个，管理员 $(db_query "SELECT COUNT(*) FROM users WHERE role = 'ROLE_ADMIN'") 个"
unset MYSQL_PWD MYSQL_PASSWORD
