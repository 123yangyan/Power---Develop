#!/usr/bin/env bash
# 修复 vitals batch inserted:0 后重建 PostgreSQL schema（会清空 PG 数据卷）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${TARGET_DIR}"

echo "==> 停止服务并删除 postgres_data 卷（schema 约束已变更）"
docker compose down -v

echo "==> 无缓存重建 api 镜像"
docker compose build --no-cache api

echo "==> 启动全部服务"
docker compose up -d

echo "==> 等待 API 就绪..."
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1/api/health >/dev/null 2>&1; then
    echo "   API 健康 ✓"
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo "   API 启动超时: docker compose logs api --tail 50"
    exit 1
  fi
  sleep 2
done

API_KEY="${API_KEY:-MyRecorder2026!}"
echo "==> 验证 batch 写入"
RESULT=$(curl -s -X POST http://127.0.0.1/api/vitals/batch \
  -H "Authorization: Bearer ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test001","source":"LIVE_STREAM","table":"hr_samples","rows":[{"clientRowId":"r1","ts":1718000001000,"bpm":75,"rr_ms":810}]}')

echo "${RESULT}"

if echo "${RESULT}" | grep -q '"inserted":1'; then
  echo "==> batch 写入成功 ✓"
else
  echo "==> batch 写入失败，查看日志: docker compose logs api --tail 30"
  exit 1
fi

echo "==> 验证 devices 列表"
curl -s -H "Authorization: Bearer ${API_KEY}" http://127.0.0.1/api/vitals/devices
echo ""
