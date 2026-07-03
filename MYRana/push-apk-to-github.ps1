# رفع APK إلى GitHub — من مجلد المشروع الحالي
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$myrana = Join-Path $root "MYRana"
$childSrc = Join-Path $myrana "app\build\outputs\apk\child\debug\app-child-debug.apk"
$parentSrc = Join-Path $myrana "app\build\outputs\apk\parent\debug\app-parent-debug.apk"
$releases = Join-Path $myrana "releases"

foreach ($f in @($childSrc, $parentSrc)) {
    if (-not (Test-Path $f)) {
        Write-Host "بناء APK أولاً:"
        Write-Host "  cd MYRana"
        Write-Host "  .\gradlew assembleChildDebug assembleParentDebug"
        exit 1
    }
}

New-Item -ItemType Directory -Force -Path $releases | Out-Null
Copy-Item $childSrc (Join-Path $releases "app-child-debug.apk") -Force
Copy-Item $parentSrc (Join-Path $releases "app-parent-debug.apk") -Force

Set-Location $root
git add MYRana/releases/app-child-debug.apk MYRana/releases/app-parent-debug.apk `
    MYRana/LINKS.md MYRana/README.md "MYRana/تشغيل_جوالين.md" README.md ANDROID_README.md MYRana/push-apk-to-github.ps1

$msg = "Update APK releases and download links to MYRana/releases (latest build)"
git commit -m $msg 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "لا تغييرات جديدة للرفع أو فشل الـ commit."
} else {
    git push origin main
}

Write-Host ""
Write-Host "روابط التحميل المحدّثة:"
Write-Host "https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/MYRana/releases/app-child-debug.apk"
Write-Host "https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/MYRana/releases/app-parent-debug.apk"
