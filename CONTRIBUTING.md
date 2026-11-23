# Contributing

Bug reports and PRs are welcome.

When you open an issue about the drone, please say which firmware version you're on (the
boot log prints it, and the app shows it under Firmware update), your phone and app version,
and what hardware you have (IMU module, motors, battery, any sensors). A USB boot log helps
a lot: battery unplugged, `idf.py monitor` or any serial monitor at 115200.

For bigger changes open an issue first so we can talk about it. Keep PRs to one thing, and
add a line to `tools/CHANGELOG.md` if users will notice the change.

Anything that touches flight has to be tried on a real drone before it gets merged. Props
off for the bench checks and motor test, then a short low hover. Mention in the PR what
you tested on.

The board is done in EasyEDA. If you change it, re-export all the files in `hardware/`
together with the same date in the names.

Contributions fall under the license of the folder you change (see the README).
