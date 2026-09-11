#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


# Cross-file compile-safety guards for merged sources.
import re
base_strings_xml = ROOT / "app/src/main/res/values/strings.xml"
pl_strings_xml = ROOT / "app/src/main/res/values-pl/strings.xml"
def _string_names(path):
    tree = ET.parse(path)
    return {e.attrib["name"] for e in tree.getroot() if e.tag == "string" and "name" in e.attrib}
base_string_names = _string_names(base_strings_xml)
pl_string_names = _string_names(pl_strings_xml)
if base_string_names != pl_string_names:
    raise SystemExit(f"PL/EN string resource mismatch. Missing PL={sorted(base_string_names-pl_string_names)}; missing EN={sorted(pl_string_names-base_string_names)}")
java_files = list((ROOT / "app/src/main/java").rglob("*.java"))

# Lightweight Java lexical guard. This intentionally runs before Android compilation
# and catches merge/edit mistakes such as a raw newline inside a quoted string.
def _check_java_lexical_balance(path):
    src = path.read_text(errors="ignore")
    NORMAL, STRING, CHAR, LINE, BLOCK, TEXT_BLOCK = range(6)
    state = NORMAL
    escape = False
    line = 1
    start_line = 1
    i = 0
    while i < len(src):
        ch = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ""
        tri = src[i:i+3]
        if state == NORMAL:
            if tri == '\"\"\"':
                state = TEXT_BLOCK; start_line = line; i += 3; continue
            if ch == '"':
                state = STRING; start_line = line; escape = False
            elif ch == "'":
                state = CHAR; start_line = line; escape = False
            elif ch == '/' and nxt == '/':
                state = LINE; i += 2; continue
            elif ch == '/' and nxt == '*':
                state = BLOCK; start_line = line; i += 2; continue
        elif state == STRING:
            if ch == '\n':
                raise SystemExit(f"Unclosed Java string literal: {path.relative_to(ROOT)}:{start_line}")
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == '"':
                state = NORMAL
        elif state == CHAR:
            if ch == '\n':
                raise SystemExit(f"Unclosed Java char literal: {path.relative_to(ROOT)}:{start_line}")
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == "'":
                state = NORMAL
        elif state == LINE:
            if ch == '\n':
                state = NORMAL
        elif state == BLOCK:
            if ch == '*' and nxt == '/':
                state = NORMAL; i += 2; continue
        elif state == TEXT_BLOCK:
            if tri == '\"\"\"':
                state = NORMAL; i += 3; continue
        if ch == '\n':
            line += 1
        i += 1
    if state == STRING:
        raise SystemExit(f"Unclosed Java string literal at EOF: {path.relative_to(ROOT)}:{start_line}")
    if state == CHAR:
        raise SystemExit(f"Unclosed Java char literal at EOF: {path.relative_to(ROOT)}:{start_line}")
    if state == BLOCK:
        raise SystemExit(f"Unclosed Java block comment: {path.relative_to(ROOT)}:{start_line}")
    if state == TEXT_BLOCK:
        raise SystemExit(f"Unclosed Java text block: {path.relative_to(ROOT)}:{start_line}")

for _java in java_files:
    _check_java_lexical_balance(_java)
print("Java string/comment lexical balance: PASS")
missing_string_refs = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    for m in re.finditer(r"(?<!android\.)R\.string\.([A-Za-z0-9_]+)", text):
        if m.group(1) not in base_string_names:
            line = text[:m.start()].count("\n") + 1
            missing_string_refs.append(f"{jf.relative_to(ROOT)}:{line}: R.string.{m.group(1)}")
if missing_string_refs:
    raise SystemExit("Missing string resources:\n" + "\n".join(missing_string_refs))
print("Java R.string references + PL/EN parity: PASS")

