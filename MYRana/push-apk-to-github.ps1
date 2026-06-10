# رفع APK إلى GitHub من القرص E: (المستودع: E:\parental-server-deploy)
$ErrorActionPreference = "Stop"
$src = "C:\Users\rannn\AndroidStudioProjects\MYRana\releases"
$repo = "E:\parental-server-deploy"

foreach ($f in @("myrana-child-debug.apk", "myrana-parent-debug.apk")) {
    if (-not (Test-Path "$src\$f")) {
        Write-Host "بناء APK أولاً: cd MYRana; .\gradlew assembleChildDebug assembleParentDebug"
        exit 1
    }
}

New-Item -ItemType Directory -Force -Path "$repo\releases" | Out-Null
Copy-Item "$src\myrana-child-debug.apk" "$repo\releases\app-child-debug.apk" -Force
Copy-Item "$src\myrana-parent-debug.apk" "$repo\releases\app-parent-debug.apk" -Force

Set-Location $repo
git add releases/app-child-debug.apk releases/app-parent-debug.apk .gitignore .gitattributes README.md MYRana/README.md MYRana/LINKS.md "MYRana/تشغيل_جوالين.md"
git commit -m "Restore APK download links in releases without LFS" 2>$null
git push origin main
Write-Host "تم. جرّبي:"
Write-Host "https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-child-debug.apk"
Write-Host "https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-parent-debug.apk"
