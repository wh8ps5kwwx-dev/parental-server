# Sync server.py and blocklists into RA before Render deploy
$root = Split-Path -Parent $PSScriptRoot
$ra = $PSScriptRoot

Copy-Item -Force (Join-Path $root "server.py") (Join-Path $ra "server.py")
$srcBl = Join-Path $root "blocklists"
$dstBl = Join-Path $ra "blocklists"
if (Test-Path $srcBl) {
    New-Item -ItemType Directory -Force -Path $dstBl | Out-Null
    Copy-Item -Force (Join-Path $srcBl "*") $dstBl -Recurse
    Write-Host "Copied blocklists to RA\blocklists"
} else {
    Write-Warning "Missing blocklists folder. Run: python blocklists\generate_catalog.py"
}
Write-Host "Ready: server.py, blocklists, Procfile, requirements.txt"