router_path = ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java"
router_text_guard = router_path.read_text(errors="ignore")
router_methods = set(re.findall(r"(?:public|private|protected)\s+static(?:\s+synchronized)?\s+[A-Za-z0-9_<>\[\].?]+\s+([A-Za-z0-9_]+)\s*\(", router_text_guard))
unknown_router_calls = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    for m in re.finditer(r"InputRouter\.([A-Za-z0-9_]+)\s*\(", text):
        if m.group(1) not in router_methods:
            line = text[:m.start()].count("\n") + 1
            unknown_router_calls.append(f"{jf.relative_to(ROOT)}:{line}: InputRouter.{m.group(1)}()")
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
if not (("cursorView.hotspotXpx()" in cursor and "cursorView.hotspotYpx()" in cursor)
        or ("pointerX - hotspotXpx()" in cursor_view and "pointerY - hotspotYpx()" in cursor_view)):
    raise SystemExit("Overlay drawing must align the click coordinate with the visible arrow tip")
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
if "consumed" not in motion or "ARM_DELAY_MS = 100L" not in motion:
    raise SystemExit("Motion detector must consume one gesture per Top hold and use the short 100 ms arm delay")
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
diag_call = "addDiagnosticsCard(connectedSettingsContainer)" if "addDiagnosticsCard(connectedSettingsContainer)" in main_v052 else "addDiagnosticsCard(root)"
mode_call = "addModeCard(connectedSettingsContainer)" if "addModeCard(connectedSettingsContainer)" in main_v052 else "addModeCard(root)"
if main_v052.index(diag_call) > main_v052.index(mode_call):
    raise SystemExit("Diagnostics must appear above Control mode")
for forbidden in ["diagnosticsSystem", "diagnostics_pokeball_feedback", "diagnostics_test_ball_vibration", "diagnostics_test_ball_sound", "diagnostics_ball_output_unavailable"]:
    if forbidden in main_v052:
        raise SystemExit(f"Removed Diagnostics UI token still present: {forbidden}")
if "battery.setVisibility(View.GONE)" not in main_v052 or ("connected ? View.VISIBLE : View.GONE" not in main_v052 and "phase == PokeballService.Phase.CONNECTED ? View.VISIBLE : View.GONE" not in main_v052):
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
    raise SystemExit("Top gesture live preview must respect the configured short arming delay")
for token in ["hypot(dx, dz)", "lateralProjection", "lateralAxisLearned", "LATERAL_RELEARN_RATIO"]:
    if token not in motion:
        raise SystemExit(f"Motion detector X/Z lateral-plane support missing: {token}")
if "intentionalLongGestureHold" not in router_v052:
    raise SystemExit("Long Top gesture holds must not fall through to a normal Top click")
print("v0.5.2 UI ordering + battery visibility + seamless animation + gesture arming: PASS")


# v0.5.3 joystick center calibration guards.
calibration_v053 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/JoystickCalibration.java").read_text()
router_v053 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
control_v053 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
main_v053 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
for token in ["applyAxis", "max - center", "center - min"]:
    if token not in calibration_v053:
        raise SystemExit(f"Joystick center rescaling missing token: {token}")
for token in ["rawJoyX", "rawJoyY", "applyJoystickCalibration", "setJoystickCenterFromCurrent", "clearJoystickCenter"]:
    if token not in router_v053:
        raise SystemExit(f"InputRouter joystick calibration missing token: {token}")
for token in ["joystickCenterCalibrated", "joystickCenterX", "joystickCenterY", "setJoystickCenter", "clearJoystickCenter"]:
    if token not in control_v053:
        raise SystemExit(f"Persistent joystick center preference missing token: {token}")
for token in ["joystick_set_zero", "joystick_center_adjusted", "calibrateJoystickCenter"]:
    if token not in main_v053:
        raise SystemExit(f"Joystick calibration UI missing token: {token}")
print("Joystick fake-center calibration + symmetric range scaling: PASS")


# v0.5.4 gesture/cursor/appearance guards.
motion_v054 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MotionGestureDetector.java").read_text()
router_v054 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
cursor_view_v054 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorOverlayView.java").read_text()
cursor_service_v054 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
main_v054 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
strings_en_v054 = (ROOT / "app/src/main/res/values/strings.xml").read_text()
strings_pl_v054 = (ROOT / "app/src/main/res/values-pl/strings.xml").read_text()
if "ARM_DELAY_MS = 100L" not in motion_v054:
    raise SystemExit("v0.5.4 must use the shorter 100 ms Top arming delay")
