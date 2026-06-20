# Upload dashboard.html to ECS and rebuild api (cached)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = "D:\OneDrive\Desktop\test\owner\server"

Write-Host "==> Upload dashboard.html"
scp "$root\backend\app\static\dashboard.html" "${ecsHost}:/opt/timedrecorder/backend/app/static/"

Write-Host "==> Rebuild api (cached) and restart"
$remoteCmd = 'cd /opt/timedrecorder && docker compose build api && docker compose up -d api'
ssh $ecsHost $remoteCmd

Write-Host "==> Done. Open http://120.26.204.190/dashboard"
