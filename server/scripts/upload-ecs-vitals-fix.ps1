# Upload vitals fix to ECS (interactive password for scp/ssh)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = "D:\OneDrive\Desktop\test\owner\server"

Write-Host "==> Upload ecs-patch-vitals-deploy.sh"
scp "$root\scripts\ecs-patch-vitals-deploy.sh" "${ecsHost}:/opt/timedrecorder/scripts/"

Write-Host "==> Upload vitals_models.py, vitals_api.py, database.py"
scp "$root\backend\app\vitals_models.py" `
    "$root\backend\app\vitals_api.py" `
    "$root\backend\app\database.py" `
    "${ecsHost}:/opt/timedrecorder/backend/app/"

Write-Host "==> Run deploy script on ECS"
$remoteCmd = 'cd /opt/timedrecorder; sed -i ''s/\r$//'' scripts/ecs-patch-vitals-deploy.sh; bash scripts/ecs-patch-vitals-deploy.sh'
ssh $ecsHost $remoteCmd