for token in ["hypot(dx, dz)", "lateralProjection", "refineLateralAxis", "lateralAxisLearned"]:
    if token not in motion_v054:
        raise SystemExit(f"v0.5.4 lateral gesture detection missing: {token}")
if "liveMotionDirection = toLiveDirection(gesture)" not in router_v054:
    raise SystemExit("Live gesture label must agree with the actionable four-way direction")
for token in ["outlinePaint", "Color.argb(215, 255, 255, 255)", "cubicTo", "setPointerPosition"]:
    if token not in cursor_view_v054:
        raise SystemExit(f"Rounded outlined Android-style cursor missing: {token}")
if "WindowManager.LayoutParams.MATCH_PARENT" not in cursor_service_v054 or "updateViewLayout(cursorView" in cursor_service_v054:
    raise SystemExit("Cursor overlay should draw in one full-screen pass-through window without per-frame WindowManager moves")
for token in ["appearanceChoiceGroup", "appearance_brand", "appearance_save", "appearance_cancel"]:
    if token not in main_v054 and token not in strings_en_v054 and token not in strings_pl_v054:
        raise SystemExit(f"User-friendly appearance dialog missing: {token}")
if "od SimmSoft" not in strings_pl_v054 or "by SimmSoft" not in strings_en_v054:
    raise SystemExit("SimmSoft attribution missing from appearance dialog")
print("Short gesture arming + X/Z lateral detection + rounded cursor + SimmSoft dialog: PASS")


# v0.6.0 device profiles / direct Accessibility / connected notification guards.
profile_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/DeviceProfileStore.java").read_text()
wizard_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibrationWizard.java").read_text()
cal_motion_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibratedMotionGestureDetector.java").read_text()
service_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
bridge_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ShizukuBridge.java").read_text()
priv_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PrivilegedInputService.java").read_text()
main_v060 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
aidl_v060 = (ROOT / "app/src/main/aidl/pl/openai/pokeballmouse/IPrivilegedInput.aidl").read_text()
for token in ["ensureProfile", "activeProfile", "saveJoystickCalibration", "saveMotionTemplates", "PB-%02d"]:
    if token not in profile_v060: raise SystemExit(f"Device profile support missing: {token}")
for token in ["6000L", "calibrationMode", "motionSamples", "saveCalibration"]:
    if token not in wizard_v060: raise SystemExit(f"Calibration wizard missing: {token}")
if "bestDot" not in cal_motion_v060 or "MotionTemplates" not in cal_motion_v060:
    raise SystemExit("Per-device calibrated motion classifier missing")
if "InputRouter.setActiveDevice" not in service_v060 or "getAddress()" not in service_v060:
    raise SystemExit("BLE device identity must select a per-device profile")
for token in ["setAccessibilityService", "Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES", "ACCESSIBILITY_ENABLED"]:
    if token not in aidl_v060 + priv_v060 + bridge_v060: raise SystemExit(f"Direct Accessibility via Shizuku missing: {token}")
for token in ["setStateListener", "onButtonsChanged"]:
    if token not in main_v060 + router_v054: raise SystemExit(f"Immediate button diagnostics missing: {token}")
for token in ["setOngoing(true)", "notification_connected_title", "action_disconnect", "EXTRA_OPEN_PROFILE"]:
    if token not in service_v060 + main_v060: raise SystemExit(f"Connected persistent notification missing: {token}")
print("v0.6.0 profiles + calibration + direct Accessibility + persistent notification: PASS")


# v0.6.1 single connection action + visual calibration wizard guards.
main_v061 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
wizard_v061 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibrationWizard.java").read_text()
visual_v061 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibrationInstructionView.java").read_text()
for token in ["connectionActionButton", "updateConnectionActionButton", "action_cancel_connection"]:
    if token not in main_v061:
        raise SystemExit(f"Single full-width connection action missing: {token}")
if "Button disconnect =" in main_v061 or "Button connect =" in main_v061:
    raise SystemExit("Connection card must not show Connect and Disconnect side by side")
