# Minor release: bump minor (0.2.3 -> 0.3.0), tag with rich notes, push
# Usage: .\scripts\release.ps1 -Message "Phase 3 kickoff"
# Optional: -Version "0.3.0" (override auto minor bump), -Notes "multi-line...", -SkipPush

param(
    [string]$Version = "",

    [Parameter(Mandatory = $true)]
    [string]$Message,

    [string]$Notes = "",

    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$RepoUrl = "https://github.com/123yangyan/Power---Develop"
$GradleFile = "mindbody-android/app/build.gradle.kts"
$VersionFile = "VERSION"
$ChangelogFile = "CHANGELOG.md"

if (-not (Test-Path ".git")) {
    Write-Error "Not a git repository. Run git init in project root first."
}

if (-not (Test-Path $GradleFile)) {
    Write-Error "Missing file: $GradleFile"
}
if (-not (Test-Path $VersionFile)) {
    Write-Error "Missing file: $VersionFile"
}

$currentVersion = (Get-Content $VersionFile -Raw -Encoding UTF8).Trim()
if (-not $Version) {
    if ($currentVersion -match '^(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?<suffix>.*)$') {
        $Version = "$($Matches.major).$([int]$Matches.minor + 1).0$($Matches.suffix)"
    } else {
        Write-Error "VERSION '$currentVersion' is not semver (expected x.y.z)."
    }
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
[System.IO.File]::WriteAllText((Join-Path $Root $VersionFile), "$Version`n", [System.Text.UTF8Encoding]::new($false))

$commitMsg = "release: v$Version - $Message"
$tagBody = $commitMsg
if ($Notes) {
    $tagBody = "$commitMsg`n`n$Notes"
}

# Append structured entry to CHANGELOG.md
$date = Get-Date -Format "yyyy-MM-dd"
$changelogEntry = "## v$Version ($date)`n`n$Message`n"
if ($Notes) {
    $changelogEntry += "`n$Notes`n"
}
$changelogEntry += "`n"

$changelogPath = Join-Path $Root $ChangelogFile
if (Test-Path $changelogPath) {
    $existing = Get-Content $changelogPath -Raw -Encoding UTF8
    if ($existing -match '(?s)^(# Changelog\s*\r?\n)') {
        $header = $Matches[1]
        $rest = $existing.Substring($header.Length)
        $updated = $header + $changelogEntry + $rest
    } else {
        $updated = "# Changelog`n`n$changelogEntry$existing"
    }
    [System.IO.File]::WriteAllText($changelogPath, $updated, [System.Text.UTF8Encoding]::new($false))
} else {
    $header = "# Changelog`n`nAll notable minor releases (via /release). Patch pushes are tagged but not listed here.`n`n"
    [System.IO.File]::WriteAllText($changelogPath, "$header$changelogEntry", [System.Text.UTF8Encoding]::new($false))
}

Write-Host "Minor version: $currentVersion -> $Version (versionCode -> $newCode)" -ForegroundColor Cyan

git add $VersionFile $GradleFile $ChangelogFile
git commit -m "$commitMsg"
if ($LASTEXITCODE -ne 0) {
    Write-Error "git commit failed (exit $LASTEXITCODE)."
}

$tag = "v$Version"
git tag -a $tag -m "$tagBody"
if ($LASTEXITCODE -ne 0) {
    Write-Error "git tag failed (exit $LASTEXITCODE)."
}

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
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push failed (exit $LASTEXITCODE)."
}

git push origin $tag
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push tag failed (exit $LASTEXITCODE)."
}

$hash = git rev-parse --short HEAD
Write-Host "Released $hash tag $tag ($RepoUrl)" -ForegroundColor Green
