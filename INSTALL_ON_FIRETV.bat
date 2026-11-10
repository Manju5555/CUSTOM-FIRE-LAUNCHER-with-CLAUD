@echo off
title FIRE TV CUSTOM HS VER1 - One-Click Installer
echo ========================================================
echo   FIRE TV CUSTOM HS VER1 - One-Click Fire TV Installer
echo ========================================================
echo.
set /p FIRE_IP="Enter Fire TV IP address (press Enter for default 192.168.29.29): "
if "%FIRE_IP%"=="" set FIRE_IP=192.168.29.29
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install_to_firetv.ps1" -FireTvIp "%FIRE_IP%:5555"
echo.
echo Done! Press any key to close...
pause >nul