for token in ["CalibrationInstructionView.Type.TABLE", "JOY_UP", "MOTION_UP", "MOTION_HOLD_MS = 3000L", "calibration_hold_top_countdown"]:
    if token not in wizard_v061 + visual_v061:
        raise SystemExit(f"Visual calibration wizard missing: {token}")
for token in ["ValueAnimator", "drawDirectionArrow", "drawTopButtonPulse", "drawTable"]:
    if token not in visual_v061:
        raise SystemExit(f"Calibration animation implementation missing: {token}")
if "profile_calibration_steps" not in main_v061 or "CalibrationInstructionView" not in main_v061:
    raise SystemExit("Calibration offer must use the styled visual panel")
print("v0.6.1 single connection action + animated calibration wizard: PASS")

# v0.7.0 merged typing / profiles / scroll / environment guards.
router_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
cursor_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
control_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
typing_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/TypingOverlayView.java").read_text()
service_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
priv_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PrivilegedInputService.java").read_text()
bridge_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ShizukuBridge.java").read_text()
aidl_v070 = (ROOT / "app/src/main/aidl/pl/openai/pokeballmouse/IPrivilegedInput.aidl").read_text()
main_v070 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
strings_en_v070 = (ROOT / "app/src/main/res/values/strings.xml").read_text()
strings_pl_v070 = (ROOT / "app/src/main/res/values-pl/strings.xml").read_text()

for token in ["TOP_MULTI_CLICK_WINDOW_MS", "topClickCount >= 3", "count == 2", "toggleTypingMode", "toggleScrollMode", "scrollByJoystick"]:
    if token not in router_v070 + cursor_v070:
        raise SystemExit(f"2x typing / 3x scroll state machine missing: {token}")
for token in ["TypingMode", "RADIAL", "KEYBOARD", "typingMode"]:
    if token not in control_v070:
        raise SystemExit(f"Typing mode configuration missing: {token}")
for token in ["RADIAL_LETTERS", '"123"', '"#+="', '"ABC"', "activateKey", "GRID_NUMBERS", "GRID_SYMBOLS"]:
    if token not in typing_v070:
        raise SystemExit(f"Single-character letters/numbers/symbols typing missing: {token}")
if '"ABC", "DEF"' in typing_v070 or '"ABC","DEF"' in typing_v070:
    raise SystemExit("Old grouped ABC/DEF radial typing must not return")
for token in ["editableFocus", "lastEditableViewId", "insertTextIntoNode", "bridge.text(insertion)"]:
    if token not in cursor_v070:
        raise SystemExit(f"Robust focused-field typing fallback missing: {token}")
for token in ["injectText(String text)", "KeyCharacterMap", "current.injectText(text)"]:
    if token not in aidl_v070 + priv_v070 + bridge_v070:
        raise SystemExit(f"Shizuku text injection fallback missing: {token}")
for token in ["fakeCenterEnabled", "setFakeCenterFromCurrent", "clearFakeCenter"]:
    if token not in control_v070 + router_v070:
        raise SystemExit(f"Quick fake-center support missing: {token}")
for token in ["DeviceProfileStore", "CalibrationWizard", "profile_calibration_steps"]:
    if token not in main_v070 + router_v070:
        raise SystemExit(f"Merged calibration/typing UI missing: {token}")
if "addTypingCard(root)" not in main_v070 and "addTypingCard(connectedSettingsContainer)" not in main_v070:
    raise SystemExit("Merged calibration/typing UI missing: typing card")
for token in ["BluetoothAdapter.ACTION_STATE_CHANGED", "LocationManager.MODE_CHANGED_ACTION", "disconnectForEnvironment", "service_location_off"]:
    if token not in service_v070 + strings_en_v070 + strings_pl_v070:
        raise SystemExit(f"Bluetooth/location disconnect tracking missing: {token}")
if "getMaximumWindowMetrics" not in cursor_v070:
    raise SystemExit("Tap-screen points must use whole-display metrics")
if "FLAG_LAYOUT_NO_LIMITS" not in cursor_v070 or "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES" not in cursor_v070:
    raise SystemExit("Global overlays must cover inset/cutout areas")
print("v0.7.0 merged calibration + single-character typing + robust text injection + 3x scroll + environment disconnect: PASS")

