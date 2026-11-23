#!/bin/sh
# Double-click on a Mac to flash the drone and install the Android app.
cd "$(dirname "$0")"
sh ./install.sh "$@"
echo
printf "Press Return to close this window."
read -r _
