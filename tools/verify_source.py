#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

# v0.6.1: normalize stale source left behind by incremental GitHub uploads.
# v0.6.0 did not ship CalibrationWizard.java, so GitHub upload does not delete an
# older copy.  That legacy file referenced APIs/resources removed long ago and
# caused dozens of javac errors.  Replace it deterministically before checks.
compat_wizard = ROOT / "tools/compat/CalibrationWizard.java"
app_wizard = ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibrationWizard.java"
if compat_wizard.exists():
    compat_text = compat_wizard.read_text()
    if (not app_wizard.exists()) or app_wizard.read_text(errors="ignore") != compat_text:
        app_wizard.write_text(compat_text)
        print("Legacy CalibrationWizard normalized: PASS")

# Resource-reference guard.  This catches the exact class of R.string.* failures
# that previously reached Gradle only after a full Android compile.
import re
base_strings_xml = ROOT / "app/src/main/res/values/strings.xml"
pl_strings_xml = ROOT / "app/src/main/res/values-pl/strings.xml"
def _string_names(path):
    tree = ET.parse(path)
    return {e.attrib["name"] for e in tree.getroot() if e.tag == "string" and "name" in e.attrib}
base_string_names = _string_names(base_strings_xml)
pl_string_names = _string_names(pl_strings_xml)
if base_string_names != pl_string_names:
    missing_pl = sorted(base_string_names - pl_string_names)
    missing_en = sorted(pl_string_names - base_string_names)
    raise SystemExit(f"PL/EN string resource mismatch. Missing PL={missing_pl}; missing EN={missing_en}")
java_files = list((ROOT / "app/src/main/java").rglob("*.java"))
missing_string_refs = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    # Exclude android.R.string.* references.
    for m in re.finditer(r"(?<!android\.)R\.string\.([A-Za-z0-9_]+)", text):
        name = m.group(1)
        if name not in base_string_names:
            line = text[:m.start()].count("\n") + 1
            missing_string_refs.append(f"{jf.relative_to(ROOT)}:{line}: R.string.{name}")
if missing_string_refs:
    raise SystemExit("Missing string resources:\n" + "\n".join(missing_string_refs))
print("Java R.string references + PL/EN parity: PASS")


# Drawable/mipmap resource-reference guard.
def _resource_file_names(folder):
    out = set()
    base = ROOT / "app/src/main/res" / folder
    if base.exists():
        for f in base.iterdir():
            if f.is_file():
                out.add(f.stem)
    return out
resource_sets = {
    "drawable": _resource_file_names("drawable"),
    "mipmap": _resource_file_names("mipmap-anydpi-v26"),
}
missing_visual_refs = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    for kind, available in resource_sets.items():
        for m in re.finditer(rf"(?<!android\\.)R\\.{kind}\\.([A-Za-z0-9_]+)", text):
            name = m.group(1)
            if name not in available:
                line = text[:m.start()].count("\\n") + 1
                missing_visual_refs.append(f"{jf.relative_to(ROOT)}:{line}: R.{kind}.{name}")
if missing_visual_refs:
    raise SystemExit("Missing visual resources:\\n" + "\\n".join(missing_visual_refs))
print("Java drawable/mipmap references: PASS")

# Internal InputRouter API guard.  It catches stale sources that call methods no
# longer present in the router (e.g. setCalibrationMode/calibrationMode).
router_path = ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java"
router_text = router_path.read_text(errors="ignore")
router_methods = set(re.findall(r"(?:public|private|protected)\s+static(?:\s+synchronized)?\s+[A-Za-z0-9_<>\[\].?]+\s+([A-Za-z0-9_]+)\s*\(", router_text))
router_methods.update(re.findall(r"(?:public|private|protected)\s+static\s+synchronized\s+[A-Za-z0-9_<>\[\].?]+\s+([A-Za-z0-9_]+)\s*\(", router_text))
unknown_router_calls = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    for m in re.finditer(r"InputRouter\.([A-Za-z0-9_]+)\s*\(", text):
        name = m.group(1)
        if name not in router_methods:
            line = text[:m.start()].count("\n") + 1
            unknown_router_calls.append(f"{jf.relative_to(ROOT)}:{line}: InputRouter.{name}()")
if unknown_router_calls:
    raise SystemExit("Unknown InputRouter API calls:\n" + "\n".join(unknown_router_calls))