# v0.7.1 build-preflight guards: Accessibility text-field access + complete resource refs.
accessibility_xml = (ROOT / "app/src/main/res/xml/accessibility_config.xml").read_text()
for token in [
    'android:canRetrieveWindowContent="true"',
    'flagReportViewIds',
    'flagRetrieveInteractiveWindows',
    'android:canPerformGestures="true"',
]:
    if token not in accessibility_xml:
        raise SystemExit(f"Accessibility typing/window capability missing: {token}")
print("Accessibility editable-field/window retrieval: PASS")

# Collect custom resources and verify all non-android R references used from Java.
resource_names = {}
res_root = ROOT / "app/src/main/res"
for directory in res_root.iterdir():
    if not directory.is_dir():
        continue
    resource_type = directory.name.split("-")[0]
    for file in directory.iterdir():
        if not file.is_file():
            continue
        if resource_type == "values" and file.suffix == ".xml":
            tree = ET.parse(file)
            for element in tree.getroot():
                name = element.attrib.get("name")
                if name:
                    resource_names.setdefault(element.tag, set()).add(name)
        elif file.suffix == ".xml":
            resource_names.setdefault(resource_type, set()).add(file.stem)
        else:
            resource_names.setdefault(resource_type, set()).add(file.stem)

def resource_exists(resource_type, generated_name):
    names = resource_names.get(resource_type, set())
    if generated_name in names:
        return True
    # AAPT turns dots in style names into underscores in generated R fields.
    if resource_type == "style":
        return any(name.replace('.', '_') == generated_name for name in names)
    return False

missing_resources = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    for match in re.finditer(r"(?<!android\.)R\.([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)", text):
        resource_type, name = match.group(1), match.group(2)
        if not resource_exists(resource_type, name):
            line = text[:match.start()].count("\n") + 1
            missing_resources.append(f"{jf.relative_to(ROOT)}:{line}: R.{resource_type}.{name}")
if missing_resources:
    raise SystemExit("Missing custom resources:\n" + "\n".join(missing_resources))
print("All Java custom R.* references resolve: PASS")

# AIDL transaction numbers must be unique to avoid generator/dispatch collisions.
transaction_ids = []
for method in methods:
    match = re.search(r"=\s*(\d+)\s*;", method)
    if match:
        transaction_ids.append(int(match.group(1)))
if len(transaction_ids) != len(set(transaction_ids)):
    raise SystemExit(f"Duplicate AIDL transaction IDs: {transaction_ids}")
print("AIDL transaction IDs unique: PASS")

# CI versions intentionally match Android Gradle Plugin 8.10 compatibility.
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
root_gradle = (ROOT / "build.gradle").read_text()
app_gradle = (ROOT / "app/build.gradle").read_text()
for token in ["version '8.10.0'", "compileSdk 36", "gradle-version: '8.11.1'", "java-version: '17'", 'build-tools;35.0.0']:
    if token not in root_gradle + app_gradle + workflow:
        raise SystemExit(f"CI/AGP compatibility token missing: {token}")
print("AGP/Gradle/JDK/SDK CI compatibility pins: PASS")

# v0.7.2 idle/background + contextual UI + Top-to-type + graphic diagnostics guards.
main_v072 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
service_v072 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballService.java").read_text()
router_v072 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/InputRouter.java").read_text()
cursor_v072 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
ball_diag_v072 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballDiagnosticView.java").read_text()
for token in ["START_NOT_STICKY", "failAndStop", "stopForeground(STOP_FOREGROUND_REMOVE)", "stopSelf()"]:
    if token not in service_v072:
        raise SystemExit(f"Disconnected service idle behavior missing: {token}")
for token in ["setPokeballConnected", "frameRunning = false", "removeFrameCallback"]:
    if token not in cursor_v072:
        raise SystemExit(f"Disconnected Accessibility idle behavior missing: {token}")
if "service.selectTypingKey()" not in router_v072 or "service.typingBackspace()" not in router_v072:
    raise SystemExit("Typing must use Top to confirm and stick click as delete")
