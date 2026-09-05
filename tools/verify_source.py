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
        str(ROOT / "app/src/main/java/pl/openai/pokeballmouse/MotionTelemetryDetector.java"),
        str(ROOT / "tools/DecoderSelfTest.java"),
        str(ROOT / "tools/MotionGestureSelfTest.java"),
        str(ROOT / "tools/MotionTelemetrySelfTest.java"),
    ], check=True)
    subprocess.run(["java", "-cp", td, "DecoderSelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "MotionGestureSelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "MotionTelemetrySelfTest"], check=True)

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
    "Six-direction live motion": "MotionTelemetryDetector",
    "Tap screen picker": "TouchPickerOverlayView",
    "Dark theme": "AppTheme.Dark",
    "Theme preference": "ThemePrefs",
    "PL/EN language": "LanguagePrefs",
    "Location status": "isLocationEnabled",
    "Connection animation": "ConnectionOrbView",
    "Circular joystick diagnostics": "JoystickDiagnosticView",
    "Gyro/Pitch/Yaw/Roll": "quaternionToEuler",
    "Adaptive launcher icon": "ic_launcher_foreground",
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


# Cursor overlay regression guard: Accessibility being enabled must not show a cursor
# before a Poké Ball Plus connection exists.
cursor = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
if "cursorView.setVisibility(View.GONE)" not in cursor:
    raise SystemExit("Cursor overlay must start hidden")
if "PokeballService.isConnected()" not in cursor:
    raise SystemExit("Cursor visibility/movement must be gated by Poké Ball connection state")
if "connected && mouseMode" not in cursor:
    raise SystemExit("Mouse processing must require an active Poké Ball connection")
print("Cursor hidden until Poké Ball connection: PASS")

# Android-14-style cursor + touch-through regression guards.
cursor_view = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorOverlayView.java").read_text()
if "pointerPath" not in cursor_view or "hotspotXpx" not in cursor_view:
    raise SystemExit("Cursor must use the Android-style arrow path with a tip hotspot")
if "setClickable(false)" not in cursor_view or "IMPORTANT_FOR_ACCESSIBILITY_NO" not in cursor_view:
    raise SystemExit("Cursor view must remain non-interactive")
if "FLAG_NOT_TOUCHABLE" not in cursor or "FLAG_NOT_TOUCH_MODAL" not in cursor:
    raise SystemExit("Cursor overlay must pass finger input through to apps underneath")
if "cursorView.hotspotXpx()" not in cursor or "cursorView.hotspotYpx()" not in cursor:
    raise SystemExit("Overlay position must align the click coordinate with the visible arrow tip")
print("Android-style click-through cursor: PASS")


# Diagnostics/motion UI regression guards for v0.4.7.
main_activity = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
joystick_view = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/JoystickDiagnosticView.java").read_text()
if "postInvalidateOnAnimation()" not in joystick_view or "InputRouter.joyX()" not in joystick_view:
    raise SystemExit("Joystick diagnostics must refresh directly from InputRouter at display-frame rate")
if "diagnostics_gyro" in main_activity or "diagnostics_accel" in main_activity or "diagnostics_pitch" in main_activity:
    raise SystemExit("Motion sensor telemetry must not remain in Diagnostics")
for token in ["motionDetected", "motionSensors", "motion_detected_direction", "liveMotionDirection"]:
    if token not in main_activity:
        raise SystemExit(f"Missing Motion gestures live telemetry token: {token}")
print("Smooth joystick + motion telemetry relocation: PASS")



# v0.4.8 feedback/adaptive-icon regression guards.
manifest_text = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
if "android.permission.VIBRATE" not in manifest_text:
    raise SystemExit("Missing VIBRATE permission for phone haptics")
if 'android:icon="@mipmap/ic_launcher"' not in manifest_text:
    raise SystemExit("Application must use adaptive mipmap launcher icon")
service_text = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
if "PhoneFeedback.lightDetectedVibration" not in service_text:
    raise SystemExit("Missing light phone vibration when Poké Ball Plus is detected")
main_text = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
for token in ["PhoneFeedback.testVibration", "PhoneFeedback.testSound"]:
    if token not in main_text:
        raise SystemExit(f"Missing diagnostics phone-feedback token: {token}")
strings_text = (ROOT / "app/src/main/res/values/strings.xml").read_text()
if "diagnostics_phone_feedback" not in strings_text:
    raise SystemExit("Missing diagnostics phone-feedback strings")
for rel in [
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
]:
    if not (ROOT / rel).exists():
        raise SystemExit(f"Missing adaptive icon resource: {rel}")
print("Adaptive icon + phone feedback diagnostics: PASS")

print("Source verification: PASS")
