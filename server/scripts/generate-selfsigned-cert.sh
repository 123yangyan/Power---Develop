#!/usr/bin/env bash
# 生成 Nginx 自签 HTTPS 证书（无域名联调用）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}/../nginx/certs"
SERVER_IP="${1:-120.26.204.190}"

mkdir -p "${CERT_DIR}"

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "${CERT_DIR}/key.pem" \
  -out "${CERT_DIR}/cert.pem" \
  -subj "/CN=${SERVER_IP}"

chmod 600 "${CERT_DIR}/key.pem"
chmod 644 "${CERT_DIR}/cert.pem"

echo "证书已生成：${CERT_DIR}/cert.pem"
echo "私钥已生成：${CERT_DIR}/key.pem"
