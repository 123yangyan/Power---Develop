#!/usr/bin/env bash
# ECS: apply Mosquitto WebSocket (8083) + nginx /mqtt proxy, then restart
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${TARGET_DIR}"

echo "==> 1. write mosquitto.conf (1883 + WebSocket 8083)"
cat > "${TARGET_DIR}/mosquitto/mosquitto.conf" << 'EOF'
listener 1883 0.0.0.0
allow_anonymous false
password_file /mosquitto/config/passwd

# WebSocket (nginx /mqtt proxy; mobile uses port 80 with API)
listener 8083 0.0.0.0
protocol websockets
allow_anonymous false
password_file /mosquitto/config/passwd

persistence true
persistence_location /mosquitto/data/
EOF

echo "==> 2. write nginx.conf (/mqtt -> mosquitto:8083)"
cat > "${TARGET_DIR}/nginx/nginx.conf" << 'EOF'
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    server {
        listen 80;
        server_name _;

        location /dashboard {
            proxy_pass http://api:8000;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /api/ {
            proxy_pass http://api:8000;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 120s;
            proxy_send_timeout 120s;
            proxy_buffering off;
            client_max_body_size 10m;
        }

        location /api/health {
            proxy_pass http://api:8000;
            proxy_set_header Host $host;
        }

        location /mqtt {
            proxy_pass http://mosquitto:8083;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_read_timeout 3600s;
            proxy_send_timeout 3600s;
        }
    }
}
EOF

sed -i '1s/^\xEF\xBB\xBF//' "${TARGET_DIR}/nginx/nginx.conf" 2>/dev/null || true

echo "==> 3. restart mosquitto nginx"
docker compose restart mosquitto nginx
sleep 2

echo "==> 4. verify mosquitto listeners"
docker compose logs mosquitto --tail 15

if docker compose logs mosquitto --tail 30 | grep -q 'port 8083'; then
  echo "==> SUCCESS: Mosquitto WebSocket listener on port 8083"
else
  echo "==> FAILED: port 8083 not found in mosquitto logs"
  exit 1
fi

echo "==> 5. smoke test pub/sub on 1883 (internal)"
docker compose exec -T mosquitto mosquitto_pub \
  -h localhost -u mindbody -P "${MQTT_PASSWORD:-MyRecorder2026!}" \
  -t 'physio/83f94020/feedback' \
  -m '{"notification_id":"99","state_label":"test","message":"ecs-smoke-test"}' \
  && echo "==> pub OK"
