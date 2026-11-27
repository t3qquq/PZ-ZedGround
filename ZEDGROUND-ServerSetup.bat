@echo off
setlocal enableextensions
REM ZEDGROUND - dedicated server setup. Double-click, choose 1 (Install), pick the .bat you start the server with.
REM Puts the Halo Drop agent and ZombieBuddy on the server. The script is in the server-setup folder next to this file.
if not exist "%~dp0server-setup\ZEDGROUND-ServerSetup.ps1" (
    echo [ZEDGROUND] server-setup\ZEDGROUND-ServerSetup.ps1 not found next to this file.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0server-setup\ZEDGROUND-ServerSetup.ps1"
echo.
pause
