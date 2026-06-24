# Upload MQTT WebSocket config to ECS and apply (interactive SSH password)
$ErrorActionPreference = "Stop"
$ecsHost = "root@120.26.204.190"
$root = Join-Path $PSScriptRoot ".."
$root = (Resolve-Path $root).Path

Write-Host "==> Upload apply-mqtt-websocket.sh"
scp "$root\scripts\apply-mqtt-websocket.sh" "${ecsHost}:/opt/timedrecorder/scripts/"

Write-Host "==> Run apply script on ECS"
$remoteCmd = 'cd /opt/timedrecorder; sed -i ''s/\r$//'' scripts/apply-mqtt-websocket.sh; bash scripts/apply-mqtt-websocket.sh'
ssh $ecsHost $remoteCmd
