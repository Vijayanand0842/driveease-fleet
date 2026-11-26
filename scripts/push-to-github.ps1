#!/usr/bin/env pwsh
# Script to push DriveEase to GitHub and deploy to Railway
# Run this AFTER gh auth login and railway login

param(
    [string]$GitHubUser = "vijaygrd0803-afk",
    [string]$RepoName = "driveease-fleet"
)

$ProjectRoot = "C:\Users\vijay\eclipse-workspace\driveease-fleet"
Set-Location $ProjectRoot

Write-Host "=== Step 1: Create GitHub repo and push ===" -ForegroundColor Cyan

# Create repo and push
gh repo create $RepoName --public --source=. --remote=origin --push
if ($LASTEXITCODE -ne 0) {
    Write-Host "Repo may already exist, trying to push to existing..." -ForegroundColor Yellow
    git remote set-url origin "https://github.com/$GitHubUser/$RepoName.git"
    git push -u origin main --force
}

Write-Host "✅ Code pushed to https://github.com/$GitHubUser/$RepoName" -ForegroundColor Green
Write-Host ""
Write-Host "=== GitHub push DONE! ===" -ForegroundColor Green
Write-Host "Now go to https://railway.app and deploy from GitHub." -ForegroundColor Cyan
