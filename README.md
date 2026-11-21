<h1 align="center">XIAO S3 Drone</h1>

<p align="center">
  An open-source micro quadcopter built on the Seeed XIAO ESP32-S3, flown from your phone over Wi-Fi.
</p>

<p align="center">
  <a href="hardware/LICENSE"><img src="https://img.shields.io/badge/hardware-CERN--OHL--S--2.0-blue" alt="Hardware license: CERN-OHL-S-2.0"></a>
  <a href="firmware/LICENSE"><img src="https://img.shields.io/badge/firmware-GPL--3.0-blue" alt="Firmware license: GPL-3.0"></a>
  <a href="android-app/LICENSE.txt"><img src="https://img.shields.io/badge/app-GPL--2.0%2B-blue" alt="App license: GPL-2.0+"></a>
  <img src="https://img.shields.io/badge/ESP--IDF-v5.0-red" alt="ESP-IDF v5.0">
  <img src="https://img.shields.io/badge/board-XIAO%20ESP32--S3-green" alt="Board: Seeed XIAO ESP32-S3">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
</p>

<p align="center">
  <img src="docs/quick-guide/pcb1-top.png" alt="PCB1 flight board" width="45%">
  &nbsp;
  <img src="frame/renders/assembly_iso.png" alt="Drone on the TPU frame" width="45%">
</p>

<p align="center">
  <a href="docs/install-guide/TDC_XIAO_S3_Drone_Install_Guide.pdf"><b>Install guide</b></a> ·
  <a href="hardware/README.md"><b>Hardware</b></a> ·
  <a href="../../releases"><b>Releases</b></a> ·
  <a href="docs/DEVELOPER.md"><b>Developer notes</b></a> ·
  <a href="tools/CHANGELOG.md"><b>Changelog</b></a>
</p>

---

## Contents

