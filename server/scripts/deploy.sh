#!/usr/bin/env bash
# 一键部署定时录音助手后端
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> 部署目录: ${TARGET_DIR}"
cd "${TARGET_DIR}"

if [[ ! -f "${TARGET_DIR}/.env" ]]; then
  echo "ERROR: 未找到 ${TARGET_DIR}/.env，请先执行: cp .env.example .env && nano .env"
  exit 1
fi

if [[ ! -f "${TARGET_DIR}/nginx/certs/cert.pem" ]]; then
  echo "==> 生成自签证书"
  bash "${TARGET_DIR}/scripts/generate-selfsigned-cert.sh"
fi

echo "==> 修复脚本换行符（Windows 上传兼容）"
sed -i 's/\r$//' "${TARGET_DIR}"/scripts/*.sh 2>/dev/null || true

echo "==> 启动 Docker Compose"
docker compose up -d --build
docker compose ps

echo "==> 部署完成"
echo "健康检查: curl -k https://127.0.0.1/api/health"
