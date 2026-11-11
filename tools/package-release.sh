#!/bin/sh
# Build the firmware and app, then assemble dist/TheDroneCommunity-v<VERSION>.zip
# Usage: tools/package-release.sh 1.3.0
# Needs: ESP-IDF 5.0 at ~/esp/esp-idf, Android Studio (for its JDK) and the Android SDK.
set -e
VERSION=${1:?usage: tools/package-release.sh <version>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/dist/TheDroneCommunity-v$VERSION"

echo "== firmware"
( cd "$ROOT/firmware" && . ~/esp/esp-idf/export.sh >/dev/null && idf.py build )

echo "== app"
( cd "$ROOT/android-app" && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew -q assembleRelease )

echo "== package"
rm -rf "$OUT" "$OUT.zip"
mkdir -p "$OUT/firmware/parts"
B="$ROOT/firmware/build"
cp "$B/bootloader/bootloader.bin" "$B/partition_table/partition-table.bin" "$B/ota_data_initial.bin" "$B/ESPDrone.bin" "$OUT/firmware/parts/"
cp "$B/ESPDrone.bin" "$OUT/firmware/ESPDrone-$VERSION-update.bin"
( . ~/esp/esp-idf/export.sh >/dev/null && cd "$OUT/firmware/parts" && python -m esptool --chip esp32s3 merge_bin \
    -o "../TheDroneCommunity-firmware-$VERSION-full.bin" --flash_mode dio --flash_size 8MB --flash_freq 80m \
    0x0 bootloader.bin 0x8000 partition-table.bin 0xf000 ota_data_initial.bin 0x20000 ESPDrone.bin )
cp "$ROOT/android-app/app/build/outputs/apk/release/app-release.apk" "$OUT/TheDroneCommunity-$VERSION.apk"
sed "s/{{VERSION}}/$VERSION/g" "$ROOT/tools/QUICKSTART.template.md" > "$OUT/QUICKSTART.md"
for f in flash-mac-linux.sh flash-windows.bat; do
  sed "s/1\.2\.0/$VERSION/g" "$ROOT/tools/$f" > "$OUT/firmware/$f"
done
chmod +x "$OUT/firmware/flash-mac-linux.sh"
cp "$ROOT/tools/LICENSES.md" "$ROOT/android-app/licenses/Sora-OFL.txt" "$OUT/"
[ -f "$ROOT/tools/CHANGELOG.md" ] && cp "$ROOT/tools/CHANGELOG.md" "$OUT/"
( cd "$OUT" && find . -type f ! -name SHA256SUMS.txt | sort | xargs shasum -a 256 > SHA256SUMS.txt )
( cd "$ROOT/dist" && zip -qr "TheDroneCommunity-v$VERSION.zip" "TheDroneCommunity-v$VERSION" )
echo "Done: $OUT.zip"
