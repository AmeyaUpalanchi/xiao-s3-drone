#!/bin/sh
# The Drone Community: flash the drone and install the Android app in one go.
#
#   curl -fsSL https://raw.githubusercontent.com/AmeyaUpalanchi/xiao-s3-drone/main/tools/installer/install.sh | sh
#   ... | sh -s -- --ios            iPhone build (ESP-Drone app)
#   ... | sh -s -- --firmware-only  just the drone
#   ... | sh -s -- --app-only       just the phone
#
# Inside an unzipped release you can also double-click Install.command (Mac) or run ./install.sh.
set -e
RAW="https://raw.githubusercontent.com/AmeyaUpalanchi/xiao-s3-drone/main/tools/installer/tdc_install.py"
HOME_DIR="$HOME/.tdc-drone"
VENV="$HOME_DIR/venv"
HERE=""
case "$0" in *install.sh) HERE=$(cd "$(dirname "$0")" && pwd) ;; esac   # empty when piped from curl

if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 is needed."
  if [ "$(uname)" = "Darwin" ]; then
    echo "A window will ask to install the command line tools. Accept, then run this again."
    xcode-select --install 2>/dev/null || true
  else
    echo "Install it with your package manager, e.g.: sudo apt install python3 python3-venv"
  fi
  exit 1
fi

mkdir -p "$HOME_DIR"
if [ ! -x "$VENV/bin/python" ]; then
  echo "Setting up esptool (first run only)..."
  python3 -m venv "$VENV"
fi
if ! "$VENV/bin/python" -c "import esptool, serial" 2>/dev/null; then
  # Old pips (macOS ships 20.x) can't use prebuilt wheels and try to compile instead
  "$VENV/bin/python" -m pip install -q --disable-pip-version-check --upgrade pip
  "$VENV/bin/python" -m pip install -q --disable-pip-version-check --prefer-binary "esptool==4.8.1" "pyserial>=3.5"
fi

if [ -n "$HERE" ] && [ -f "$HERE/tdc_install.py" ]; then
  SCRIPT="$HERE/tdc_install.py"
else
  SCRIPT="$HOME_DIR/tdc_install.py"
  curl -fsSL "$RAW" -o "$SCRIPT"
fi

# Running from an unzipped release: use the files next to us instead of downloading
DIR_ARG=""
if [ -n "$HERE" ] && { [ -d "$HERE/firmware" ] || ls "$HERE"/*-full.bin >/dev/null 2>&1; }; then
  DIR_ARG="--dir $HERE"
fi

# shellcheck disable=SC2086
exec "$VENV/bin/python" "$SCRIPT" $DIR_ARG "$@"