for token in ["connectedSettingsContainer", "updateModeSpecificVisibility", "config.mode() == ControlConfig.Mode.TOUCH", "advanced_settings"]:
    if token not in main_v072:
        raise SystemExit(f"Contextual connected/mode UI missing: {token}")
for token in ["scopedKey", "device_", 'textValue("mode"', 'bool("motion_enabled"', 'scopedKey(base + "_set")']:
    if token not in control_v070:
        raise SystemExit(f"Per-device control configuration missing: {token}")
for token in ["PokeballDiagnosticView", "setPressed", "topPressed", "stickPressed"]:
    if token not in main_v072 + ball_diag_v072:
        raise SystemExit(f"Graphic Poké Ball diagnostics missing: {token}")
print("v0.7.2 idle background + contextual UI + Top typing + graphic diagnostics: PASS")

# v0.7.3 Java top-level duplicate-method guard. This catches merge mistakes such as
# defining Service.onDestroy() twice while ignoring methods inside anonymous/nested classes.
def _brace_depths_java(text):
    depths = [0] * (len(text) + 1)
    depth = 0
    state = "code"
    i = 0
    while i < len(text):
        depths[i] = depth
        c = text[i]
        if state == "code":
            if c == "/" and i + 1 < len(text) and text[i + 1] == "/":
                depths[i + 1] = depth
                state = "line"
                i += 2
                continue
            if c == "/" and i + 1 < len(text) and text[i + 1] == "*":
                depths[i + 1] = depth
                state = "block"
                i += 2
                continue
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "{":
                depth += 1
            elif c == "}":
                depth = max(0, depth - 1)
        elif state == "line":
            if c == "\n":
                state = "code"
        elif state == "block":
            if c == "*" and i + 1 < len(text) and text[i + 1] == "/":
                depths[i + 1] = depth
                state = "code"
                i += 2
                continue
        elif state == "string":
            if c == "\\" and i + 1 < len(text):
                depths[i + 1] = depth
                i += 2
                continue
            if c == '"':
                state = "code"
        elif state == "char":
            if c == "\\" and i + 1 < len(text):
                depths[i + 1] = depth
                i += 2
                continue
            if c == "'":
                state = "code"
        i += 1
    depths[len(text)] = depth
    return depths

def _param_types(params):
    params = params.strip()
    if not params:
        return ()
    parts, current, generic_depth = [], "", 0
    for ch in params:
        if ch == "," and generic_depth == 0:
            parts.append(current)
            current = ""
            continue
        current += ch
        if ch == "<":
            generic_depth += 1
        elif ch == ">":
            generic_depth = max(0, generic_depth - 1)
    parts.append(current)
    result = []
    for part in parts:
        part = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", part).strip()
        part = re.sub(r"\bfinal\b\s*", "", part).strip()
        tokens = part.split()
        typ = " ".join(tokens[:-1]) if len(tokens) >= 2 else part
        result.append(re.sub(r"\s+", " ", typ))
    return tuple(result)

_top_method = re.compile(
    r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|protected|private|static|final|synchronized|native|abstract|default|strictfp)\s+)+"
    r"[\w<>\[\].?, @]+?\s+(\w+)\s*\(([^)]*)\)\s*(?:throws[^{]+)?\{"
)
duplicate_methods = []
for jf in java_files:
    text = jf.read_text(errors="ignore")
    depths = _brace_depths_java(text)
    seen = {}
    for match in _top_method.finditer(text):
        if depths[match.start()] != 1:
            continue
        signature = (match.group(1), _param_types(match.group(2)))
        line = text[:match.start()].count("\n") + 1
        seen.setdefault(signature, []).append(line)
    for signature, lines in seen.items():
        if len(lines) > 1:
            duplicate_methods.append(
                f"{jf.relative_to(ROOT)}:{lines}: {signature[0]}{signature[1]}"
            )
if duplicate_methods:
    raise SystemExit("Duplicate top-level Java methods:\n" + "\n".join(duplicate_methods))
print("Java top-level duplicate-method guard: PASS")


