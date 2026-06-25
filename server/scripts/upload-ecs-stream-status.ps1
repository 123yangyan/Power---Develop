# Upload stream status endpoint fix to ECS (interactive password for scp/ssh)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = "D:\OneDrive\Desktop\test\owner\server"

Write-Host "==> Upload stream_routes.py"
scp "$root\backend\app\stream_routes.py" "${ecsHost}:/opt/timedrecorder/backend/app/"

Write-Host "==> Restart api container (volume-mounted code hot reload)"
$remoteCmd = 'cd /opt/timedrecorder; docker compose restart api; sleep 5; docker compose logs api --tail 15'
ssh $ecsHost $remoteCmd

Write-Host "==> Verify /status endpoint (replace DEVICE_ID and run on ECS if curl fails locally)"
Write-Host 'ssh root@120.26.204.190 "curl -s -H \"Authorization: Bearer \$API_KEY\" \"http://127.0.0.1/api/vitals/stream/status?device_id=YOUR_DEVICE_ID\" | head -c 500"'
