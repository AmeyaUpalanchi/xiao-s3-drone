# The Drone Community: XIAO ESP32-S3 Drone, v{{VERSION}}

## What's in this folder

| File | What it's for |
|---|---|
| `TheDroneCommunity-{{VERSION}}.apk` | The Android app (Android 8 or newer) |
| `firmware/TheDroneCommunity-firmware-{{VERSION}}-full.bin` | Full drone firmware for the first install over USB |
| `firmware/ESPDrone-{{VERSION}}-update.bin` | Firmware update to send from the app over Wi-Fi |
| `firmware/flash-mac-linux.sh`, `firmware/flash-windows.bat` | One-step USB flashing |
| `firmware-ios/` | The same firmware built for Espressif's ESP-Drone iPhone app (see `README-iOS.txt`) |
| `TDC_XIAO_S3_Drone_Install_Guide.pdf` | Step-by-step install guide (2 pages) |

## 1. Flash the drone (once, over USB)

Plug the XIAO ESP32-S3 into your computer with a data USB-C cable. Take the battery off.

- **Mac / Linux:** open Terminal in the `firmware` folder and run `./flash-mac-linux.sh`
- **Windows:** open Command Prompt in the `firmware` folder and run `flash-windows.bat COM5`
  (use the COM number from Device Manager)
- **No Python?** Open https://espressif.github.io/esptool-js/ in Chrome, connect,
  and flash `TheDroneCommunity-firmware-{{VERSION}}-full.bin` at address **0x0**.

If the board isn't found, hold its **BOOT** button while plugging it in.

Later versions can be installed from the app: gear, then Firmware update, then pick `ESPDrone-…-update.bin`.

## 2. Install the app

Copy `TheDroneCommunity-{{VERSION}}.apk` to the phone and open it. Android asks you to allow
installing from this source the first time.

## 3. First flight

1. Put the drone on flat ground and connect the battery. Keep it still for 3 seconds.
2. Open the app and tap **Connect**. Allow the drone's Wi-Fi when Android asks.
3. Open the gear, run the **Motor test** with the propellers off, then **Calibrate** the level.
4. Back on the flight screen, let go of the left stick and tap **Arm**.
5. Push the left stick up slowly to take off. Steer with the right stick.
6. Lower the left stick or tap **Land**, then **Disarm**.

**Stopping in an emergency:** hold **Hold to stop** for half a second. The motors cut instantly.

**Safety the drone handles itself:**
- It lands if the Wi-Fi drops while flying.
- It disarms after 20 seconds idle on the ground, or if the phone disconnects.
- It warns you at 25% and 15% battery.

## Optional hardware

- **XIAO ESP32-S3 Sense camera board:** live video, photos and recording in the app.
- **VL53L1X or VL53L0X height sensor on a Qwiic port:** the drone holds its height by itself, and a Take off button appears.

Default Wi-Fi: `ESP-DRONE_<id>`, password `12345678`. Change both in the app: gear, then Wi-Fi.
