@echo off
cd /d "%~dp0"
echo Sync MYRana from C to E...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\sync_myrana_to_E.ps1"
set ERR=%ERRORLEVEL%
if %ERR% NEQ 0 (
    echo FAILED exit code %ERR%
    pause
    exit /b %ERR%
)
echo.
echo DONE. Open in Android Studio:
echo E:\parent_monitor_project\MYRana
pause
