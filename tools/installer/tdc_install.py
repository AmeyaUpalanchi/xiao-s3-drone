"""The Drone Community installer: flashes the drone and installs the Android app.

Run through install.sh / Install.command / Install.bat, which set up Python and esptool.

    tdc_install.py [--ios] [--firmware-only | --app-only] [--port PORT] [--dir DIR]

--dir points at an unzipped release (firmware/ and the .apk). Without it the latest
release is downloaded from GitHub.
"""
import argparse
import glob
import hashlib
import io
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

REPO = "AmeyaUpalanchi/xiao-s3-drone"
APP_ID = "com.thedronecommunity.drone"
ESPRESSIF_VID = 0x303A
HOME = os.path.join(os.path.expanduser("~"), ".tdc-drone")


def say(msg=""):
    print(msg, flush=True)


def step(msg):
    say("\n== " + msg)


def fail(msg):
    say("\nERROR: " + msg)
    sys.exit(1)


def ask(prompt):
    try:
        return input(prompt).strip()
    except EOFError:
        return ""


def download(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "tdc-installer"})
    with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def check_sums(folder):
    sums = os.path.join(folder, "SHA256SUMS.txt")
    if not os.path.exists(sums):
        return
    for line in open(sums):
        digest, name = line.split(None, 1)
        name = name.strip().lstrip("*")
        path = os.path.join(folder, name)
        if not os.path.exists(path):
            fail(name + " is missing from " + folder + ". Download the release again.")
        with open(path, "rb") as f:
            if hashlib.sha256(f.read()).hexdigest() != digest:
                fail("checksum mismatch for " + name + ". Download it again.")


# ---------------------------------------------------------------- release files

