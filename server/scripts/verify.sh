#!/usr/bin/env bash
# 联调验证脚本
set -euo pipefail

BASE_URL="${1:-https://120.26.204.190}"
API_KEY="${2:-}"
AUDIO_FILE="${3:-}"

CURL_OPTS=(-k -sS)
if [[ -n "${API_KEY}" ]]; then
  CURL_OPTS+=(-H "Authorization: Bearer ${API_KEY}")
fi

echo "==> 1. 健康检查"
curl "${CURL_OPTS[@]}" "${BASE_URL}/api/health"
echo

if [[ -z "${AUDIO_FILE}" || ! -f "${AUDIO_FILE}" ]]; then
  echo "跳过上传测试（未提供音频文件）。用法:"
  echo "  bash scripts/verify.sh https://120.26.204.190 your-api-key /path/to/test.m4a"
  exit 0
fi

echo "==> 2. 上传音频"
UPLOAD_RESP=$(curl "${CURL_OPTS[@]}" -X POST "${BASE_URL}/api/audio/upload" \
  -F "file=@${AUDIO_FILE}" \
  -F "fileName=$(basename "${AUDIO_FILE}")" \
  -F "format=m4a" \
  -F "startTime=2026-06-06T09:00:00+08:00" \
  -F "endTime=2026-06-06T09:05:00+08:00" \
  -F "duration=300" \
  -F "deviceId=test-device-001" \
  -F "localId=1")
echo "${UPLOAD_RESP}"

FILE_ID=$(python3 -c "import json,sys; d=json.loads(sys.argv[1]); print(d.get('data',{}).get('fileId',''))" "${UPLOAD_RESP}")

if [[ -z "${FILE_ID}" ]]; then
  echo "上传失败，未获取 fileId"
  exit 1
fi

echo "==> 3. 轮询处理结果 fileId=${FILE_ID}"
for i in $(seq 1 20); do
  RESULT=$(curl "${CURL_OPTS[@]}" "${BASE_URL}/api/audio/result?fileId=${FILE_ID}")
  echo "第 ${i} 次: ${RESULT}"
  if echo "${RESULT}" | grep -q '"status":"completed"'; then
    echo "处理完成"
    exit 0
  fi
  if echo "${RESULT}" | grep -q '"status":"failed"'; then
    echo "处理失败"
    exit 1
  fi
  sleep 15
done

echo "轮询超时"
exit 1