print("InputRouter API references: PASS")
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
        str(ROOT / "app/src/main/java/pl/openai/pokeballmouse/JoystickCalibration.java"),
        str(ROOT / "tools/DecoderSelfTest.java"),
        str(ROOT / "tools/MotionGestureSelfTest.java"),
        str(ROOT / "tools/MotionTelemetrySelfTest.java"),
        str(ROOT / "tools/JoystickCalibrationSelfTest.java"),
    ], check=True)
    subprocess.run(["java", "-cp", td, "DecoderSelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "MotionGestureSelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "MotionTelemetrySelfTest"], check=True)
    subprocess.run(["java", "-cp", td, "JoystickCalibrationSelfTest"], check=True)

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



# v0.5.0 connection haptic / adaptive icon / Poké Ball output UI guards.
manifest_text = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
if "android.permission.VIBRATE" not in manifest_text:
    raise SystemExit("Missing VIBRATE permission for connection acknowledgement")
if 'android:icon="@mipmap/ic_launcher"' not in manifest_text:
    raise SystemExit("Application must use adaptive mipmap launcher icon")
service_text = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
if "PhoneFeedback.detectedVibration" not in service_text:
    raise SystemExit("Missing stronger phone vibration when Poké Ball Plus is detected")
feedback_text = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PhoneFeedback.java").read_text()
if "75L" not in feedback_text or "135" not in feedback_text:
    raise SystemExit("Connection vibration must use the stronger v0.5.0 pulse")
main_text = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
if "diagnostics_pokeball_feedback" in main_text or "diagnostics_test_ball_vibration" in main_text or "diagnostics_test_ball_sound" in main_text:
    raise SystemExit("Diagnostics must not contain Poké Ball output test controls")
if "PhoneFeedback.testVibration" in main_text or "PhoneFeedback.testSound" in main_text:
    raise SystemExit("Phone vibration/sound diagnostics must not remain")
if "BluetoothAdapter.ACTION_REQUEST_ENABLE" not in main_text or "Settings.ACTION_BLUETOOTH_SETTINGS" not in main_text:
    raise SystemExit("Bluetooth status action must request enable/open Bluetooth settings")
for rel in [
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
]:
    if not (ROOT / rel).exists():
        raise SystemExit(f"Missing adaptive icon resource: {rel}")
print("Adaptive icon + Bluetooth control + connection haptic: PASS")

# v0.5.0 mouse touch-through/performance guards.
cursor = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
router = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
if "Choreographer" not in cursor:
    raise SystemExit("Cursor must be synchronized to display frames with Choreographer")
if "bridge.move(" in cursor:
    raise SystemExit("Cursor must not continuously inject SOURCE_MOUSE hover events")
if "InputRouter.onMouseCursorMoved" not in cursor:
    raise SystemExit("Cursor movement must only enter Shizuku during a real drag")
if "mousePressPending" not in router or "mouseDragging" not in router:
    raise SystemExit("Mouse click/drag state machine missing")
if "service.clickAtCursor()" not in router:
    raise SystemExit("Normal mouse click must use Accessibility tap to preserve finger input")
print("Mouse finger-touch coexistence + cursor performance: PASS")

# v0.5.0 gesture guards: one deliberate gesture per Top hold, no idle live flicker.
control = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
motion = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MotionGestureDetector.java").read_text()
if "Math.max(0.32f" not in control:
    raise SystemExit("Motion sensitivity must enforce a practical noise floor")
if "consumed" not in motion or "ARM_DELAY_MS = 300L" not in motion:
    raise SystemExit("Motion detector must consume one gesture per Top hold and require a 300 ms Top arm delay")
if "top && liveMotionDirection == null" not in router:
    raise SystemExit("Live motion direction must only detect one direction while Top is held")
print("One-shot Top gesture + rebound suppression: PASS")


# Java multi-catch regression guard: catch alternatives cannot be related
# by inheritance (SecurityException is a RuntimeException).
java_text = "\n".join(
    p.read_text(errors="ignore")
    for p in (ROOT / "app/src/main/java").rglob("*.java")
)
illegal_multicatches = [
    "RuntimeException | SecurityException",
    "SecurityException | RuntimeException",
]
for pattern in illegal_multicatches:
    if pattern in java_text:
        raise SystemExit(f"Illegal related Java multi-catch detected: {pattern}")
print("Java multi-catch inheritance guard: PASS")

# v0.5.1 battery/Shizuku/appearance UI guards.
main_v051 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
battery_v051 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/BatteryLevelView.java").read_text()
if "BatteryLevelView" not in main_v051 or "batteryIcon.setLevel(battery)" not in main_v051:
    raise SystemExit("Battery UI must use a percentage-filled BatteryLevelView")
for token in ["level / 100f", "successColor", "warningColor", "dangerColor"]:
    if token not in battery_v051:
        raise SystemExit(f"Battery fill implementation missing token: {token}")
