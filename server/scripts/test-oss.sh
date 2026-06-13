#!/usr/bin/env bash
# 在 api 容器内测试 OSS 上传权限
set -euo pipefail

cd /opt/timedrecorder

echo "==> .env OSS 配置"
grep OSS .env || true

echo "==> ECS RAM 角色凭证"
curl -s "http://100.100.100.200/latest/meta-data/ram/security-credentials/TimedRecorderECSRole" | head -c 200
echo

echo "==> 容器内 OSS 上传测试"
docker compose exec -T api python - <<'PY'
from app.config import get_settings
from app.oss_client import upload_bytes, build_object_key

settings = get_settings()
key = build_object_key("diag", "test001", "probe.txt")
try:
    upload_bytes(key, b"oss permission test", "text/plain")
    print("SUCCESS: uploaded", key)
except Exception as exc:
    print("FAILED:", exc)
PY
