@echo off
title FIRE TV CUSTOM HS VER1 - Rebuild APK
echo ========================================================
echo   Rebuilding FIRE_TV_CUSTOM_HS_VER1.apk...
echo ========================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fast_build.ps1"
echo.
echo Build complete! Press any key to close...
pause >nul