- [Features](#features)
- [Hardware at a glance](#hardware-at-a-glance)
- [Quick start](#quick-start)
- [Building from source](#building-from-source)
- [Add-ons](#add-ons)
- [Repository layout](#repository-layout)
- [Documentation](#documentation)
- [Safety](#safety)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

## Features

- **Phone control over Wi-Fi.** The drone runs its own access point. Fly it with the Android app,
  or with Espressif's ESP-Drone app on iPhone.
- **Flight safety on the drone itself.** Boots disarmed, disarms when idle or when the phone
  disconnects on the ground, lands automatically on link loss or low battery, and has a
  hold-to-stop motor cut. All of it runs in firmware, so a frozen app can't override it.
- **Altitude hold.** Plug a VL53L1X or VL53L0X time-of-flight sensor into a Qwiic port; the drone
  detects it at boot and holds height, with one-tap take-off and soft landing.
- **Live video.** With the XIAO ESP32-S3 Sense camera: MJPEG stream, photos and MP4 recording.
- **On-board tools.** Motor test, level calibration, flying styles, find-my-drone beeper,
  Wi-Fi name and password, and over-the-air firmware updates, all from the app.
- **Open hardware.** Schematic, Gerbers, BOM with LCSC part numbers, pick-and-place and a 3D model,
  plus a parametric 3D-printable frame.

## Hardware at a glance

| | |
|---|---|
| Flight controller | Seeed XIAO ESP32-S3 (240 MHz dual-core, Wi-Fi, 8 MB flash, 8 MB PSRAM) |
| IMU | MPU-6050 on a GY-521 module (MPU6500-family clones supported) |
| Board | PCB1: 50 × 50 mm, 2 layers, 4 × M3 on a 42.27 mm square |
| Motors | 4 × 8520 coreless brushed, MOSFET-driven, JST SH 2-pin |
| Propellers | 75 mm, 2 CW + 2 CCW |
| Battery | 1S LiPo, XT30, with reverse-polarity and TVS protection |
| Power | TPS61023 boost converter: 5 V for the XIAO from the 1S pack |
| Expansion | 2 × Qwiic (JST SH 4-pin, 3.3 V I²C) |
| Frame | TPU, ducted, 171.7 × 171.7 mm, 26 g printed |

Full details, pin map and ordering steps: [hardware/README.md](hardware/README.md).

## Quick start

You need an assembled drone, a USB-C data cable and Python 3.

**1. Download** the firmware and app zips from the [latest release](../../releases).

**2. Flash** the drone with the battery unplugged:

```bash
unzip TheDroneCommunity-firmware-<version>.zip
cd TheDroneCommunity-firmware-<version>
./flash-mac-linux.sh          # Windows: flash-windows.bat COM5
```

**3. Install** `TheDroneCommunity-<version>.apk` on an Android phone.

**4. Fly.** Unplug USB, connect the battery, join the Wi-Fi network `ESP-DRONE_…`
(password `12345678`), open the app and tap **Connect**, then **Arm**.

Before the first flight, run the motor test and level calibration from the app. The
[install guide](docs/install-guide/TDC_XIAO_S3_Drone_Install_Guide.pdf) covers every step,
including iPhone setup and troubleshooting.

## Building from source

### Firmware

Requires [ESP-IDF v5.0.x](https://docs.espressif.com/projects/esp-idf/en/v5.0/esp32s3/get-started/).

```bash
cd firmware
. ~/esp/esp-idf/export.sh
idf.py set-target esp32s3
idf.py build
idf.py -p /dev/cu.usbmodem* flash monitor
```

Build options live in `idf.py menuconfig` under **ESPDrone Config**:

| Option | Effect |
|---|---|
| `DRONE_AUTO_ARM` | iOS build: arms automatically for the ESP-Drone App Store app |
| `DRONE_IMU_BENCH_TEST` | Replaces flight with an IMU orientation bench test |
| `DRONE_MOTOR_AUTO_TEST` | Spins M1 to M4 in turn at power-up, then stops |
| `DRONE_DEBUG_PRINTS` | Prints attitude, commands and motor output at 10 Hz |

### Android app

Requires Android Studio (or JDK 17 and the Android SDK).

```bash
cd android-app
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

### Release package

```bash
tools/package-release.sh <version>
```

Builds the firmware and app and writes `dist/TheDroneCommunity-v<version>.zip` with flash
scripts, the over-the-air update image and the guides.

## Add-ons

| Add-on | How to fit it | What you get |
|---|---|---|
| VL53L1X distance sensor | Qwiic port (CN5 or CN6), facing the floor | Altitude hold up to about 3 m indoors |
| VL53L0X distance sensor | Same | Altitude hold up to about 1 m |
| XIAO ESP32-S3 Sense camera | Use the Sense version of the XIAO | Live video, photos, recording |
| iPhone | Flash the `-ios` firmware, install [ESP-Drone](https://apps.apple.com/us/app/id1522247884) | Flying from iOS (the drone arms itself when the app connects) |

## Repository layout

```
.
├── android-app/     Android app (Android Studio project)
├── docs/
│   ├── install-guide/   Install guide (PDF + HTML source)
│   ├── quick-guide/     Board quick guide (PDF + source)
│   └── DEVELOPER.md     Internals: web API, safety system, pin map
├── firmware/        Flight firmware (ESP-IDF, based on ESP-Drone / Crazyflie)
├── frame/           Parametric OpenSCAD frame, STLs and renders
├── hardware/        Schematic, Gerbers, BOM, pick-and-place, STEP model
└── tools/           Release script, IMU orientation viewer, changelog
```

## Documentation

| Document | For |
|---|---|
| [Install guide](docs/install-guide/TDC_XIAO_S3_Drone_Install_Guide.pdf) | Flashing, Android and iPhone setup, pre-flight checks, troubleshooting |
| [Quick guide](docs/quick-guide/XIAO_S3_Drone_Quick_Guide.pdf) | Board tour, wiring, status lights, programming |
| [Hardware](hardware/README.md) | Schematic, bill of materials, ordering and assembly |
| [Frame](frame/README.md) | Printing the frame, dimensions, weight |
| [Developer notes](docs/DEVELOPER.md) | Web API, flight safety, IMU mapping, release builds |
| [Changelog](tools/CHANGELOG.md) | What changed in each version |

## Safety

- **Never connect USB and the battery at the same time.** Both feed the XIAO's 5 V line; the
  computer would charge the battery and its USB port can shut down.
- Remove the propellers for flashing, bench tests and the motor test.
- With the iOS build, the motors spin as soon as the throttle rises while an app is connected.
- Fly low and over a soft surface until you know the drone.
- LiPo batteries can catch fire if damaged, over-charged or over-discharged. Charge them with a
  proper LiPo charger and never leave them charging unattended.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to report
bugs, propose changes and test them on real hardware.

## License

| Part | License |
|---|---|
| Hardware design (`hardware/`) | [CERN-OHL-S-2.0](hardware/LICENSE) |
| Firmware (`firmware/`) | [GPL-3.0](firmware/LICENSE) |
| Android app (`android-app/`) | [GPL-2.0-or-later](android-app/LICENSE.txt) |
| Sora typeface | SIL Open Font License 1.1 |

## Acknowledgements

Built on [ESP-Drone](https://github.com/espressif/esp-drone) by Espressif Systems and the
[Crazyflie firmware](https://github.com/bitcraze/crazyflie-firmware) by Bitcraze. The app is based
on [ESP-Drone-Android](https://github.com/EspressifApps/ESP-Drone-Android) and the
[Crazyflie Android client](https://github.com/bitcraze/crazyflie-android-client).

Maintained by **The Drone Community**.
