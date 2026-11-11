# Changelog

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
