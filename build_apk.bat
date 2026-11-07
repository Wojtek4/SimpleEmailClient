@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo  Simple Mail - Android APK Build Runner
echo ==========================================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_apk.ps1" %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Build finished with errors.
    pause
    exit /b %ERRORLEVEL%
)

echo.
pause