# v0.7.4 UI-copy/branding guards. Keep attribution only in Appearance and the app footer,
# and stop long helper paragraphs from creeping back into compact cards/dialogs.
main_v074 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
wizard_v074 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CalibrationWizard.java").read_text()
strings_paths_v074 = [
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-pl/strings.xml",
]
if "calibration_brand" in main_v074 + wizard_v074 + "\n".join(p.read_text() for p in strings_paths_v074):
    raise SystemExit("SimmSoft branding must not appear in calibration/configuration dialogs")
if main_v074.count("R.string.appearance_brand") != 2:
    raise SystemExit("SimmSoft attribution must appear exactly in Appearance dialog and app footer")
if "addAppFooter(root)" not in main_v074:
    raise SystemExit("Missing bottom-of-app SimmSoft footer")
for path in strings_paths_v074:
    root = ET.parse(path).getroot()
    values = {e.attrib.get("name"): "".join(e.itertext()).strip() for e in root if e.tag == "string"}
    if "3×" not in values.get("scroll_mode_on", ""):
        raise SystemExit(f"{path}: stale scroll shortcut copy; scroll toggle is 3× Top")
    for name, value in values.items():
        if any(name.endswith(suffix) for suffix in (
            "_subtitle", "_desc", "_description", "_note", "_message", "_offer", "_steps", "_shortcuts"
        )) and len(value) > 120:
            raise SystemExit(f"{path}: UI helper text too long ({len(value)} chars): {name}")
print("v0.7.4 compact copy + branding placement: PASS")

# v0.7.7 keyboard UX + unified Poké Ball visual guards.
typing_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/TypingOverlayView.java").read_text()
control_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ControlConfig.java").read_text()
main_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/MainActivity.java").read_text()
service_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/CursorAccessibilityService.java").read_text()
diag_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/PokeballDiagnosticView.java").read_text()
orb_v077 = (ROOT / "app/src/main/java/pl/openai/pokeballmouse/ConnectionOrbView.java").read_text()
launcher_v077 = (ROOT / "app/src/main/res/drawable/ic_launcher_pokeball.xml").read_text()
foreground_v077 = (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").read_text()
for token in ["RadialConfirmMode", "typingOverlayOpacity", "typing_radial_confirm", "typing_overlay_opacity"]:
    if token not in control_v077:
        raise SystemExit(f"v0.7.7 ControlConfig missing: {token}")
for token in ["updateRadialReleaseSelection", "radialDeflected", "releaseCandidate", "0.42f", "0.20f"]:
    if token not in typing_v077:
        raise SystemExit(f"v0.7.7 radial release-confirm missing: {token}")
if "drawText(pageLabel()" in typing_v077 or "typing_keyboard_footer" in typing_v077 or "typing_radial_footer" in typing_v077:
    raise SystemExit("v0.7.7 typing overlay must not show ABC/123/helper header/footer text")
for token in ['{"Q","W","E","R","T","Y","U","I","O","P"}',
              '{"A","S","D","F","G","H","J","K","L"}',
              '{"PL","Z","X","C","V","B","N","M","⌫"}']:
    if token not in typing_v077:
        raise SystemExit(f"v0.7.7 QWERTY geometry missing: {token}")
for token in ["typing_opacity_label", "typing_radial_confirm_title", "refreshTypingOverlay"]:
    if token not in main_v077:
        raise SystemExit(f"v0.7.7 typing settings UI missing: {token}")
if "new TypingOverlayView(this, mode, config" not in service_v077:
    raise SystemExit("Typing overlay must receive live per-device ControlConfig")
if "ball.right - bw * 1.75f" not in diag_v077:
    raise SystemExit("Diagnostic Top button must be moved onto the Poké Ball shell")
for text, label in [(orb_v077, "connection orb"), (launcher_v077, "launcher icon"), (foreground_v077, "adaptive foreground")]:
    if "226, 49, 60" not in text and "#FFE2313C" not in text:
        raise SystemExit(f"Unified Poké Ball red missing from {label}")
if "#FFF3F5F7" not in launcher_v077 or "M54,7L45,18H63Z" not in launcher_v077:
    raise SystemExit("Launcher direction arrows must remain present")
print("v0.7.7 typing UX + unified Poké Ball visuals: PASS")
print("Source verification: PASS")
