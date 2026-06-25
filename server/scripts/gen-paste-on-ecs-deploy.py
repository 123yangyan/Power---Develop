#!/usr/bin/env python3
"""Generate paste-on-ecs-deploy.sh with embedded base64 of stream_routes.py."""
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "backend" / "app" / "stream_routes.py"
OUT = Path(__file__).resolve().parent / "paste-on-ecs-deploy.sh"

b64 = base64.b64encode(SRC.read_bytes()).decode("ascii")
script = f"""#!/usr/bin/env bash
set -euo pipefail
cd /opt/timedrecorder/backend/app
python3 << 'PY'
import base64, pathlib
data = base64.b64decode('{b64}')
pathlib.Path('stream_routes.py').write_bytes(data)
print('stream_routes.py written', len(data), 'bytes')
PY
cd /opt/timedrecorder
docker compose restart api
sleep 5
if [ -f scripts/ecs-deploy-stream-status.sh ]; then
  bash scripts/ecs-deploy-stream-status.sh db683f93
else
  API_KEY=$(grep -E '^API_KEY=' .env | head -1 | cut -d= -f2- | tr -d '\\r')
  curl -s -H "Authorization: Bearer ${{API_KEY}}" \\
    "http://127.0.0.1/api/vitals/stream/status?device_id=db683f93" | head -c 600
  echo
fi
"""
OUT.write_text(script, encoding="utf-8", newline="\n")
print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")
