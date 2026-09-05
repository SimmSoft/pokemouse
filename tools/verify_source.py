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

print("Source verification: PASS")
