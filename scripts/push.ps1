# Daily push: commit and push to GitHub main (no version bump, no tag)
# Usage: .\scripts\push.ps1 -Message "feat: description"

param(
    [Parameter(Mandatory = $true)]
    [string]$Message
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$RepoUrl = "https://github.com/123yangyan/Power---Develop"

if (-not (Test-Path ".git")) {
    Write-Error "Not a git repository."
}

git add -A
$status = git status --porcelain
if (-not $status) {
    Write-Host "Nothing to commit." -ForegroundColor Yellow
    exit 0
}

git commit -m "$Message"
if ($LASTEXITCODE -ne 0) {
    Write-Error "git commit failed (exit $LASTEXITCODE)."
}

git push -u origin HEAD:main
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push failed (exit $LASTEXITCODE)."
}

$hash = git rev-parse --short HEAD
Write-Host "Pushed $hash to origin/main ($RepoUrl)" -ForegroundColor Green
