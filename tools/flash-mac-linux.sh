#!/bin/sh
# The Drone Community - flash the XIAO ESP32-S3 over USB (first install).
# Usage:   ./flash-mac-linux.sh                 (finds the port by itself)
#          ./flash-mac-linux.sh /dev/cu.usbmodem1101
# Needs Python 3. Installs esptool if missing.
cd "$(dirname "$0")"
PORT="$1"
if [ -z "$PORT" ]; then
  PORT=$(ls /dev/cu.usbmodem* /dev/ttyACM* 2>/dev/null | head -n 1)
fi
if [ -z "$PORT" ]; then
  echo "No XIAO found. Plug it in with a data USB-C cable."
  echo "If it still doesn't show up: hold the BOOT button while plugging it in."
  exit 1
fi
python3 -m esptool version >/dev/null 2>&1 || python3 -m pip install --user esptool
echo "Flashing The Drone Community firmware 1.2.0 to $PORT ..."
python3 -m esptool --chip esp32s3 -p "$PORT" -b 460800 --before default_reset --after hard_reset \
  write_flash 0x0 TheDroneCommunity-firmware-1.2.0-full.bin && \
  echo "Done. Unplug USB, connect the battery, and look for the Wi-Fi network ESP-DRONE_..."
