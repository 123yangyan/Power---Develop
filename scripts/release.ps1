# MindBody / Power-Develop release script
# Usage: .\scripts\release.ps1 -Version "0.2.0" -Message "Phase 2 mood port"
# Optional: -SkipPush (local commit + tag only)

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Message = "",

    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Test-Path ".git")) {
    Write-Error "Not a git repository. Run git init in project root first."
}

$GradleFile = "mindbody-android/app/build.gradle.kts"
$VersionFile = "VERSION"

if (-not (Test-Path $GradleFile)) {
    Write-Error "Missing file: $GradleFile"
}

$content = Get-Content $GradleFile -Raw -Encoding UTF8
if ($content -match 'versionCode\s*=\s*(\d+)') {
    $newCode = [int]$Matches[1] + 1
    $content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
} else {
    Write-Error "versionCode not found in build.gradle.kts"
}

$content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$Version`""
[System.IO.File]::WriteAllText((Join-Path $Root $GradleFile), $content, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $Root $VERSIONFile), "$Version`n", [System.Text.UTF8Encoding]::new($false))

if ($Message) {
    $commitMsg = "release: v$Version - $Message"
} else {
    $commitMsg = "release: v$Version"
}

Write-Host "Version: $Version (versionCode -> $newCode)" -ForegroundColor Cyan

git add $VersionFile $GradleFile
git commit -m $commitMsg

$tag = "v$Version"
git tag -a $tag -m $commitMsg

Write-Host "Created commit and tag: $tag" -ForegroundColor Green

if ($SkipPush) {
    Write-Host "SkipPush enabled, skipping remote push." -ForegroundColor Yellow
    exit 0
}

$remote = git remote get-url origin 2>$null
if (-not $remote) {
    Write-Error "origin remote not configured"
}

Write-Host "Pushing to origin/main and tag $tag ..." -ForegroundColor Cyan
git push -u origin HEAD:main
git push origin $tag

$repoUrl = 'https://github.com/123yangyan/Power---Develop'
Write-Host "Done: $repoUrl" -ForegroundColor Green
