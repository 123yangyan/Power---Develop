#!/usr/bin/env bash
# ECS: deploy stream /status endpoint (volume mount + restart api)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_DIR="${TARGET_DIR}/backend/app"

cd "${TARGET_DIR}"

echo "==> 1. verify stream_routes.py contains /status"
grep -q 'get_device_status' "${APP_DIR}/stream_routes.py" && echo "stream_routes.py OK" || {
  echo "stream_routes.py missing get_device_status"; exit 1;
}

echo "==> 2. restart api (hot reload via volume mount)"
docker compose restart api

echo "==> 3. wait for API"
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1/api/health >/dev/null 2>&1; then
    echo "API healthy"
    break
  fi
  sleep 2
done

echo "==> 4. verify /status endpoint"
DEVICE_ID="${1:-db683f93}"
API_KEY="$(grep -E '^API_KEY=' .env | head -1 | cut -d= -f2- | tr -d '\r')"
if [ -z "${API_KEY}" ]; then
  echo "WARN: API_KEY empty in .env — skipping auth curl"
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    "http://127.0.0.1/api/vitals/stream/status?device_id=${DEVICE_ID}")
else
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer ${API_KEY}" \
    "http://127.0.0.1/api/vitals/stream/status?device_id=${DEVICE_ID}")
fi
echo "GET /api/vitals/stream/status?device_id=${DEVICE_ID} -> HTTP ${HTTP_CODE}"
if [ "${HTTP_CODE}" = "200" ]; then
  echo "==> Sample response:"
  curl -s -H "Authorization: Bearer ${API_KEY}" \
    "http://127.0.0.1/api/vitals/stream/status?device_id=${DEVICE_ID}" | head -c 600
  echo ""
  echo "==> SUCCESS"
else
  echo "==> FAILED — logs:"
  docker compose logs api --tail 30
  exit 1
fi
