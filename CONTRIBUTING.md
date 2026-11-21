# Contributing

Thanks for helping improve the XIAO S3 Drone. Bug reports, fixes, hardware notes and
documentation changes are all welcome.

## Reporting a bug

Open an issue and include:

- **Firmware version.** The app shows it under Drone settings > Firmware update, and the USB
  boot log prints it as `fw 1.2.x-tdc`.
- **App and phone.** App version, phone model, Android or iOS version.
- **Hardware.** PCB revision, IMU module, motors, battery, and any add-ons (height sensor, camera).
- **What happened.** Steps to reproduce it, what you expected, and what you saw.
- **The boot log**, if the problem is on the drone. Plug in USB (battery unplugged) and run
  `idf.py monitor`, or any serial monitor at 115200 baud.

## Proposing a change

For anything bigger than a small fix, open an issue first so we can agree on the approach.

1. Fork the repo and create a branch from `main`.
2. Keep each pull request focused on one change.
3. Follow the style of the code around your change: 4-space indent in C and Java, and
   ESP-IDF conventions in the firmware.
4. Add a line to [tools/CHANGELOG.md](tools/CHANGELOG.md) for user-visible changes.
5. Describe in the pull request what you changed and how you tested it.

## Testing on hardware

Firmware changes that affect flight must be tested on a real drone before they're merged.

- Bench first, **propellers off**: check the boot log, then run the motor test from the app.
- Then a short, low hover over a soft surface.
- Say in the pull request which board, IMU chip and motors you tested with.

**Never connect USB and the battery at the same time.** Flash and read logs on USB only;
fly on the battery only.

## Hardware changes

The board is designed in EasyEDA. When you change it, update every exported file in
[`hardware/`](hardware/) together (schematic, Gerber, BOM, pick-and-place, STEP), use the same
date in the file names, and describe the change in the pull request.

## License

By contributing you agree that your work is released under the license of the part you
changed: CERN-OHL-S-2.0 for `hardware/`, GPL-3.0 for `firmware/`, GPL-2.0-or-later for
`android-app/`.
