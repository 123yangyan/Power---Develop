#!/usr/bin/env bash
# ECS 上修复 vitals batch inserted:0 — patch 3 个文件并重建 api
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_DIR="${TARGET_DIR}/backend/app"

cd "${TARGET_DIR}"

echo "==> 1. verify files"
grep -q '"client_row_id", "ts"' "${APP_DIR}/vitals_models.py" && echo "models OK" || echo "models OLD"
grep -q 'SessionLocal.begin' "${APP_DIR}/vitals_api.py" && echo "api OK" || echo "api OLD"
grep -q '_ensure_vitals_dedup' "${APP_DIR}/database.py" && echo "db OK" || echo "db OLD"

echo "==> 2. patch vitals_models.py (skip if already OK)"
if grep -q '"client_row_id", "ts"' "${APP_DIR}/vitals_models.py"; then
  echo "models already patched, skip sed"
else
  sed -i 's/UniqueConstraint("device_id", "source", "client_row_id", name=/UniqueConstraint("device_id", "source", "client_row_id", "ts", name=/' \
      "${APP_DIR}/vitals_models.py"
fi
grep -q '"client_row_id", "ts"' "${APP_DIR}/vitals_models.py" || { echo "vitals_models patch FAILED"; exit 1; }

echo "==> 3. patch database.py"
python3 - "${APP_DIR}/database.py" << 'PYEOF'
import sys
p = sys.argv[1]
src = open(p).read()
if '_ensure_vitals_dedup_indexes' not in src:
    insert_before = 'def _create_hypertables'
    new_funcs = '''
VITALS_DEDUP_TABLES = [
    "hr_samples", "skin_temp_samples", "acc_minute_summary", "ppi_samples",
    "activity_day_summary", "hr_247_samples", "ppi_247_samples", "skin_temp_247_samples",
    "nightly_recharge", "activity_minute_samples", "sleep_sessions", "training_sessions",
    "mood_entries",
]


def _ensure_vitals_dedup_indexes() -> None:
    """ON CONFLICT (device_id, source, client_row_id, ts) 所需唯一索引。"""
    for table_name in VITALS_DEDUP_TABLES:
        try:
            with engine.begin() as conn:
                conn.execute(text(f"""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_{table_name}_dedup_idx
                    ON {table_name} (device_id, source, client_row_id, ts)
                """))
        except Exception as exc:
            logger.warning("Vitals dedup index %s skipped (%s)", table_name, exc)


'''
    src = src.replace(insert_before, new_funcs + insert_before)
if '    _ensure_vitals_dedup_indexes()\n' not in src:
    src = src.replace(
        '    _create_hypertables()\n',
        '    _create_hypertables()\n    _ensure_vitals_dedup_indexes()\n',
        1,
    )
open(p, 'w').write(src)
assert '_ensure_vitals_dedup_indexes' in open(p).read()
print('database.py patched')
PYEOF

echo "==> 4. patch vitals_api.py"
python3 - "${APP_DIR}/vitals_api.py" << 'PYEOF'
import re, sys
p = sys.argv[1]
src = open(p).read()

if 'from app.database import get_db, SessionLocal' not in src:
    src = src.replace(
        'from app.database import get_db\n',
        'from app.database import get_db, SessionLocal\n',
    )

new_batch = r'''
@router.post("/batch")
def vitals_batch(request: VitalsBatchRequest, db: Session = Depends(get_db)):
    """幂等批量上报身心数据。每批最多 500 行。"""
    table = request.table
    if table not in VALID_TABLES:
        raise HTTPException(status_code=400, detail=f"Invalid table: {table}")

    rows = request.rows
    if len(rows) > 500:
        raise HTTPException(status_code=400, detail="Max 500 rows per batch")

    if not rows:
        return success(VitalsBatchResponseData(inserted=0, skipped=0))

    columns = TABLE_COLUMNS.get(table, [])
    now = datetime.now(timezone.utc)
    col_list = ", ".join(columns)
    val_placeholders = ", ".join(f":{c}" for c in columns)

    inserted = 0
    skipped = 0

    insert_sql = text(f"""
        INSERT INTO {table}
            (device_id, source, client_row_id, ts, created_at, {col_list})
        VALUES
            (:device_id, :source, :client_row_id, :ts, :created_at, {val_placeholders})
        ON CONFLICT (device_id, source, client_row_id, ts) DO NOTHING
    """)

    for row in rows:
        client_row_id = str(row.get("clientRowId", ""))
        ts = row.get("ts", 0)

        values: dict = {}
        for col in columns:
            values[col] = row.get(col)

        params = {
            "device_id": request.deviceId,
            "source": request.source,
            "client_row_id": client_row_id,
            "ts": ts,
            "created_at": now,
            **values,
        }

        try:
            with SessionLocal.begin() as row_db:
                row_db.execute(insert_sql, params)
            inserted += 1
        except Exception as exc:
            skipped += 1
            logger.warning(
                "Vitals batch row skipped table=%s device=%s client_row_id=%s: %s",
                table, request.deviceId, client_row_id, exc,
            )

    logger.info("Vitals batch table=%s device=%s inserted=%d skipped=%d", table, request.deviceId, inserted, skipped)
    return success(VitalsBatchResponseData(inserted=inserted, skipped=skipped))
'''

pattern = r'@router\.post\("/batch"\).*?(?=\n@router\.get\("/devices"\))'
if not re.search(pattern, src, re.DOTALL):
    raise SystemExit('vitals_api: cannot find batch endpoint')
src = re.sub(pattern, new_batch.strip() + '\n\n', src, count=1, flags=re.DOTALL)

open(p, 'w').write(src)
ok1 = 'SessionLocal.begin' in src
ok2 = 'client_row_id, ts) DO NOTHING' in src
print(f'import/batch OK={ok1} conflict OK={ok2}')
if not (ok1 and ok2):
    raise SystemExit('vitals_api patch FAILED')
PYEOF

echo "==> 5. verify patch result"
grep -q '"client_row_id", "ts"' "${APP_DIR}/vitals_models.py" && echo "models OK"
grep -q 'SessionLocal.begin' "${APP_DIR}/vitals_api.py" && echo "api OK"
grep -q '_ensure_vitals_dedup' "${APP_DIR}/database.py" && echo "db OK"

echo "==> 6. rebuild api image (cached; use --no-cache only if requirements/Dockerfile changed)"
docker compose build api
docker compose up -d api

echo "==> 7. 等待 API 就绪"
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1/api/health >/dev/null 2>&1; then
    echo "API healthy"
    break
  fi
  sleep 2
done

API_KEY="${API_KEY:-MyRecorder2026!}"
echo "==> 8. 验证 batch 写入"
RESULT=$(curl -s -X POST http://127.0.0.1/api/vitals/batch \
  -H "Authorization: Bearer ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test001","source":"LIVE_STREAM","table":"hr_samples","rows":[{"clientRowId":"r1","ts":1718000001000,"bpm":75,"rr_ms":810}]}')
echo "${RESULT}"

if echo "${RESULT}" | grep -q '"inserted":1'; then
  echo "==> SUCCESS: batch inserted=1"
  curl -s -H "Authorization: Bearer ${API_KEY}" http://127.0.0.1/api/vitals/devices
  echo ""
else
  echo "==> FAILED — logs:"
  docker compose logs api --tail 30 | grep -E 'WARNING|ERROR|skipped' || true
  exit 1
fi
