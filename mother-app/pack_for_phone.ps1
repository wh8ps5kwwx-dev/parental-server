# Build complete mother phone package: myrana_mother_phone.zip
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "phone_bundle"
$mainSrc = Join-Path $root "main.py"

# Ensure main.py exists (phone_bundle is rebuilt each run)
$bundleMain = Join-Path $root "phone_bundle\main.py"
if (Test-Path $bundleMain) {
    Copy-Item $bundleMain $mainSrc -Force
}
if (-not (Test-Path $mainSrc)) {
    Write-Error "main.py missing in mother-app"
}

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null

Copy-Item $mainSrc $out -Force
Copy-Item (Join-Path $root "guardian_api.py") $out -Force

$font = Join-Path $root "Arabic.ttf"
if (-not (Test-Path $font)) {
    Write-Error "Arabic.ttf required in mother-app folder"
}
Copy-Item $font $out -Force

Copy-Item (Join-Path $root "requirements.txt") $out -Force
Copy-Item (Join-Path $root "README_PHONE.md") $out -Force

$required = @("main.py", "guardian_api.py", "Arabic.ttf", "requirements.txt", "README_PHONE.md")
foreach ($name in $required) {
    if (-not (Test-Path (Join-Path $out $name))) {
        Write-Error "Missing: $name"
    }
}

$zip = Join-Path $root "myrana_mother_phone.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $out "*") -DestinationPath $zip -Force

Write-Host "OK: $zip"
