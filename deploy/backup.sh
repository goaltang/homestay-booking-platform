#!/usr/bin/env bash
# ============================================
# 备份：MySQL 全库 + uploads 目录
# 用法：./deploy/backup.sh
# 建议 cron：0 4 * * * cd /opt/homestay3 && ./deploy/backup.sh >> /var/log/homestay-backup.log 2>&1
# ============================================
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE_FILE="docker-compose.prod.yml"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
STAMP="$(date +%F-%H%M)"

mkdir -p "$BACKUP_DIR"
mkdir -p uploads

echo "[backup] MySQL -> $BACKUP_DIR/db-$STAMP.sql.gz"
docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers homestay_db' \
  | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"

echo "[backup] uploads -> $BACKUP_DIR/uploads-$STAMP.tar.gz"
tar czf "$BACKUP_DIR/uploads-$STAMP.tar.gz" uploads

# 备份文件可能含用户身份证照片等隐私数据
chmod 600 "$BACKUP_DIR"/*-"$STAMP".*

echo "[backup] 清理 $RETENTION_DAYS 天前的备份"
find "$BACKUP_DIR" -maxdepth 1 -name '*.gz' -mtime +"$RETENTION_DAYS" -delete

ls -lh "$BACKUP_DIR" | tail -n 5
