#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
xmls = list((ROOT / "app/src/main/res").rglob("*.xml")) + [ROOT / "app/src/main/AndroidManifest.xml"]
for p in xmls:
    ET.parse(p)
print(f"XML: PASS ({len(xmls)} files)")

with tempfile.TemporaryDirectory() as td:
    subprocess.run([
        "javac", "-d", td,
        str(ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballDecoder.java"),
        str(ROOT / "app/src/main/java/pl/openai/pokeballmouse/MotionGestureDetector.java"),
        str(ROOT / "tools/DecoderSelfTest.java"),
        str(ROOT / "tools/MotionGestureSelfTest.java"),
    ], check=True)
    subprocess.run(["java", "-cp", td, "DecoderSelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "MotionGestureSelfTest"], check=True)

required = {
    "BLE service UUID": "6675e16c-f36d-4567-bb55-6b51e27a23e5",
    "BLE input UUID": "6675e16c-f36d-4567-bb55-6b51e27a23e6",
    "Battery Level UUID": "00002a19-0000-1000-8000-00805f9b34fb",
    "Accessibility overlay": "TYPE_ACCESSIBILITY_OVERLAY",
    "Mouse source": "SOURCE_MOUSE",
    "Touch source": "SOURCE_TOUCHSCREEN",
    "D-pad mode": "KEYCODE_DPAD_UP",
    "Shizuku UserService": "bindUserService",
    "Motion gestures": "MotionGestureDetector",
    "Tap screen picker": "TouchPickerOverlayView",
    "Dark theme": "AppTheme.Dark",
    "Theme preference": "ThemePrefs",
    "PL/EN language": "LanguagePrefs",
    "Location status": "isLocationEnabled",
    "Connection animation": "ConnectionOrbView",
    "Circular joystick diagnostics": "JoystickDiagnosticView",
    "Gyro/Pitch/Yaw/Roll": "quaternionToEuler",
    "Launcher icon": "ic_launcher_pokeball",
}
all_text = "\n".join(p.read_text(errors="ignore") for p in (ROOT / "app/src/main").rglob("*") if p.is_file())
for name, token in required.items():
    if token not in all_text:
        raise SystemExit(f"Missing required token: {name} ({token})")
    print(f"{name}: PASS")

for forbidden in ["Firmware", "Signal strength", "Siła sygnału"]:
    if forbidden in all_text:
        raise SystemExit(f"Forbidden UI text still present: {forbidden}")
print("Firmware/signal-strength UI removed: PASS")


# Compile-risk guards for Android/Shizuku glue that pure-Java self tests do not cover.
service = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
if "BluetoothDevice.TRANSPORT_LE" in service and "import android.bluetooth.BluetoothDevice;" not in service:
    raise SystemExit("PokeballService uses BluetoothDevice.TRANSPORT_LE without importing BluetoothDevice")
print("BluetoothDevice TRANSPORT_LE import: PASS")

aidl = (ROOT / "app/src/main/aidl/pl/openai/pokeballmouse/IPrivilegedInput.aidl").read_text()
methods = [line.strip() for line in aidl.splitlines() if line.strip().endswith(";") and "(" in line]
if any("=" in m for m in methods) and not all("=" in m for m in methods):
    raise SystemExit("AIDL transaction IDs must be specified for all methods or none")
if "void destroy() = 16777114;" not in aidl:
    raise SystemExit("Missing reserved Shizuku destroy transaction id")
print("AIDL transaction IDs: PASS")

print("Source verification: PASS")
