# The Drone Community: XIAO ESP32-S3 Drone

Two projects live here:

| Folder | What it is |
|---|---|
| `firmware/` | ESP-Drone flight firmware ported to the Seeed XIAO ESP32-S3 (Sense) on drone PCB1 |
| `android-app/` | ESP-Drone Android app, rebranded, with battery % and camera Live / Photo / Record |

## Firmware

### Pin map (from `Netlist_PCB1_2026-11-10.tel`)

| Function | Net | XIAO pin | GPIO |
|---|---|---|---|
| Motor 1 | MOTOR1_D3 | D3 | 4 |
| Motor 2 | MOTOR2_D8 | D7 (U4 pin 7) | 44 |
| Motor 3 | MOTOR3_D2 | D2 | 3 |
| Motor 4 | MOTOR4_D0 | D0 | 1 |
| I2C SDA (MPU6050 + 2x Qwiic) | I2C_SDA | D4 | 5 |
| I2C SCL | I2C_SCL | D5 | 6 |
| Battery sense (100k/100k) | VBAT_SENSE | D1 | 2 (ADC1_CH1) |
| Status LED | on-board user LED | - | 21 (active low) |
| MPU6050 INT | not routed | - | polled at 1 kHz |

> **Motor 2 is on D7.** The net is labelled `MOTOR2_D8`, but it lands on pin 7 of the
> right-hand XIAO header (U4), which is **D7 / GPIO44** (checked with a multimeter,
> 2026-11-18). The firmware uses GPIO44.

GPIO44 is UART0 RX, so the console runs on the XIAO's USB-C port (USB-Serial/JTAG).

### Camera (XIAO ESP32-S3 Sense)

When a Sense camera board is attached, the drone serves:

| URL | Returns |
|---|---|
| `http://192.168.43.42/status` | JSON with `camera`, `sensor`, `battery_v`, `battery_pct` |
| `http://192.168.43.42/capture` | One JPEG photo |
| `http://192.168.43.42:81/stream` | MJPEG live stream (VGA, 20 fps max) |

Without a camera the drone flies normally and `/status` reports `"camera": false`.
Resolution, JPEG quality and frame-rate cap are in `menuconfig` under
*ESPDrone Config -> camera*.

### Flight safety (new)

The drone starts **disarmed**. The app arms it with the Arm button, and the motors
can only spin while armed. All of this runs on the drone itself (`flight_safety.c`),
so it still works if the phone app freezes.

| App control | Firmware parameter | What happens |
|---|---|---|
| Arm / Disarm | `safety.armed` | Arming is only accepted while the throttle is at zero |
| Land | `safety.land` | Levels out, sinks for 3 s at 85 % of hover thrust, ramps to zero, disarms |
| Hold to stop | `safety.kill` | Cuts the motors immediately and disarms |

The drone also lands by itself if the Wi-Fi link is silent for 0.5 s or the battery
stays below 3.2 V for 5 s. There is no altitude sensor on PCB1, so landing is a
timed descent: start it from low height for the softest touchdown.

### Drone tools (firmware 1.1.0-tdc)

The app's gear button opens **Drone settings**. Each tool calls the drone's web API.
Every tool that moves motors or changes settings only works while the drone is disarmed.

| Tool | Endpoint | Notes |
|---|---|---|
| Motor test | `POST /api/motor?m=1..4` (0 = all) | 20 % for 0.7 s. M1 front-right, M2 back-right, M3 back-left, M4 front-left |
| Find my drone | `POST /api/beep` | Beeps through the motors three times |
| Level calibration | `POST /api/level`, `/api/level/reset` | Stored in flash (NVS), survives reboots |
| Wi-Fi name / password | `GET`/`POST /api/wifi` | SSID becomes `ESP-DRONE_<name>`, drone restarts |
| Firmware update | `POST /api/ota` | Upload `ESPDrone.bin`; only drone firmware is accepted |
| Status | `GET /status` | Battery, state, height, camera, SSID, firmware version |

Flying style (Beginner / Normal / Sport) and the low-battery warnings at 25 % and 15 %
are handled in the app.

**Height sensor.** Plug a VL53L1X board into either Qwiic connector (CN5/CN6). The
firmware detects it at boot and switches to altitude hold using the Kalman estimator.
The throttle stick centre then means "hold height". Land descends at 0.3 m/s and
disarms on touchdown, and the app shows a Take off button that climbs to about 0.5 m.

**Flash layout changed** to two app slots for wireless updates (`partitions.csv`).
Flash once over USB with `idf.py flash` or `dist/firmware/flash.sh`. After that,
updates can go over Wi-Fi from the app.

### IMU orientation check

The MPU6050 axis mapping lives in `firmware/components/config/include/imu_orientation.h`
(on PCB1: forward = +sensor Y, left = -sensor X, up = +sensor Z, confirmed by tilt test).
To re-check it, enable *ESPDrone Config -> system -> IMU bench test* in `idf.py menuconfig`,
flash, and run `python3 tools/imu-viewer/server.py`, then open http://localhost:8765.
The bench test holds all motor pins LOW and never starts the flight stack. Turn it off again
before flying.

### Build and flash

```bash
cd firmware
. ~/esp/esp-idf/export.sh      # ESP-IDF v5.0.x
idf.py set-target esp32s3
idf.py build
idf.py -p /dev/cu.usbmodem* flash monitor
```

Wi-Fi: SSID `ESP-DRONE_<mac>`, password `12345678`, drone IP `192.168.43.42`.

## Release package

`dist/TheDroneCommunity-v1.2.1.zip` holds everything a community member needs:
- the app
- a single-file firmware image with one-step flash scripts for Mac, Linux and Windows
- the over-the-air update file
- a quick-start guide

Rebuild it for a new version with:

```bash
tools/package-release.sh 1.3.0
```

### Firmware behaviour the app relies on (1.2.0)

- Boots **disarmed**; the app arms it (`safety.armed`).
- Disarms itself after 20 s idle on the ground, or 2 s after the app disconnects on the ground.
- Lands itself if the link drops in the air or the battery is critical.
- Picks the quietest Wi-Fi channel (1, 6 or 11) at boot (`CONFIG_DRONE_WIFI_AUTO_CHANNEL`).
- Flight-loop debug printing is off unless `CONFIG_DRONE_DEBUG_PRINTS` is enabled.

## Android app

Open `android-app/` in Android Studio, or build from the terminal:

```bash
cd android-app
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Font: Sora (SIL OFL, bundled in `res/font`). Brand colours are all in `android-app/app/src/main/res/values/colors.xml`.
Photos go to `Pictures/DroneCommunity`, videos to `Movies/DroneCommunity`.
