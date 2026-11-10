@echo off
title Publish CUSTOM FIRE LAUNCHER with CLAUD to GitHub
echo =================================================================
echo   Publishing "CUSTOM-FIRE-LAUNCHER-with-CLAUD" to GitHub...
echo =================================================================
set "PATH=C:\Users\k02799\.gemini\antigravity\scratch\git-tools\mingit\cmd;C:\Users\k02799\.gemini\antigravity\scratch\git-tools\gh\bin;%PATH%"
cd /d "%~dp0"

gh auth status >nul 2>&1
if errorlevel 1 (
    echo.
    echo Step 1: Please sign in to your GitHub account in the browser window...
    echo ^(Copy the 8-character one-time code shown below and paste it at https://github.com/login/device^)
    echo.
    gh auth login --hostname github.com --git-protocol https --web
)

echo.
echo Step 2: Creating GitHub repository "CUSTOM-FIRE-LAUNCHER-with-CLAUD" and pushing files...
gh repo create "CUSTOM-FIRE-LAUNCHER-with-CLAUD" --public --description "CUSTOM FIRE LAUNCHER with CLAUD - Custom ad-free Amazon Fire TV Home Screen Launcher (FIRE TV CUSTOM HS VER1)" --source=. --remote=origin --push

echo.
echo =================================================================
echo   Done! Your project is now live on GitHub!
echo =================================================================
pause
