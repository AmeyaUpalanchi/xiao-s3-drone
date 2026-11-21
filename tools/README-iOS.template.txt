The Drone Community firmware {{VERSION}} - iOS build
================================================

Use this build with Espressif's "ESP-Drone" app from the App Store (iPhone / iPad).

How it differs from the normal {{VERSION}} firmware
- That app has no Arm button, so this firmware arms by itself while the app is
  connected and the throttle is at zero. RAISING THE THROTTLE SPINS THE MOTORS
  STRAIGHT AWAY. Keep fingers and props clear whenever the app is connected.
- Still active: disarm when the app disconnects on the ground (2 s) or after 20 s
  idle on the ground, failsafe landing when the link is lost in the air, and
  landing on low battery.
- Motor test, level calibration, Wi-Fi settings and firmware update (Android app)
  work while the drone sits on the ground with the throttle at zero: the drone
  disarms for them and waits 15 s before arming itself again.
- The Android app (TheDroneCommunity) also works with this build.

Flash
- Full image over USB at 0x0: TheDroneCommunity-firmware-{{VERSION}}-ios-full.bin
  (flash-mac-linux.sh / flash-windows.bat do this)
- Over Wi-Fi from the Android app: ESPDrone-{{VERSION}}-ios-update.bin
- The drone reports "fw {{VERSION}}-tdc-ios" in /status and on the USB console.

Fly with the ESP-Drone iOS app
1. Power the drone from the battery (USB unplugged).
2. iPhone Settings > Wi-Fi: join ESP-DRONE_xxxxxxxxxxxx, password 12345678.
3. Open ESP-Drone, connect, keep the throttle at the bottom for 1 s, then fly.
