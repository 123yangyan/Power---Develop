#!/usr/bin/env bash
# ECS: deploy stream_debug endpoints (volume mount + uvicorn --reload)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_DIR="${TARGET_DIR}/backend/app"

cd "${TARGET_DIR}"

echo "==> 1. verify files"
test -f "${APP_DIR}/stream_debug.py" && echo "stream_debug.py OK"
test -f "${APP_DIR}/static/stream_debug.html" && echo "stream_debug.html OK"
grep -q 'stream_debug_router' "${APP_DIR}/main.py" && echo "main.py OK" || { echo "main.py missing stream_debug_router"; exit 1; }

echo "==> 2. apply compose (volume mount + --reload)"
docker compose up -d api

echo "==> 3. wait for API"
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1/api/health >/dev/null 2>&1; then
    echo "API healthy"
    break
  fi
  sleep 2
done

echo "==> 4. verify stream debug page"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/api/vitals/stream/debug/)
echo "GET /api/vitals/stream/debug/ -> HTTP ${HTTP_CODE}"
if [ "${HTTP_CODE}" = "200" ]; then
  echo "==> SUCCESS"
else
  echo "==> FAILED — logs:"
  docker compose logs api --tail 30
  exit 1
fi
