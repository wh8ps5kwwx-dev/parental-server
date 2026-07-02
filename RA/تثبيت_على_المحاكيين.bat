@echo off
chcp 65001 >nul
echo ========================================
echo  MY Rana — تثبيت عبر Python (USB / محاكي)
echo ========================================
echo.
python "%~dp0..\run_android.py" status
echo.
python "%~dp0..\run_android.py" all
pause