def latest_version():
    # github.com/<repo>/releases/latest redirects to .../tag/v<version>. Unlike the API,
    # this has no 60-requests-an-hour limit.
    req = urllib.request.Request("https://github.com/%s/releases/latest" % REPO,
                                 headers={"User-Agent": "tdc-installer"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            tag = r.geturl().rstrip("/").rsplit("/", 1)[-1]
    except Exception as e:
        fail("couldn't reach GitHub (%s). Check the internet connection." % e)
    if not tag.startswith("v"):
        fail("couldn't find the latest release on GitHub.")
    return tag[1:]


def fetch_release(ios):
    step("Getting the latest release from GitHub")
    version = latest_version()
    say("Version " + version)
    base = "https://github.com/%s/releases/download/v%s/" % (REPO, version)
    work = tempfile.mkdtemp(prefix="tdc-")
    fw_name = "TheDroneCommunity-firmware-%s%s.zip" % (version, "-ios" if ios else "")
    apk_name = "TheDroneCommunity-%s.apk" % version
    for n in (fw_name, apk_name):
        say("Downloading " + n)
        try:
            download(base + n, os.path.join(work, n))
        except Exception as e:
            fail("download of %s failed (%s)." % (n, e))
    with zipfile.ZipFile(os.path.join(work, fw_name)) as z:
        z.extractall(work)
    fw_dir = os.path.join(work, fw_name[:-4])
    return version, fw_dir, os.path.join(work, apk_name)


def local_release(d, ios):
    fw_dir = os.path.join(d, "firmware-ios" if ios else "firmware")
    if not os.path.isdir(fw_dir):          # running from inside a firmware zip
        fw_dir = d
    apks = glob.glob(os.path.join(d, "*.apk"))
    full = glob.glob(os.path.join(fw_dir, "*-full.bin"))
    if not full:
        fail("no firmware found in " + fw_dir)
    version = os.path.basename(full[0]).replace("TheDroneCommunity-firmware-", "").replace("-full.bin", "")
    return version, fw_dir, (apks[0] if apks else None)


# ---------------------------------------------------------------- drone

def find_drone_port():
    from serial.tools import list_ports
    for p in list_ports.comports():
        if p.vid == ESPRESSIF_VID:
            return p.device
    return None


def wait_for_drone(port):
    if port:
        return port
    p = find_drone_port()
    if p:
        return p
    say("Plug the drone into this computer with a USB-C data cable (battery unplugged).")
    say("Waiting... (if nothing happens, hold the BOOT button while plugging it in)")
    while True:
        time.sleep(1)
        p = find_drone_port()
        if p:
            time.sleep(1)
            return p


def flash(fw_dir, port):
    full = glob.glob(os.path.join(fw_dir, "*-full.bin"))[0]
    step("Flashing " + os.path.basename(full) + " to " + port)
    cmd = [sys.executable, "-m", "esptool", "--chip", "esp32s3", "-p", port, "-b", "460800",
           "--before", "default_reset", "--after", "hard_reset", "write_flash", "0x0", full]
    if subprocess.call(cmd) != 0:
        say("Retrying at a slower speed...")
        cmd[cmd.index("460800")] = "115200"
        if subprocess.call(cmd) != 0:
            fail("flashing failed. Try another USB cable or hold BOOT while plugging in.")


def read_boot(port):
    """Watch the boot log for the Wi-Fi name and the IMU check."""
    import serial
    step("Checking the drone boots")
    ssid, ok, lines = None, None, []
    deadline = time.time() + 15
    while time.time() < deadline:
        try:
            s = serial.Serial(port, 115200, timeout=0.5)
            break
        except Exception:
            time.sleep(0.5)
    else:
        say("Couldn't open the port to read the boot log. That's usually fine.")
        return None
    with s:
        while time.time() < deadline:
            line = s.readline().decode("utf-8", "replace").strip()
            if not line:
                continue
            lines.append(line)
            if "SSID:" in line:
                ssid = line.split("SSID:")[1].split()[0]
            low = line.lower()
            if "ready to fly" in low or "self test [ok]" in low or "selftest [ok]" in low:
                ok = True
            if ssid and ok:
                break
    if ssid:
        say("Drone Wi-Fi: " + ssid + "   (password 12345678)")
    else:
        say("Didn't see the Wi-Fi name in the boot log. Check for an ESP-DRONE_... network.")
    if not ok:
        bad = [l for l in lines if "fail" in l.lower() or "error" in l.lower()]
        if bad:
            say("Boot log warnings:")
            for l in bad[-5:]:
                say("  " + l)
    return ssid


# ---------------------------------------------------------------- phone

def find_adb():
    exe = "adb.exe" if os.name == "nt" else "adb"
    candidates = [shutil.which("adb"),
                  os.path.join(os.path.expanduser("~"), "Library/Android/sdk/platform-tools", exe),
                  os.path.join(os.path.expanduser("~"), "Android/Sdk/platform-tools", exe),
                  os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android/Sdk/platform-tools", exe),
                  os.path.join(HOME, "platform-tools", exe)]
    for c in candidates:
        if c and os.path.exists(c):
            return c
    osname = {"Darwin": "darwin", "Linux": "linux", "Windows": "windows"}[platform.system()]
    url = "https://dl.google.com/android/repository/platform-tools-latest-%s.zip" % osname
    say("Downloading Android platform-tools (adb) from Google...")
    os.makedirs(HOME, exist_ok=True)
    data = urllib.request.urlopen(url, timeout=120).read()
    zipfile.ZipFile(io.BytesIO(data)).extractall(HOME)
    adb = os.path.join(HOME, "platform-tools", exe)
    if os.name != "nt":
        os.chmod(adb, 0o755)
    return adb


def adb_devices(adb):
    out = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    return [l.split() for l in out if l.strip()]


def install_app(apk):
    step("Installing the Android app")
    adb = find_adb()
    say("Plug your Android phone into this computer with USB debugging on")
    say("(Settings > About phone > tap Build number 7 times, then Developer options > USB debugging).")
    say("Waiting for the phone... press Ctrl+C to skip.")
    try:
        warned = False
        while True:
            devs = adb_devices(adb)
            ready = [d for d in devs if len(d) > 1 and d[1] == "device"]
            if ready:
                break
            if any(len(d) > 1 and d[1] == "unauthorized" for d in devs) and not warned:
                say("Tap 'Allow' on the USB debugging prompt on the phone.")
                warned = True
            time.sleep(1)
    except KeyboardInterrupt:
        say("\nSkipped. Copy " + apk + " to the phone and open it to install.")
        return
    serial_no = ready[0][0]
    if len(ready) > 1:
        say("More than one Android device is connected:")
        for i, d in enumerate(ready, 1):
            say("  %d. %s" % (i, d[0]))
        pick = ask("Install on which one? [1-%d] " % len(ready))
        if not pick.isdigit() or not 1 <= int(pick) <= len(ready):
            fail("no device picked.")
        serial_no = ready[int(pick) - 1][0]
    r = subprocess.run([adb, "-s", serial_no, "install", "-r", apk], capture_output=True, text=True)
    out = r.stdout + r.stderr
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out or "signatures do not match" in out:
        say("An older build of the app with a different signature is on the phone.")
        say("It has to be removed first, which clears the app's saved settings.")
        if ask("Remove it and install the new one? [y/N] ").lower() not in ("y", "yes"):
            fail("app not installed. Uninstall the old app on the phone, then run this again.")
        subprocess.run([adb, "-s", serial_no, "uninstall", APP_ID], capture_output=True)
        r = subprocess.run([adb, "-s", serial_no, "install", apk], capture_output=True, text=True)
        out = r.stdout + r.stderr
    if "Success" not in out:
        say(out.strip())
        fail("app install failed. You can copy the APK to the phone instead: " + apk)
    subprocess.run([adb, "-s", serial_no, "shell", "monkey", "-p", APP_ID, "-c",
                    "android.intent.category.LAUNCHER", "1"], capture_output=True)
    say("App installed and opened on the phone.")


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ios", action="store_true", help="flash the iOS build (ESP-Drone iPhone app)")
    ap.add_argument("--firmware-only", action="store_true")
    ap.add_argument("--app-only", action="store_true")
    ap.add_argument("--port")
    ap.add_argument("--dir", help="unzipped release folder to use instead of downloading")
    a = ap.parse_args()

    say("The Drone Community installer")
    if a.dir:
        version, fw_dir, apk = local_release(a.dir, a.ios)
        # The folder decides the build; --ios inside the Android folder would flash
        # Android firmware and then print the iPhone instructions.
        a.ios = version.endswith("-ios")
        say("Using files in " + a.dir + " (version " + version + ")")
    else:
        version, fw_dir, apk = fetch_release(a.ios)
    check_sums(fw_dir)

    ssid = None
    if not a.app_only:
        port = wait_for_drone(a.port)
        flash(fw_dir, port)
        ssid = read_boot(port)
        say("Unplug the drone from USB now.")

    if not a.firmware_only and not a.ios:
        if apk:
            install_app(apk)
        else:
            say("No APK found, skipping the app.")
    elif a.ios and not a.firmware_only:
        say("\nOn the iPhone, install ESP-Drone from the App Store:")
        say("https://apps.apple.com/us/app/id1522247884")

    step("Done")
    say("1. Remove the props for the first power-up, then connect the battery.")
    say("2. On the phone, join " + (ssid or "the ESP-DRONE_... network") + ", password 12345678.")
    if a.ios:
        say("3. Open ESP-Drone. The drone arms itself when the app connects.")
    else:
        say("3. Open the app, tap Connect, run the motor test and level calibration, then Arm.")
    say("Never plug in USB and the battery at the same time.")


if __name__ == "__main__":
    main()
