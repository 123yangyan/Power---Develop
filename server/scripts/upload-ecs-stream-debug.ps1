# Upload stream_debug files to ECS (interactive password for scp/ssh)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = "D:\OneDrive\Desktop\test\owner\server"

Write-Host "==> Upload docker-compose.yml (volume mount + --reload)"
scp "$root\docker-compose.yml" "${ecsHost}:/opt/timedrecorder/"

Write-Host "==> Upload stream_debug.py, main.py"
scp "$root\backend\app\stream_debug.py" `
    "$root\backend\app\main.py" `
    "${ecsHost}:/opt/timedrecorder/backend/app/"

Write-Host "==> Ensure static dir and upload stream_debug.html"
$remoteMkdir = 'mkdir -p /opt/timedrecorder/backend/app/static'
ssh $ecsHost $remoteMkdir
scp "$root\backend\app\static\stream_debug.html" `
    "${ecsHost}:/opt/timedrecorder/backend/app/static/"

Write-Host "==> Run deploy script on ECS"
$remoteCmd = 'cd /opt/timedrecorder; sed -i ''s/\r$//'' scripts/ecs-deploy-stream-debug.sh; bash scripts/ecs-deploy-stream-debug.sh'
scp "$root\scripts\ecs-deploy-stream-debug.sh" "${ecsHost}:/opt/timedrecorder/scripts/"
ssh $ecsHost $remoteCmd
