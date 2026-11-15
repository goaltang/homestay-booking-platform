#!/usr/bin/env bash
# ============================================
# 一键启动演示环境：启动 compose → 等后端就绪 → 导入脱敏演示数据 → 创建管理员
# 用法（仓库根目录）：./deploy/demo.sh
#   本地：没有 .env 时自动生成 localhost 配置（用户端 8088、管理端 8089，密钥随机）
#   服务器：先按 docs/DEPLOY.md 填好 .env，再运行本脚本
# 使用 docker-compose.prod.yml：只发布用户端与管理端两个端口，不与本机 MySQL/Redis 冲突。
# 演示数据只会导入空库；再次运行只会启动服务，不会重复导入或覆盖数据。
# 依赖：Docker（含 compose 插件）、Python 3（创建管理员需要 python3-venv 或已安装 bcrypt）
# ============================================
set -euo pipefail

cd "$(dirname "$0")/.."

# 通过 COMPOSE_FILE 环境变量指定 compose 文件：import_data.py 出于安全会拒绝含 -f 的 mysql 命令
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE=(docker compose)
VENV_PY="tools/database/.venv/bin/python"

log() { echo "[demo] $*"; }
fail() { echo "[demo] $*" >&2; exit 1; }

command -v docker >/dev/null || fail "未找到 docker"
docker compose version >/dev/null 2>&1 || fail "未找到 docker compose 插件"
command -v python3 >/dev/null || fail "未找到 python3"
[ -f tools/database/demo_full.sql.gz ] || fail "缺少 tools/database/demo_full.sql.gz"

# ---------- 1. 准备 .env ----------
if [ ! -f .env ]; then
  log "1/5 未找到 .env，按 .env.prod.example 生成本地演示配置（localhost，随机密钥与密码）"
  python3 - <<'GENERATE_ENV'
import re, secrets
text = open(".env.prod.example", encoding="utf-8").read()
values = {
    "SITE_PORT": "8088",
    "ADMIN_PORT": "8089",
    "APP_SERVER_URL": "http://localhost:8088",
    "APP_CORS_ALLOWED_ORIGINS": "http://localhost:8088,http://localhost:8089",
    "JWT_SECRET": secrets.token_urlsafe(48),
    "DB_PASSWORD": secrets.token_hex(16),
    "DB_ROOT_PASSWORD": secrets.token_hex(16),
    "REDIS_PASSWORD": secrets.token_hex(16),
    "RABBITMQ_PASSWORD": secrets.token_hex(16),
    "ALIPAY_NOTIFY_URL": "http://localhost:8088/api/payment/alipay/notify",
    "ALIPAY_RETURN_URL": "http://localhost:8088/orders/pay/success",
    "DEMO_GUEST_USERNAME": "user_4424",
    "DEMO_HOST_USERNAME": "host_0097",
}
for key, value in values.items():
    text, count = re.subn(rf"^{key}=.*$", f"{key}={value}", text, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f".env.prod.example 中缺少 {key}")
with open(".env", "w", encoding="utf-8") as out:
    out.write(text)
GENERATE_ENV
  chmod 600 .env
else
  log "1/5 使用已有 .env"
fi

env_value() { { grep -E "^$1=" .env || true; } | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
DB_USER="$(env_value DB_USERNAME)"
DB_PASS="$(env_value DB_PASSWORD)"
[ -n "$DB_USER" ] && [ -n "$DB_PASS" ] || fail ".env 中缺少 DB_USERNAME 或 DB_PASSWORD"
if grep -Eq '^[^#]*1\.2\.3\.4' .env; then
  fail ".env 中还有示例 IP 1.2.3.4，请替换为服务器公网 IP"
fi

# ---------- 2. 启动服务 ----------
log "2/5 构建并启动服务（首次构建需要下载依赖，可能需要 10 分钟以上）"
"${COMPOSE[@]}" up -d --build

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
log "3/5 等待后端完成 Flyway 迁移并就绪"
wait_backend

# ---------- 3. 导入演示数据（仅空库） ----------
export MYSQL_PWD="$DB_PASS"
db_count() {
  "${COMPOSE[@]}" exec -T -e MYSQL_PWD mysql mysql -u"$DB_USER" homestay_db -N -B -e "$1" | tr -d '\r'
}
export MYSQL_DATABASE=homestay_db MYSQL_USER="$DB_USER" MYSQL_PASSWORD="$DB_PASS"
MYSQL_CMD="docker compose exec -T -e MYSQL_PWD mysql mysql"

if [ "$(db_count 'SELECT COUNT(*) FROM homestays')" = "0" ]; then
  log "4/5 导入演示数据（导入期间暂停后端）"
  "${COMPOSE[@]}" stop backend
  python3 tools/database/import_data.py --mysql-command "$MYSQL_CMD" demo-full
  "${COMPOSE[@]}" start backend
  wait_backend
else
  log "4/5 库中已有房源，跳过演示数据导入"
fi

# ---------- 4. 创建管理员（仅在没有管理员时） ----------
ADMIN_NOTE=""
if [ "$(db_count "SELECT COUNT(*) FROM users WHERE role = 'ROLE_ADMIN'")" = "0" ]; then
  log "5/5 创建管理员 admin（随机密码）"
  if python3 -c "import bcrypt" 2>/dev/null; then
    PY="python3"
  else
    if [ ! -x "$VENV_PY" ]; then
      python3 -m venv tools/database/.venv || fail "创建虚拟环境失败；Ubuntu 可先 apt install -y python3-venv"
    fi
    "$VENV_PY" -m pip install -q -r tools/database/requirements.txt || fail "安装 bcrypt 失败"
    PY="$VENV_PY"
  fi
  ADMIN_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(12))')"
  ADMIN_PASSWORD_HASH="$(ADMIN_PASSWORD="$ADMIN_PASSWORD" "$PY" -c \
    'import bcrypt, os; print(bcrypt.hashpw(os.environ["ADMIN_PASSWORD"].encode(), bcrypt.gensalt(10)).decode())')"
  ADMIN_PASSWORD_HASH="$ADMIN_PASSWORD_HASH" "$PY" tools/database/import_data.py \
    --mysql-command "$MYSQL_CMD" admin --username admin --email admin@example.com
  ADMIN_NOTE="管理员：admin / $ADMIN_PASSWORD（只显示这一次，请自行保存）"
else
  log "5/5 已有管理员，跳过创建"
fi
unset MYSQL_PWD MYSQL_PASSWORD

# ---------- 完成 ----------
HOST="$(env_value APP_SERVER_URL | sed -E 's#^https?://##; s#[:/].*$##')"
SITE_PORT="$(env_value SITE_PORT)"
ADMIN_PORT="$(env_value ADMIN_PORT)"
echo
log "完成"
log "用户端：http://${HOST:-localhost}:${SITE_PORT:-80}    管理端：http://${HOST:-localhost}:${ADMIN_PORT:-8089}"
log "演示账号：房客 user_4424 / 123456（订单较多），房东 host_0097 / 123456；所有演示账号密码均为 123456"
if [ -n "$ADMIN_NOTE" ]; then
  log "$ADMIN_NOTE"
fi
