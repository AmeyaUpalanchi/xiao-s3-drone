# XIAO S3 Drone

A small brushed quad built around the Seeed XIAO ESP32-S3. You fly it from your phone over
the drone's own Wi-Fi. This repo has everything: the PCB, firmware, Android app and the 3D
printed frame.

![PCB1](docs/quick-guide/pcb1-top.png)

The firmware is a port of Espressif's [ESP-Drone](https://github.com/espressif/esp-drone)
(which itself comes from the [Crazyflie](https://github.com/bitcraze/crazyflie-firmware))
to the XIAO S3 and our board, with a few things added on top:

- arming and failsafes handled on the drone, so it disarms if the phone drops on the ground
  and lands if the link or battery goes while flying
- altitude hold if you plug a VL53L1X or VL53L0X into one of the Qwiic ports
- camera stream, photos and recording if you use the XIAO S3 Sense
- motor test, level calibration, Wi-Fi name and OTA updates from the app

## What you need

- PCB1 (files in [hardware/](hardware/)) with a XIAO ESP32-S3 and a GY-521 MPU-6050 module
- 4x 8520 coreless motors, 2 CW and 2 CCW props
- a 1S LiPo with XT30. We fly a GNB 550 mAh
- the [v2 Strong frame](frame/v2-strong/) printed in TPU. Rubber M3 grommets under the board
  help a lot with drift

## Flashing and flying

Grab the firmware zip and the APK from [Releases](../../releases). With the battery
**unplugged**, connect the XIAO over USB-C and run:

```bash
unzip TheDroneCommunity-firmware-<version>.zip
cd TheDroneCommunity-firmware-<version>
./flash-mac-linux.sh        # on Windows: flash-windows.bat COM5
```

Install the APK, unplug USB, plug in the battery and join the `ESP-DRONE_...` network
(password `12345678`). Open the app, Connect, Arm. Run the motor test and level calibration
once before the first flight.

iPhone works too with Espressif's [ESP-Drone](https://apps.apple.com/us/app/id1522247884)
app, but flash the `-ios` firmware instead. That build arms itself when the app connects,
since the iOS app has no arm button.

The [install guide (PDF)](docs/install-guide/TDC_XIAO_S3_Drone_Install_Guide.pdf) goes through
all of this step by step, with troubleshooting.

**Don't plug in USB and the battery at the same time.** Both feed the XIAO's 5V line and
the computer ends up charging the battery.

## Building

Firmware needs ESP-IDF v5.0.x. The target and board settings are already in `sdkconfig`,
so don't run `set-target` (it resets some of them).

```bash
cd firmware
. ~/esp/esp-idf/export.sh
idf.py build flash monitor
```

There are a few build options under ESPDrone Config in `idf.py menuconfig`: the iOS
auto-arm build, an IMU bench test, a motor test that spins each motor at power-up, and
debug prints.

For the app, open `android-app/` in Android Studio, or:

```bash
cd android-app
export ANDROID_HOME=~/Library/Android/sdk
./gradlew assembleDebug
```

`tools/package-release.sh <version>` builds both firmware variants and the app and puts
the release zips in `dist/`.

More detail on the internals (web API, safety logic, IMU orientation) is in
[docs/DEVELOPER.md](docs/DEVELOPER.md). Changes are in [tools/CHANGELOG.md](tools/CHANGELOG.md).

## Folders

```
android-app/   Android app
firmware/      ESP-IDF firmware
hardware/      schematic, gerbers, BOM, pick and place, STEP
frame/         printable frames
docs/          install guide, board quick guide, developer notes
tools/         release script, IMU viewer, changelog
```

## License

Hardware is CERN-OHL-S-2.0, firmware GPL-3.0 (same as ESP-Drone), app GPL-2.0 or later
(it's based on [ESP-Drone-Android](https://github.com/EspressifApps/ESP-Drone-Android) and
the [Crazyflie Android client](https://github.com/bitcraze/crazyflie-android-client)).

Issues and PRs are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).

The Drone Community
