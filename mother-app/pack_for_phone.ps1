# Build complete mother phone package: myrana_mother_phone.zip
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "phone_bundle"
$mainSrc = Join-Path $root "main.py"

if (-not (Test-Path $mainSrc)) {
    Write-Error "main.py missing in mother-app"
}

if (-not (Test-Path $out)) {
    New-Item -ItemType Directory -Path $out | Out-Null
}

Copy-Item $mainSrc $out -Force
$apiSrc = Join-Path (Split-Path $root -Parent) "common\guardian_api.py"
if (-not (Test-Path $apiSrc)) {
    Write-Error "common/guardian_api.py missing"
}
Copy-Item $apiSrc (Join-Path $out "guardian_api.py") -Force

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
