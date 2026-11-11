@echo off
rem The Drone Community - flash the XIAO ESP32-S3 over USB (first install).
rem Usage: flash-windows.bat COM5   (check the COM number in Device Manager)
cd /d "%~dp0"
if "%1"=="" (
  echo Usage: flash-windows.bat COM5
  echo Find the COM port in Device Manager under Ports. Hold BOOT while plugging in if none shows.
  exit /b 1
)
python -m esptool version >nul 2>&1 || python -m pip install --user esptool
echo Flashing The Drone Community firmware 1.2.0 to %1 ...
python -m esptool --chip esp32s3 -p %1 -b 460800 --before default_reset --after hard_reset write_flash 0x0 TheDroneCommunity-firmware-1.2.0-full.bin
if errorlevel 1 exit /b 1
echo Done. Unplug USB, connect the battery, and look for the Wi-Fi network ESP-DRONE_...
