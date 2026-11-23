# Changelog

## 1.2.5
- One-click installer: double-click Install.command (Mac) or Install.bat (Windows) in the release
  folder, or run one command, and it flashes the drone over USB and installs the app on an Android
  phone plugged in with USB debugging. It downloads esptool and adb by itself
- The APK is now signed with a proper release key. If you have 1.2.4 or older installed, uninstall
  it once (the installer does this for you)
- No firmware changes apart from the version number

## 1.2.4
- Firmware: VL53L0X height sensor support on a Qwiic port (CN5/CN6): altitude hold, Take off and
  height-based landing, like the VL53L1X. Best below about 1 m; the VL53L1X reaches further
- Firmware: the VL53L0X driver no longer reports a sensor when none is fitted
- App: Take off and height mode also appear for a VL53L0X
- App and firmware share one version number from 1.2.4 on
- Release files: separate firmware zips for Android and iOS, the APK, and a combined package
  with the install guide

## 1.2.3
- Firmware: fixed the drone spinning (yawing) steadily in flight. The motor mix expected the
  props the other way round from the XIAO S3 drone build (M1 + M3 CW, M2 + M4 CCW), so the yaw
  control pushed the wrong way
- Firmware: separate iOS build (1.2.3-ios) for Espressif's ESP-Drone app on the App Store. That app
  has no Arm button, so this build arms by itself while the app is connected with the throttle at zero

## 1.2.2
- App: Arm now reaches the drone over Wi-Fi. The app skipped downloading the drone's parameter
  list on the Wi-Fi link, so it never found safety.armed and the sticks did nothing
- App: the Arm button is enabled as soon as the drone link is fully set up
- App: Connect no longer fails with "No SoftAP connection" on Android 10+ phones that are on the
  drone's Wi-Fi (Android hides the Wi-Fi network ID from apps without location permission)
- Firmware: works with GY-521 modules that carry an MPU6500-family chip (WHO_AM_I 0x70, 0x71, 0x73,
  0x74, 0x78) as well as a genuine MPU-6050 (0x68). These were rejected before ("MPU6050 I2C
  connection [FAIL]"), so the drone refused to fly
- Firmware: power-on IMU self-test uses limits that suit those chips (their response is larger)
- Firmware: optional motor auto test build (menuconfig, off by default): spins M1..M4 one by one
  on battery power, motor N = N pulses; never spins while a computer is on USB

## 1.2.1
- App: Connect now says "Searching…" while Android looks for the drone, gives up after 20 s instead of 45 s,
  and shows a "No drone found" dialog with what to check, Try again and Wi-Fi settings
- App: the Connect button no longer stays on "Connecting…" when the drone link fails
- Firmware: a missing or miswired MPU-6050 no longer makes the drone reboot in a loop; it stays on
  Wi-Fi, refuses to fly and reports "imu": false in /status

## 1.2.0
- Drone disarms by itself when idle on the ground for 20 s or when the app disconnects on the ground
- Drone picks the quietest Wi-Fi channel (1, 6 or 11) at boot; shown in Drone settings
- Flight-loop debug printing switched off (could stall the 1 kHz loop over USB)
- Single-file firmware image and one-step flash scripts for Mac, Linux and Windows

## 1.1.0
- Drone settings: flying style, motor test, level calibration, find my drone, Wi-Fi name and password, firmware update over Wi-Fi
- Low-battery warnings at 25 % and 15 %
- VL53L1X height sensor support: altitude hold, Take off, height-based landing

## 1.0.0
- Port to Seeed XIAO ESP32-S3 drone PCB1 (pin map from the netlist), fixed battery reading
- Arm / Disarm, auto-land, hold-to-stop, failsafe landing on signal loss
- Live video, photo and MP4 recording from the XIAO ESP32-S3 Sense camera
- Rebranded app (The Drone Community), Sora typography, How to fly guide
