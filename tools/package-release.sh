#!/bin/sh
# Build the firmware (normal + iOS build) and the Android app, then write the release files:
#
#   dist/TheDroneCommunity-firmware-<VERSION>.zip       firmware for the Android app
#   dist/TheDroneCommunity-firmware-<VERSION>-ios.zip   firmware for Espressif's ESP-Drone iOS app
#   dist/TheDroneCommunity-<VERSION>.apk                Android app
#   dist/TheDroneCommunity-v<VERSION>.zip               everything above plus the guides
#
# Usage: tools/package-release.sh 1.3.0
# Needs: ESP-IDF 5.0 at ~/esp/esp-idf (or IDF_PATH), a JDK 17 (Android Studio's is used if found)
#        and the Android SDK (ANDROID_HOME, or local.properties in android-app/).
set -e
VERSION=${1:?usage: tools/package-release.sh <version>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIST="$ROOT/dist"
IDF_EXPORT="${IDF_PATH:-$HOME/esp/esp-idf}/export.sh"
STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[ -z "$JAVA_HOME" ] && [ -d "$STUDIO_JBR" ] && export JAVA_HOME="$STUDIO_JBR"
[ -z "$ANDROID_HOME" ] && [ ! -f "$ROOT/android-app/local.properties" ] && [ -d "$HOME/Library/Android/sdk" ] \
  && export ANDROID_HOME="$HOME/Library/Android/sdk"

# Firmware version string must match: drone_api.h -> "<VERSION>-tdc"
grep -q "\"$VERSION-tdc\"" "$ROOT/firmware/components/core/crazyflie/modules/interface/drone_api.h" || {
  echo "DRONE_FW_VERSION in drone_api.h is not $VERSION-tdc"; exit 1; }

echo "== firmware (normal build)"
( cd "$ROOT/firmware" && . "$IDF_EXPORT" >/dev/null && idf.py build >/dev/null )

echo "== firmware (iOS build)"
IOS_DIR="$ROOT/firmware/build-ios"
grep -v "DRONE_AUTO_ARM" "$ROOT/firmware/sdkconfig" > "$ROOT/firmware/sdkconfig.ios"
echo "CONFIG_DRONE_AUTO_ARM=y" >> "$ROOT/firmware/sdkconfig.ios"
( cd "$ROOT/firmware" && . "$IDF_EXPORT" >/dev/null \
  && idf.py -B "$IOS_DIR" -D SDKCONFIG="$ROOT/firmware/sdkconfig.ios" build >/dev/null )

echo "== app"
( cd "$ROOT/android-app" && ./gradlew -q assembleRelease )

echo "== package"
mkdir -p "$DIST"

# fw_package <name> <build dir>
fw_package() {
  NAME=$1; B=$2; OUT="$DIST/TheDroneCommunity-firmware-$NAME"
  rm -rf "$OUT" "$OUT.zip"
  mkdir -p "$OUT/parts"
  cp "$B/bootloader/bootloader.bin" "$B/partition_table/partition-table.bin" \
     "$B/ota_data_initial.bin" "$B/ESPDrone.bin" "$OUT/parts/"
  cp "$B/ESPDrone.bin" "$OUT/ESPDrone-$NAME-update.bin"
  ( . "$IDF_EXPORT" >/dev/null && cd "$OUT/parts" && python -m esptool --chip esp32s3 merge_bin \
      -o "../TheDroneCommunity-firmware-$NAME-full.bin" --flash_mode dio --flash_size 8MB --flash_freq 80m \
      0x0 bootloader.bin 0x8000 partition-table.bin 0xf000 ota_data_initial.bin 0x20000 ESPDrone.bin >/dev/null )
  for f in flash-mac-linux.sh flash-windows.bat; do
    sed "s/1\.2\.0/$NAME/g" "$ROOT/tools/$f" > "$OUT/$f"
  done
  chmod +x "$OUT/flash-mac-linux.sh"
  cp "$ROOT/tools/CHANGELOG.md" "$OUT/"
  ( cd "$OUT" && find . -type f ! -name SHA256SUMS.txt | sort | xargs shasum -a 256 > SHA256SUMS.txt )
  ( cd "$DIST" && zip -qr "TheDroneCommunity-firmware-$NAME.zip" "TheDroneCommunity-firmware-$NAME" )
}

fw_package "$VERSION" "$ROOT/firmware/build"
sed "s/{{VERSION}}/$VERSION/g" "$ROOT/tools/README-iOS.template.txt" \
  > "$DIST/README-iOS.tmp"
fw_package "$VERSION-ios" "$IOS_DIR"
mv "$DIST/README-iOS.tmp" "$DIST/TheDroneCommunity-firmware-$VERSION-ios/README-iOS.txt"
( cd "$DIST/TheDroneCommunity-firmware-$VERSION-ios" \
  && find . -type f ! -name SHA256SUMS.txt | sort | xargs shasum -a 256 > SHA256SUMS.txt )
( cd "$DIST" && rm -f "TheDroneCommunity-firmware-$VERSION-ios.zip" \
  && zip -qr "TheDroneCommunity-firmware-$VERSION-ios.zip" "TheDroneCommunity-firmware-$VERSION-ios" )

cp "$ROOT/android-app/app/build/outputs/apk/release/app-release.apk" "$DIST/TheDroneCommunity-$VERSION.apk"

# Combined package
OUT="$DIST/TheDroneCommunity-v$VERSION"
rm -rf "$OUT" "$OUT.zip"
mkdir -p "$OUT"
cp -R "$DIST/TheDroneCommunity-firmware-$VERSION" "$OUT/firmware"
cp -R "$DIST/TheDroneCommunity-firmware-$VERSION-ios" "$OUT/firmware-ios"
cp "$DIST/TheDroneCommunity-$VERSION.apk" "$OUT/"
sed "s/{{VERSION}}/$VERSION/g" "$ROOT/tools/QUICKSTART.template.md" > "$OUT/QUICKSTART.md"
cp "$ROOT/tools/LICENSES.md" "$ROOT/android-app/licenses/Sora-OFL.txt" "$ROOT/tools/CHANGELOG.md" "$OUT/"
cp "$ROOT/docs/install-guide/TDC_XIAO_S3_Drone_Install_Guide.pdf" \
   "$ROOT/docs/quick-guide/XIAO_S3_Drone_Quick_Guide.pdf" "$OUT/"
( cd "$OUT" && find . -type f ! -name SHA256SUMS.txt | sort | xargs shasum -a 256 > SHA256SUMS.txt )
( cd "$DIST" && zip -qr "TheDroneCommunity-v$VERSION.zip" "TheDroneCommunity-v$VERSION" )

echo "Done:"
ls -1 "$DIST"/TheDroneCommunity-firmware-$VERSION.zip "$DIST"/TheDroneCommunity-firmware-$VERSION-ios.zip \
      "$DIST"/TheDroneCommunity-$VERSION.apk "$DIST"/TheDroneCommunity-v$VERSION.zip
