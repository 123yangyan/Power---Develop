# Patch release: commit all changes, bump patch (0.2.0 -> 0.2.1), tag, push
# Usage: .\scripts\push.ps1 -Message "feat: description"

param(
    [Parameter(Mandatory = $true)]
    [string]$Message
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$RepoUrl = "https://github.com/123yangyan/Power---Develop"
$GradleFile = "mindbody-android/app/build.gradle.kts"
$VersionFile = "VERSION"

if (-not (Test-Path ".git")) {
    Write-Error "Not a git repository."
}

git add -A
$status = git status --porcelain
if (-not $status) {
    Write-Host "Nothing to commit." -ForegroundColor Yellow
    exit 0
}

if (-not (Test-Path $VersionFile)) {
    Write-Error "Missing file: $VersionFile"
}
if (-not (Test-Path $GradleFile)) {
    Write-Error "Missing file: $GradleFile"
}

$currentVersion = (Get-Content $VersionFile -Raw -Encoding UTF8).Trim()
if ($currentVersion -match '^(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?<suffix>.*)$') {
  $newVersion = "$($Matches.major).$($Matches.minor).$([int]$Matches.patch + 1)$($Matches.suffix)"
} else {
    Write-Error "VERSION '$currentVersion' is not semver (expected x.y.z)."
}

$gradleContent = Get-Content $GradleFile -Raw -Encoding UTF8
if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
    $newCode = [int]$Matches[1] + 1
    $gradleContent = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
} else {
    Write-Error "versionCode not found in build.gradle.kts"
}
$gradleContent = $gradleContent -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$newVersion`""

[System.IO.File]::WriteAllText((Join-Path $Root $GradleFile), $gradleContent, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $Root $VersionFile), "$newVersion`n", [System.Text.UTF8Encoding]::new($false))

git add $VersionFile $GradleFile

$commitMsg = "patch: v$newVersion - $Message"
Write-Host "Patch version: $currentVersion -> $newVersion (versionCode -> $newCode)" -ForegroundColor Cyan

git commit -m "$commitMsg"
if ($LASTEXITCODE -ne 0) {
    Write-Error "git commit failed (exit $LASTEXITCODE)."
}

$tag = "v$newVersion"
git tag -a $tag -m "$commitMsg"
if ($LASTEXITCODE -ne 0) {
    Write-Error "git tag failed (exit $LASTEXITCODE)."
}

git push -u origin HEAD:main
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push failed (exit $LASTEXITCODE)."
}

git push origin $tag
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push tag failed (exit $LASTEXITCODE)."
}

$hash = git rev-parse --short HEAD
Write-Host "Pushed $hash tag $tag to origin/main ($RepoUrl)" -ForegroundColor Green
