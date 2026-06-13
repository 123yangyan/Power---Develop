# MindBody / Power-Develop 版本发布脚本
# 用法: .\scripts\release.ps1 -Version "0.2.0" -Message "Phase 2 心情记录首版"
# 可选: -SkipPush 仅本地 commit + tag，不推送

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Message = "",

    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

# 校验是否在 git 仓库内
if (-not (Test-Path ".git")) {
    Write-Error "当前目录不是 git 仓库，请先在项目根目录执行 git init"
}

# 校验工作区是否干净（允许 VERSION 与 build.gradle 即将被修改）
$status = git status --porcelain
if ($status -and -not $SkipPush) {
    $dirty = $status | Where-Object { $_ -notmatch 'VERSION|build\.gradle\.kts' }
    if ($dirty) {
        Write-Host "工作区有未提交改动：" -ForegroundColor Yellow
        Write-Host $dirty
        $confirm = Read-Host "是否继续？(y/N)"
        if ($confirm -ne 'y') { exit 1 }
    }
}

$GradleFile = "mindbody-android/app/build.gradle.kts"
$VersionFile = "VERSION"

if (-not (Test-Path $GradleFile)) {
    Write-Error "找不到 $GradleFile"
}

# 读取并递增 versionCode
$content = Get-Content $GradleFile -Raw -Encoding UTF8
if ($content -match 'versionCode\s*=\s*(\d+)') {
    $newCode = [int]$Matches[1] + 1
    $content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
} else {
    Write-Error "无法在 build.gradle.kts 中找到 versionCode"
}

$content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$Version`""
Set-Content -Path $GradleFile -Value $content -Encoding UTF8

Set-Content -Path $VersionFile -Value "$Version`n" -Encoding UTF8

$commitMsg = if ($Message) { "release: v$Version — $Message" } else { "release: v$Version" }

Write-Host "版本: $Version (versionCode -> $newCode)" -ForegroundColor Cyan

git add $VersionFile $GradleFile
git commit -m $commitMsg

$tag = "v$Version"
git tag -a $tag -m $commitMsg

Write-Host "已创建 commit 与 tag: $tag" -ForegroundColor Green

if ($SkipPush) {
    Write-Host "SkipPush 已启用，跳过远程推送。" -ForegroundColor Yellow
    exit 0
}

$remote = git remote get-url origin 2>$null
if (-not $remote) {
    Write-Error "未配置 origin 远程，请先: git remote add origin https://github.com/123yangyan/Power---Develop.git"
}

Write-Host "推送到 origin/main 与 tag $tag ..." -ForegroundColor Cyan
git push -u origin HEAD:main
git push origin $tag

Write-Host "完成！仓库: https://github.com/123yangyan/Power---Develop" -ForegroundColor Green
