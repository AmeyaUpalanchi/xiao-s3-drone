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

> **Check motor 2.** The net is labelled `MOTOR2_D8`, but it lands on pin 7 of the
> right-hand XIAO header (U4), which is **D7 / GPIO44**. The firmware follows the copper.
> If motor 2 does not spin, set `MOTOR02_PIN` to `7` (D8) in `idf.py menuconfig`.

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

### Build and flash

```bash
cd firmware
. ~/esp/esp-idf/export.sh      # ESP-IDF v5.0.x
idf.py set-target esp32s3
idf.py build
idf.py -p /dev/cu.usbmodem* flash monitor
```

Wi-Fi: SSID `ESP-DRONE_<mac>`, password `12345678`, drone IP `192.168.43.42`.

## Android app

Open `android-app/` in Android Studio, or build from the terminal:

```bash
cd android-app
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Brand colours are all in `android-app/app/src/main/res/values/colors.xml`.
Photos go to `Pictures/DroneCommunity`, videos to `Movies/DroneCommunity`.
