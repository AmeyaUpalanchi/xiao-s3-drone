@echo off
rem Double-click to flash the drone and install the Android app.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
pause
