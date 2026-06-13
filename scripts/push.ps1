# 日常推送：提交当前改动并推送到 GitHub
# 用法: .\scripts\push.ps1 -Message "feat: 描述改动"

param(
    [Parameter(Mandatory = $true)]
    [string]$Message
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Test-Path ".git")) {
    Write-Error "当前目录不是 git 仓库"
}

git add -A
$status = git status --porcelain
if (-not $status) {
    Write-Host "没有可提交的改动。" -ForegroundColor Yellow
    exit 0
}

git commit -m $Message
git push -u origin HEAD:main

Write-Host "已推送到 https://github.com/123yangyan/Power---Develop" -ForegroundColor Green
