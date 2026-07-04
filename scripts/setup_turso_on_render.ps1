# -*- coding: utf-8 -*-
# Adds TURSO_DATABASE_URL + TURSO_AUTH_TOKEN on Render and triggers Manual Deploy.
# Usage (do NOT commit secrets):
#   $env:RENDER_API_KEY = "rnd_..."
#   $env:TURSO_DATABASE_URL = "libsql://....turso.io"
#   $env:TURSO_AUTH_TOKEN = "..."
#   .\scripts\setup_turso_on_render.ps1

param(
    [string]$ServiceName = "parental-server",
    [string]$RenderApiKey = $env:RENDER_API_KEY,
    [string]$TursoUrl = $env:TURSO_DATABASE_URL,
    [string]$TursoToken = $env:TURSO_AUTH_TOKEN
)

$ErrorActionPreference = "Stop"

function Require-Value($name, $value) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing $name. Set env var or pass as parameter."
    }
}

Require-Value "RENDER_API_KEY" $RenderApiKey
Require-Value "TURSO_DATABASE_URL" $TursoUrl
Require-Value "TURSO_AUTH_TOKEN" $TursoToken

$headers = @{
    Authorization = "Bearer $RenderApiKey"
    Accept        = "application/json"
    "Content-Type" = "application/json"
}

Write-Host "Looking up Render service '$ServiceName'..."
$services = Invoke-RestMethod -Uri "https://api.render.com/v1/services?limit=100" -Headers $headers
$match = $services | ForEach-Object { $_.service } | Where-Object { $_.name -eq $ServiceName } | Select-Object -First 1
if (-not $match) {
    $names = ($services | ForEach-Object { $_.service.name }) -join ", "
    throw "Service '$ServiceName' not found. Available: $names"
}
$serviceId = $match.id
Write-Host "Found service id: $serviceId"

function Set-RenderEnvVar($key, $value) {
    $body = @{ value = $value } | ConvertTo-Json
    $uri = "https://api.render.com/v1/services/$serviceId/env-vars/$key"
    Invoke-RestMethod -Method Put -Uri $uri -Headers $headers -Body $body | Out-Null
    Write-Host "Set env var: $key"
}

Set-RenderEnvVar "TURSO_DATABASE_URL" $TursoUrl
Set-RenderEnvVar "TURSO_AUTH_TOKEN" $TursoToken

Write-Host "Triggering deploy..."
$deployBody = @{ clearCache = "do_not_clear" } | ConvertTo-Json
$deploy = Invoke-RestMethod -Method Post -Uri "https://api.render.com/v1/services/$serviceId/deploys" -Headers $headers -Body $deployBody
$deployId = $deploy.id
Write-Host "Deploy started: $deployId"

Write-Host "Waiting for deploy (up to 5 min)..."
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 15
    $status = Invoke-RestMethod -Uri "https://api.render.com/v1/services/$serviceId/deploys/$deployId" -Headers $headers
    $state = $status.status
    Write-Host "  status: $state"
} while ($state -notin @("live", "deactivated", "build_failed", "update_failed", "canceled") -and (Get-Date) -lt $deadline)

if ($state -ne "live") {
    throw "Deploy did not reach live state (last: $state)"
}

Write-Host "Checking server storage mode..."
Start-Sleep -Seconds 10
$health = Invoke-RestMethod -Uri "https://parental-server-4mms.onrender.com/" -TimeoutSec 120
$mode = $health.storage.mode
$persistent = $health.storage.persistent
Write-Host "storage.mode=$mode persistent=$persistent"

if ($mode -ne "turso" -or -not $persistent) {
    throw "Turso not active yet. Check Render logs."
}

Write-Host "Done — Turso is active on Render."
