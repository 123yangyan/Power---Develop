#!/usr/bin/env bash
# 实时观察：Redis 队列 + 数据库记录 + worker 日志提示
set -euo pipefail

cd /opt/timedrecorder

INTERVAL="${1:-5}"

echo "=========================================="
echo " 定时录音助手 — 处理流水线监视器"
echo " 每 ${INTERVAL} 秒刷新一次，Ctrl+C 退出"
echo "=========================================="
echo ""
echo "另开窗口看实时日志："
echo "  docker compose logs -f worker"
echo "  docker compose logs -f api"
echo ""

while true; do
  clear
  echo "========== $(date '+%Y-%m-%d %H:%M:%S') =========="
  echo ""

  echo ">>> 容器状态"
  docker compose ps --format "table {{.Service}}\t{{.Status}}" 2>/dev/null || docker compose ps
  echo ""

  echo ">>> Redis 待处理队列长度"
  docker compose exec -T redis redis-cli LLEN timedrecorder:audio_jobs 2>/dev/null || echo "无法连接 redis"
  echo ""

  echo ">>> 最近 8 条数据库记录"
  docker compose exec -T api python -m app.show_records 8 2>/dev/null || \
    docker compose exec -T api python -c "
import json
from sqlalchemy import select
from app.database import SessionLocal, init_db
from app.models import AudioFile
init_db()
db = SessionLocal()
rows = db.execute(select(AudioFile).order_by(AudioFile.created_at.desc()).limit(8)).scalars().all()
for r in rows:
    print(f'{r.status:12} | {r.file_id[:12]}... | {r.file_name}')
db.close()
"
  echo ""
  echo "下次刷新: ${INTERVAL}s ..."
  sleep "$INTERVAL"
done
