# Upload sleep dashboard fix to ECS (interactive password for scp/ssh)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = "D:\OneDrive\Desktop\test\owner\server"

Write-Host "==> Upload vitals_api.py"
scp "$root\backend\app\vitals_api.py" "${ecsHost}:/opt/timedrecorder/backend/app/"

Write-Host "==> Upload dashboard.html"
scp "$root\backend\app\static\dashboard.html" "${ecsHost}:/opt/timedrecorder/backend/app/static/"

Write-Host "==> Restart api (volume-mounted source, no rebuild needed)"
$remoteCmd = 'cd /opt/timedrecorder; docker compose restart api; sleep 5; docker compose logs api --tail 15'
ssh $ecsHost $remoteCmd

Write-Host "Done. Open dashboard and switch to 睡眠分析 tab (7d window auto-applied)."
