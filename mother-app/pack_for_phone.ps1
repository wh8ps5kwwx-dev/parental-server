# Pack mother-app files for phone transfer (Pydroid 3)
$out = Join-Path $PSScriptRoot "phone_bundle"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
Copy-Item (Join-Path $PSScriptRoot "main.py") $out -Force
Copy-Item (Join-Path $PSScriptRoot "guardian_api.py") $out -Force
$font = Join-Path $PSScriptRoot "Arabic.ttf"
if (Test-Path $font) { Copy-Item $font $out -Force }
$zip = Join-Path $PSScriptRoot "myrana_mother_phone.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $out "*") -DestinationPath $zip -Force
Write-Host "Ready:" $zip