if "action_open_shizuku" in main_v051 or "openShizuku()" in main_v051:
    raise SystemExit("Standalone Open Shizuku button/action must be removed")
if "addAppearanceCard(root)" in main_v051:
    raise SystemExit("Appearance must not remain as a main-screen card")
for token in ["showAppearanceDialog()", "ImageButton appearance", "R.drawable.ic_palette"]:
    if token not in main_v051:
        raise SystemExit(f"Header appearance dialog control missing: {token}")
print("Battery fill + compact appearance dialog + Shizuku cleanup: PASS")

# v0.5.2 UI + gesture regression guards.
main_v052 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
control_v052 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
router_v052 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
orb_v052 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ConnectionOrbView.java").read_text()
icon_v052 = (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").read_text()
if main_v052.index("addDiagnosticsCard(root)") > main_v052.index("addModeCard(root)"):
    raise SystemExit("Diagnostics must appear above Control mode")
for forbidden in ["diagnosticsSystem", "diagnostics_pokeball_feedback", "diagnostics_test_ball_vibration", "diagnostics_test_ball_sound", "diagnostics_ball_output_unavailable"]:
    if forbidden in main_v052:
        raise SystemExit(f"Removed Diagnostics UI token still present: {forbidden}")
if "battery.setVisibility(View.GONE)" not in main_v052 or "phase == PokeballService.Phase.CONNECTED ? View.VISIBLE : View.GONE" not in main_v052:
    raise SystemExit("Battery row must be hidden whenever Poké Ball Plus is not connected")
if "setOnApplyWindowInsetsListener" not in main_v052 or "baseTopPadding + topInset" not in main_v052:
    raise SystemExit("Main content must respect the status-bar inset")
if "loopAngle" not in orb_v052 or "-rotation * 0.7f" in orb_v052:
    raise SystemExit("Connection search animation must loop seamlessly without a snapping counter-rotation")
if 'android:scaleX="0.88"' not in icon_v052 or 'android:scaleY="0.88"' not in icon_v052:
    raise SystemExit("Launcher foreground must be slightly reduced inside the adaptive icon")
if "Action fallback = Action.NONE" not in control_v052:
    raise SystemExit("Motion gesture actions must default to None")
if "motion_defaults_none_v052" not in control_v052:
    raise SystemExit("Legacy motion defaults must be migrated without overwriting custom mappings")
if "now - topHoldStartMs < MotionGestureDetector.ARM_DELAY_MS" not in router_v052:
    raise SystemExit("Top gesture live preview must respect the 300 ms arming delay")
if "Math.max(absX, absZ)" not in motion:
    raise SystemExit("Motion detector must support X/Z horizontal fallback for left/right gestures")
if "armedGestureHold" not in router_v052:
    raise SystemExit("Long Top gesture holds must not fall through to a normal Top click")
print("v0.5.2 UI ordering + battery visibility + seamless animation + gesture arming: PASS")


# v0.6.0 joystick recentering + Top multi-click + typing/scroll guards.
router_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
control_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
cursor_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
typing_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/TypingOverlayView.java").read_text()
main_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
accessibility_v060 = (ROOT / "app/src/main/res/xml/accessibility_config.xml").read_text()
for token in ["fakeCenterEnabled", "JoystickCalibration.applyAxis", "rawJoyX", "rawJoyY"]:
    if token not in router_v060 + control_v060:
        raise SystemExit(f"Missing fake-center token: {token}")
for token in ["TOP_MULTI_CLICK_WINDOW_MS", "topClickCount >= 3", "toggleScrollMode", "count == 2", "toggleTypingMode"]:
    if token not in router_v060:
        raise SystemExit(f"Missing Top multi-click token: {token}")
for token in ["TypingMode", "RADIAL", "KEYBOARD"]:
    if token not in control_v060:
        raise SystemExit(f"Missing typing mode preference token: {token}")
for token in ["TypingOverlayView", "setTypingVisible", "updateTypingJoystick", "selectTypingKey", "scrollByJoystick"]:
    if token not in cursor_v060 + typing_v060:
        raise SystemExit(f"Missing typing/scroll implementation token: {token}")
if 'android:canRetrieveWindowContent="true"' not in accessibility_v060:
    raise SystemExit("Typing requires Accessibility window content access")
for token in ["joystick_set_zero", "joystick_fake_center_active", "addTypingCard(root)"]:
    if token not in main_v060:
        raise SystemExit(f"Missing v0.6.0 UI token: {token}")
print("v0.6.0 fake center + radial/QWERTY typing + 2x/3x Top shortcuts: PASS")

print("Source verification: PASS")
