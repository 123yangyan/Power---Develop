#!/usr/bin/env bash
# 一键部署定时录音助手后端（含 PostgreSQL + TimescaleDB 升级）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> 部署目录: ${TARGET_DIR}"
cd "${TARGET_DIR}"

# ── 0. 检查 .env ──
if [[ ! -f "${TARGET_DIR}/.env" ]]; then
  echo "ERROR: 未找到 ${TARGET_DIR}/.env，请先执行: cp .env.example .env && nano .env"
  exit 1
fi

# ── 1. 检查 POSTGRES_PASSWORD 是否仍为默认值 ──
if grep -q '^POSTGRES_PASSWORD=changeme' "${TARGET_DIR}/.env" 2>/dev/null; then
  echo "⚠️  POSTGRES_PASSWORD 仍为默认值 'changeme'，强烈建议修改！"
  echo "   编辑 .env 中的 POSTGRES_PASSWORD 和 DATABASE_URL，5 秒后继续..."
  sleep 5
fi

# ── 2. 检查 DATABASE_URL 是否仍为 SQLite ──
if grep -q '^DATABASE_URL=sqlite' "${TARGET_DIR}/.env" 2>/dev/null; then
  echo "⚠️  .env 中 DATABASE_URL 仍为 SQLite，自动更新为 PostgreSQL..."
  PG_PASS=$(grep '^POSTGRES_PASSWORD=' "${TARGET_DIR}/.env" | cut -d= -f2-)
  : "${PG_PASS:=changeme}"
  sed -i "s|^DATABASE_URL=.*|DATABASE_URL=postgresql://recorder:${PG_PASS}@postgres:5432/recorder|" "${TARGET_DIR}/.env"
  echo "   已更新 DATABASE_URL → postgresql://recorder:****@postgres:5432/recorder"
fi

# ── 3. 旧 SQLite 备份 ──
if docker compose exec -T api test -f /data/recorder.db 2>/dev/null; then
  BACKUP_NAME="recorder.db.pre-pg-migration.$(date +%Y%m%d%H%M%S)"
  echo "==> 备份旧 SQLite 数据库 → ${BACKUP_NAME}"
  docker compose exec -T api cp /data/recorder.db "/data/${BACKUP_NAME}" 2>/dev/null || true
fi

# ── 4. 证书 ──
if [[ ! -f "${TARGET_DIR}/nginx/certs/cert.pem" ]]; then
  echo "==> 生成自签证书"
  bash "${TARGET_DIR}/scripts/generate-selfsigned-cert.sh"
fi

# ── 5. 修复换行符 & BOM ──
echo "==> 修复脚本换行符（Windows 上传兼容）"
sed -i 's/\r$//' "${TARGET_DIR}"/scripts/*.sh 2>/dev/null || true
echo "==> 去除 nginx.conf UTF-8 BOM"
sed -i '1s/^\xEF\xBB\xBF//' "${TARGET_DIR}/nginx/nginx.conf" 2>/dev/null || true

# ── 6. 启动服务 ──
echo "==> 启动 Docker Compose（含 PostgreSQL + TimescaleDB + 备份容器）"
docker compose up -d --build

# ── 7. 等待 PostgreSQL 就绪 ──
echo "==> 等待 PostgreSQL 就绪..."
for i in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U recorder >/dev/null 2>&1; then
    echo "   PostgreSQL 就绪 ✓"
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo "   PostgreSQL 启动超时，请检查: docker compose logs postgres"
    exit 1
  fi
  sleep 2
done

# ── 8. 验证 API ──
echo "==> 等待 API 健康检查..."
for i in $(seq 1 20); do
  if curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
    echo "   API 健康 ✓"
    break
  fi
  if [[ $i -eq 20 ]]; then
    echo "   API 启动超时，请检查: docker compose logs api"
    exit 1
  fi
  sleep 3
done

# ── 9. 验证 TimescaleDB 扩展 ──
echo "==> 验证 TimescaleDB..."
TSDB_CHECK=$(docker compose exec -T postgres psql -U recorder -d recorder -tAc "SELECT default_version FROM pg_available_extensions WHERE name = 'timescaledb'" 2>/dev/null || echo "")
if [[ -n "$TSDB_CHECK" ]]; then
  echo "   TimescaleDB ${TSDB_CHECK} ✓"
else
  echo "   ⚠️  TimescaleDB 扩展未安装，hypertable 将跳过（功能降级为普通 PostgreSQL）"
fi

# ── 10. 显示状态 ──
echo ""
docker compose ps
echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo "  健康检查:  curl http://127.0.0.1/api/health"
echo "  看板页面:  http://127.0.0.1/dashboard"
echo "  数据库:    PostgreSQL (recorder@postgres:5432/recorder)"
echo "  备份:      每日 03:00 UTC → OSS"
echo "  降采样:    每日 02:00 UTC → hourly_agg"
echo ""
echo "  旧 SQLite 备份位于 api 容器 /data/ 目录"
echo "  手动备份:  docker compose exec backup python scripts/backup-pg.py"
echo "  手动聚合:  docker compose exec api python -c \"from app.database import SessionLocal; from app.agg_job import run_hourly_aggregation; db=SessionLocal(); print(run_hourly_aggregation(db)); db.close()\""
echo "========================================="
