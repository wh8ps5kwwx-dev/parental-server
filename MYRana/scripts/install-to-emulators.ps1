# تثبيت تطبيق الطفل والأم على محاكيين مختلفين (من VS Code أو PowerShell)
param(
    [string]$ChildSerial = "",
    [string]$ParentSerial = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$PropsFile = Join-Path $PSScriptRoot "emulators.local.properties"

if (Test-Path $PropsFile) {
    Get-Content $PropsFile | ForEach-Object {
        if ($_ -match '^\s*child\.serial\s*=\s*(.+)\s*$') { if (-not $ChildSerial) { $ChildSerial = $Matches[1].Trim() } }
        if ($_ -match '^\s*parent\.serial\s*=\s*(.+)\s*$') { if (-not $ParentSerial) { $ParentSerial = $Matches[1].Trim() } }
    }
}

if (-not $ChildSerial -or -not $ParentSerial) {
    Write-Host "الأجهزة المتصلة (USB + محاكي):" -ForegroundColor Yellow
    adb devices -l
    Write-Host ""
    Write-Host "عدّلي scripts/emulators.local.properties أو devices.local.json في parent_monitor_project" -ForegroundColor Yellow
    if (-not $ChildSerial) { $ChildSerial = Read-Host "Serial جهاز الطفل (مثل emulator-5554 أو USB ID)" }
    if (-not $ParentSerial) { $ParentSerial = Read-Host "Serial جهاز ولي الأمر (مثل emulator-5556)" }
}

function Test-AdbReady {
    param([string]$Serial)
    $line = adb devices | Select-String "^\s*$Serial\s+"
    if (-not $line) {
        Write-Host "خطأ: $Serial غير ظاهر في adb devices" -ForegroundColor Red
        adb devices
        exit 1
    }
    if ($line -match "unauthorized") {
        Write-Host "خطأ: $Serial unauthorized — وافقي على USB debugging على الجهاز" -ForegroundColor Red
        exit 1
    }
    if ($line -notmatch "device\s*$") {
        Write-Host "خطأ: $Serial ليس في حالة device (offline؟)" -ForegroundColor Red
        exit 1
    }
}

Test-AdbReady $ChildSerial
Test-AdbReady $ParentSerial

if ($ChildSerial -eq $ParentSerial) {
    Write-Host "خطأ: الطفل وولي الأمر يحتاجان جهازين مختلفين" -ForegroundColor Red
    exit 1
}

Set-Location $Root

Write-Host "بناء وتثبيت الطفل على $ChildSerial ..." -ForegroundColor Cyan
$env:ANDROID_SERIAL = $ChildSerial
& .\gradlew.bat installChildDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "بناء وتثبيت ولي الأمر على $ParentSerial ..." -ForegroundColor Cyan
$env:ANDROID_SERIAL = $ParentSerial
& .\gradlew.bat installParentDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
Write-Host "تم التثبيت على المحاكيين." -ForegroundColor Green
