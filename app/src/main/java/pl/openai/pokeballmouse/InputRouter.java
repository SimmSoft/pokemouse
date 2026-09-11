package pl.openai.pokeballmouse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Central state machine translating Poké Ball Plus reports into the selected control mode. */
public final class InputRouter {
    public interface StateListener { void onButtonsChanged(boolean topPressed, boolean stickPressed); }
    private InputRouter() {}

    private static volatile float rawJoyX;
    private static volatile float rawJoyY;
    private static volatile float joyX;
    private static volatile float joyY;
    private static volatile float accelX = Float.NaN;
    private static volatile float accelY = Float.NaN;
    private static volatile float accelZ = Float.NaN;
    private static volatile float gyroX = Float.NaN;
    private static volatile float gyroY = Float.NaN;
    private static volatile float gyroZ = Float.NaN;
    private static volatile float gyroW = Float.NaN;
    private static volatile float pitch = Float.NaN;
    private static volatile float yaw = Float.NaN;
    private static volatile float roll = Float.NaN;
    private static volatile boolean topPressed;
    private static volatile boolean stickPressed;
    private static volatile String lastMotion = "—";
    private static volatile StateListener stateListener;
    private static volatile boolean calibrationMode;
    private static volatile boolean typingActive;
    private static volatile boolean scrollMode;
    private static Context appContext;
    private static Handler mainHandler;
    private static int topClickCount;
    private static long lastTopClickMs;
    private static final long TOP_MULTI_CLICK_WINDOW_MS = 260L;
    private static long nextScrollMs;
    private static final long SCROLL_REPEAT_MS = 115L;

    private static ControlConfig config;
    private static ControlConfig.Mode routedMode = ControlConfig.Mode.MOUSE;
    private static final MotionGestureDetector motionDetector = new MotionGestureDetector();
    private static final CalibratedMotionGestureDetector calibratedMotionDetector = new CalibratedMotionGestureDetector();
    private static final MotionTelemetryDetector motionTelemetryDetector = new MotionTelemetryDetector();
    private static volatile MotionTelemetryDetector.Direction liveMotionDirection;
    private static volatile long liveMotionTimestampMs;
    private static boolean topGestureUsed;
    private static long topHoldStartMs;

    private static boolean mousePressPending;
    private static boolean mouseDragging;
    private static float mousePressX;
    private static float mousePressY;
    private static final float MOUSE_DRAG_START_PX = 18f;

    private static int dpadDirection; // 0 neutral, 1 up, 2 down, 3 left, 4 right
    private static long nextDpadRepeatMs;
    private static final long DPAD_INITIAL_REPEAT_MS = 420L;
    private static final long DPAD_REPEAT_MS = 120L;

    private static boolean touchUp, touchDown, touchLeft, touchRight;

    public static synchronized void init(Context context) {
        DeviceProfileStore.init(context);
        appContext = context.getApplicationContext();
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        if (config == null) config = new ControlConfig(context);
        routedMode = config.mode();
    }

    public static void setStateListener(StateListener listener) { stateListener = listener; }
    public static void setCalibrationMode(boolean enabled) { calibrationMode = enabled; }
    public static boolean calibrationMode() { return calibrationMode; }

    public static synchronized void setActiveDevice(String address, String bluetoothName) {
        DeviceProfileStore.get().setActiveAddress(address, bluetoothName);
        motionDetector.reset();
        calibratedMotionDetector.reset();
        motionTelemetryDetector.reset();
        applyJoystickCalibration();
    }

    public static DeviceProfileStore.Profile activeProfile() { return DeviceProfileStore.get().activeProfile(); }

    private static ControlConfig cfg() {
        if (config == null) throw new IllegalStateException("InputRouter.init() not called");
        return config;
    }

    public static float rawJoyX() { return rawJoyX; }
    public static float rawJoyY() { return rawJoyY; }
    public static float joyX() { return joyX; }
    public static float joyY() { return joyY; }
    public static boolean joystickCenterCalibrated() { return cfg().joystickCenterCalibrated(); }
    public static float joystickCenterX() { return cfg().joystickCenterX(); }
    public static float joystickCenterY() { return cfg().joystickCenterY(); }
    public static float accelX() { return accelX; }
    public static float accelY() { return accelY; }
    public static float accelZ() { return accelZ; }
    public static float gyroX() { return gyroX; }
    public static float gyroY() { return gyroY; }
    public static float gyroZ() { return gyroZ; }
    public static float gyroW() { return gyroW; }
    public static float pitch() { return pitch; }
    public static float yaw() { return yaw; }
    public static float roll() { return roll; }
    public static boolean topPressed() { return topPressed; }
    public static boolean stickPressed() { return stickPressed; }
    public static String lastMotion() { return lastMotion; }
    public static MotionTelemetryDetector.Direction liveMotionDirection() { return liveMotionDirection; }
    public static long liveMotionTimestampMs() { return liveMotionTimestampMs; }
    public static ControlConfig.Mode mode() { return cfg().mode(); }
    public static boolean typingActive() { return typingActive; }
    public static boolean scrollMode() { return scrollMode && cfg().mode() == ControlConfig.Mode.MOUSE; }
    public static boolean scrollModeEnabled() { return scrollMode(); }

    public static synchronized void onPacket(float x, float y, boolean top, boolean stick,
                                             float ax, float ay, float az,
                                             float gx, float gy, float gz, float gw,
                                             float pitchValue, float yawValue, float rollValue) {
        rawJoyX = clamp(x);
        rawJoyY = clamp(y);
        applyJoystickCalibration();
        accelX = ax;
        accelY = ay;
        accelZ = az;
        gyroX = gx; gyroY = gy; gyroZ = gz; gyroW = gw;
        pitch = pitchValue; yaw = yawValue; roll = rollValue;

        ControlConfig.Mode currentMode = cfg().mode();
        if (currentMode != routedMode) {
            releaseModeState(routedMode);
            routedMode = currentMode;
        }

        boolean oldTop = topPressed;
        boolean oldStick = stickPressed;
        topPressed = top;
        stickPressed = stick;
        if (oldTop != top || oldStick != stick) {
            StateListener listener = stateListener;
            if (listener != null) listener.onButtonsChanged(top, stick);
        }
        long now = SystemClock.uptimeMillis();

        if (!oldTop && top) {
            topGestureUsed = false;
            topHoldStartMs = now;
            motionTelemetryDetector.reset();
            liveMotionDirection = null;
            liveMotionTimestampMs = 0L;
        }

        if (calibrationMode) {
            if (!top) {
                motionDetector.update(ax, ay, az, false, cfg().motionThreshold(), now);
                calibratedMotionDetector.update(ax, ay, az, false, cfg().motionThreshold(), now, DeviceProfileStore.get().motionTemplates());
            }
            return;
        }

        if (oldStick != stick) {
            routeStickButton(stick);
        }

        float motionX = ax;
        float motionY = ay;
        float motionZ = az;
        if (cfg().motionSwapAxes()) { float tmp = motionX; motionX = motionY; motionY = tmp; }
        if (cfg().motionInvertX()) { motionX = -motionX; motionZ = -motionZ; }
        if (cfg().motionInvertY()) motionY = -motionY;

        // Live preview obeys the same short Top-button settle delay as actual actions.
        // During that interval the baseline follows the hand so the physical button press
        // itself is not mistaken for a gesture.
        if (!typingActive && !scrollMode && top && liveMotionDirection == null) {
            if (now - topHoldStartMs < MotionGestureDetector.ARM_DELAY_MS) {
                motionTelemetryDetector.prime(motionX, motionY, motionZ);
            } else {
                MotionTelemetryDetector.Direction liveDirection = motionTelemetryDetector.update(
                        motionX, motionY, motionZ, Math.max(0.40f, cfg().motionThreshold() * 0.90f), now);
                if (liveDirection != null) {
                    liveMotionDirection = liveDirection;
                    liveMotionTimestampMs = now;
                }
            }
        }

        if (!typingActive && !scrollMode && cfg().motionEnabled() && !topGestureUsed) {
            DeviceProfileStore.MotionTemplates templates = DeviceProfileStore.get().motionTemplates();
            MotionGestureDetector.Direction gesture = templates != null
                    ? calibratedMotionDetector.update(ax, ay, az, top, cfg().motionThreshold(), now, templates)
                    : motionDetector.update(motionX, motionY, motionZ, top, cfg().motionThreshold(), now);
            if (gesture != null) {
                topGestureUsed = true;
                lastMotion = gesture.name();
                // Make the live label agree with the actionable four-way gesture even when
                // raw X/Z telemetry would otherwise look like forward/backward motion.
                liveMotionDirection = toLiveDirection(gesture);
                liveMotionTimestampMs = now;
                ActionExecutor.execute(cfg().motionAction(toConfigDirection(gesture)));
            }
        } else {
            // Feed release/idle state so both detectors rearm for the next Top hold.
            motionDetector.update(motionX, motionY, motionZ, false, cfg().motionThreshold(), now);
            calibratedMotionDetector.update(motionX, motionY, motionZ, false, cfg().motionThreshold(), now, DeviceProfileStore.get().motionTemplates());
        }

        if (oldTop && !top) {
            long heldMs = now - topHoldStartMs;
            // The gesture detector now arms after only ~0.1 s. Do not let that tiny delay
            // steal ordinary Top clicks: only suppress the normal Top action after a real
            // gesture, or after a clearly intentional long hold.
            boolean intentionalLongGestureHold = cfg().motionEnabled() && heldMs >= 500L;
            if (!topGestureUsed && !intentionalLongGestureHold) registerTopClick(now);
            topGestureUsed = false;
        }

        if (typingActive) {
            CursorAccessibilityService service = CursorAccessibilityService.getInstance();
            if (service != null) service.updateTypingJoystick(joyX, joyY, now);
            dpadDirection = 0;
            return;
        }

        if (scrollMode) {
            updateScroll(now);
            dpadDirection = 0;
            return;
        }

        switch (currentMode) {
            case DPAD:
                updateDpad(now);
                break;
            case TOUCH:
                updateTouchDirections();
                break;
            case MOUSE:
            default:
                dpadDirection = 0;
        }
    }


    public static synchronized void setFakeCenterFromCurrent() {
        cfg().setFakeCenter(rawJoyX, rawJoyY);
        applyJoystickCalibration();
    }

    public static synchronized void clearFakeCenter() {
        cfg().clearFakeCenter();
        applyJoystickCalibration();
    }

    public static boolean fakeCenterEnabled() { return cfg().fakeCenterEnabled(); }

    public static synchronized void setJoystickCenterFromCurrent() {
        cfg().setJoystickCenter(rawJoyX, rawJoyY);
        applyJoystickCalibration();
    }

    public static synchronized void clearJoystickCenter() {
        cfg().clearJoystickCenter();
        applyJoystickCalibration();
    }

    private static void applyJoystickCalibration() {
        if (cfg().fakeCenterEnabled()) {
            joyX = JoystickCalibration.applyAxis(rawJoyX, cfg().fakeCenterX());
            joyY = JoystickCalibration.applyAxis(rawJoyY, cfg().fakeCenterY());
            return;
        }
        DeviceProfileStore.Profile profile = DeviceProfileStore.get().activeProfile();
        if (profile != null && profile.joystickCalibrated) {
            joyX = JoystickCalibration.applyAxis(rawJoyX, profile.centerX, profile.minX, profile.maxX);
            joyY = JoystickCalibration.applyAxis(rawJoyY, profile.centerY, profile.minY, profile.maxY);
        } else if (cfg().joystickCenterCalibrated()) {
            joyX = JoystickCalibration.applyAxis(rawJoyX, cfg().joystickCenterX());
            joyY = JoystickCalibration.applyAxis(rawJoyY, cfg().joystickCenterY());
        } else {
            joyX = rawJoyX;
            joyY = rawJoyY;
        }
    }

    public static synchronized void onModeChanged() {
        ControlConfig.Mode newMode = cfg().mode();
        if (newMode == routedMode) return;
        releaseModeState(routedMode);
        resetTopClicks();
        disableTypingMode();
        disableScrollMode();
        routedMode = newMode;
        if (newMode == ControlConfig.Mode.TOUCH) updateTouchDirections();
    }

    public static synchronized void onShizukuReady() {
        // Re-assert only state that is meaningful in the active mode.
        if (routedMode == ControlConfig.Mode.TOUCH) {
            touchUp = touchDown = touchLeft = touchRight = false;
            updateTouchDirections();
            if (stickPressed) touchBinding(ControlConfig.Binding.STICK_CLICK, pointerId(ControlConfig.Binding.STICK_CLICK), true);
        }
    }

    private static void routeStickButton(boolean down) {
        if (typingActive) {
            // During typing the Top button confirms the highlighted character.
            // Keep the joystick click useful as Backspace instead of a second confirm button.
            if (down) {
                CursorAccessibilityService service = CursorAccessibilityService.getInstance();
                if (service != null) service.typingBackspace();
            }
            return;
        }
        switch (routedMode) {
            case MOUSE:
                handleMouseStickButton(down);
                break;
            case DPAD:
                if (down) key(KeyEvent.KEYCODE_DPAD_CENTER);
                break;
            case TOUCH:
                touchBinding(ControlConfig.Binding.STICK_CLICK,
                        pointerId(ControlConfig.Binding.STICK_CLICK), down);
                break;
        }
    }

    private static void registerTopClick(long now) {
        boolean firstInSequence = now - lastTopClickMs > TOP_MULTI_CLICK_WINDOW_MS || topClickCount <= 0;
        if (firstInSequence) {
            topClickCount = 0;
            if (typingActive) {
                CursorAccessibilityService service = CursorAccessibilityService.getInstance();
                if (service != null) {
                    service.clearCapturedTypingSelection();
                    service.captureTypingSelection();
                }
            }
        }
        lastTopClickMs = now;
        topClickCount++;
        if (mainHandler != null) mainHandler.removeCallbacks(resolveTopClicks);

        if (topClickCount >= 3) {
            topClickCount = 0;
            CursorAccessibilityService service = CursorAccessibilityService.getInstance();
            if (service != null) service.clearCapturedTypingSelection();
            toggleScrollMode();
            return;
        }
        if (mainHandler != null) mainHandler.postDelayed(resolveTopClicks, TOP_MULTI_CLICK_WINDOW_MS);
    }

    private static final Runnable resolveTopClicks = () -> {
        synchronized (InputRouter.class) {
            int count = topClickCount;
            topClickCount = 0;
            if (count == 1) {
                if (typingActive) {
                    CursorAccessibilityService service = CursorAccessibilityService.getInstance();
                    if (service != null) service.selectTypingKey();
                } else {
                    routeTopTap();
                }
            } else if (count == 2) {
                CursorAccessibilityService service = CursorAccessibilityService.getInstance();
                if (service != null) service.clearCapturedTypingSelection();
                toggleTypingMode();
            }
        }
    };

    private static void resetTopClicks() {
        topClickCount = 0;
        lastTopClickMs = 0L;
        if (mainHandler != null) mainHandler.removeCallbacks(resolveTopClicks);
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service != null) service.clearCapturedTypingSelection();
    }

    private static void stopMouseDragForAuxMode() {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (mouseDragging && service != null) {
            ShizukuBridge bridge = ShizukuBridge.get();
            if (bridge != null && bridge.isReady()) {
                bridge.button(service.cursorX(), service.cursorY(), MotionEvent.BUTTON_PRIMARY, false);
            }
        }
        mousePressPending = false;
        mouseDragging = false;
    }

    private static void toggleTypingMode() {
        stopMouseDragForAuxMode();
        scrollMode = false;
        nextScrollMs = 0L;
        typingActive = !typingActive;
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service != null) {
            service.clearCapturedTypingSelection();
            service.setTypingVisible(typingActive, cfg().typingMode());
        }
        toast(typingActive ? R.string.typing_enabled_toast : R.string.typing_disabled_toast);
    }

    private static void disableTypingMode() {
        if (!typingActive) return;
        typingActive = false;
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service != null) {
            service.clearCapturedTypingSelection();
            service.setTypingVisible(false, cfg().typingMode());
        }
    }

    private static void toggleScrollMode() {
        if (routedMode != ControlConfig.Mode.MOUSE) return;
        stopMouseDragForAuxMode();
        disableTypingMode();
        scrollMode = !scrollMode;
        nextScrollMs = 0L;
        toast(scrollMode ? R.string.scroll_enabled_toast : R.string.scroll_disabled_toast);
    }

    private static void disableScrollMode() {
        scrollMode = false;
        nextScrollMs = 0L;
    }

    private static void updateScroll(long now) {
        if (now < nextScrollMs) return;
        float ax = Math.abs(joyX), ay = Math.abs(joyY);
        if (Math.max(ax, ay) < 0.38f) return;
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service == null) return;
        service.scrollByJoystick(joyX, joyY);
        nextScrollMs = now + SCROLL_REPEAT_MS;
    }

    private static void toast(int stringRes) {
        if (appContext == null || mainHandler == null) return;
        mainHandler.post(() -> Toast.makeText(appContext, stringRes, Toast.LENGTH_SHORT).show());
    }

    private static void routeTopTap() {
        switch (routedMode) {
            case MOUSE:
                mouseClickSecondary();
                break;
            case DPAD:
                key(KeyEvent.KEYCODE_BACK);
                break;
            case TOUCH:
                tapBinding(ControlConfig.Binding.TOP_CLICK);
                break;
        }
    }

    private static void mouseButton(int button, boolean down) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        float x = service != null ? service.cursorX() : 0f;
        float y = service != null ? service.cursorY() : 0f;
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady()) {
            bridge.button(x, y, button, down);
            return;
        }
        if (service != null && down && button == MotionEvent.BUTTON_PRIMARY) service.clickAtCursor();
    }

    private static void handleMouseStickButton(boolean down) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service == null) return;
        if (down) {
            mousePressPending = true;
            mouseDragging = false;
            mousePressX = service.cursorX();
            mousePressY = service.cursorY();
            return;
        }

        if (mouseDragging) {
            ShizukuBridge bridge = ShizukuBridge.get();
            if (bridge != null && bridge.isReady()) {
                bridge.button(service.cursorX(), service.cursorY(), MotionEvent.BUTTON_PRIMARY, false);
            }
        } else if (mousePressPending) {
            // A normal click uses Accessibility instead of injecting a permanent mouse stream.
            // This keeps finger touch fully usable while the visual cursor is on screen.
            service.clickAtCursor();
        }
        mousePressPending = false;
        mouseDragging = false;
    }

    public static synchronized void onMouseCursorMoved(float x, float y) {
        if (routedMode != ControlConfig.Mode.MOUSE || !stickPressed || !mousePressPending) return;
        float dx = x - mousePressX;
        float dy = y - mousePressY;
        if (!mouseDragging && dx * dx + dy * dy >= MOUSE_DRAG_START_PX * MOUSE_DRAG_START_PX) {
            ShizukuBridge bridge = ShizukuBridge.get();
            if (bridge != null && bridge.isReady()) {
                bridge.button(mousePressX, mousePressY, MotionEvent.BUTTON_PRIMARY, true);
                mouseDragging = true;
            }
        }
        if (mouseDragging) {
            ShizukuBridge bridge = ShizukuBridge.get();
            if (bridge != null && bridge.isReady()) bridge.move(x, y);
        }
    }

    public static boolean isMouseDragging() { return mouseDragging; }

    private static void mouseClickSecondary() {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service == null) return;
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady()) {
            bridge.button(service.cursorX(), service.cursorY(), MotionEvent.BUTTON_SECONDARY, true);
            bridge.button(service.cursorX(), service.cursorY(), MotionEvent.BUTTON_SECONDARY, false);
        } else {
            service.longPressAtCursor();
        }
    }

    private static void updateDpad(long now) {
        int direction = cardinalDirection(joyX, joyY, 0.46f);
        if (direction == 0) {
            dpadDirection = 0;
            nextDpadRepeatMs = 0L;
            return;
        }
        if (direction != dpadDirection) {
            dpadDirection = direction;
            injectDpadDirection(direction);
            nextDpadRepeatMs = now + DPAD_INITIAL_REPEAT_MS;
            return;
        }
        if (now >= nextDpadRepeatMs) {
            injectDpadDirection(direction);
            nextDpadRepeatMs = now + DPAD_REPEAT_MS;
        }
    }

    private static void injectDpadDirection(int direction) {
        switch (direction) {
            case 1: key(KeyEvent.KEYCODE_DPAD_UP); break;
            case 2: key(KeyEvent.KEYCODE_DPAD_DOWN); break;
            case 3: key(KeyEvent.KEYCODE_DPAD_LEFT); break;
            case 4: key(KeyEvent.KEYCODE_DPAD_RIGHT); break;
            default:
        }
    }

    private static void updateTouchDirections() {
        boolean wantLeft = joyX < -0.48f;
        boolean wantRight = joyX > 0.48f;
        boolean wantUp = joyY > 0.48f;
        boolean wantDown = joyY < -0.48f;

        if (wantUp != touchUp) {
            touchUp = wantUp;
            touchBinding(ControlConfig.Binding.JOY_UP, pointerId(ControlConfig.Binding.JOY_UP), wantUp);
        }
        if (wantDown != touchDown) {
            touchDown = wantDown;
            touchBinding(ControlConfig.Binding.JOY_DOWN, pointerId(ControlConfig.Binding.JOY_DOWN), wantDown);
        }
        if (wantLeft != touchLeft) {
            touchLeft = wantLeft;
            touchBinding(ControlConfig.Binding.JOY_LEFT, pointerId(ControlConfig.Binding.JOY_LEFT), wantLeft);
        }
        if (wantRight != touchRight) {
            touchRight = wantRight;
            touchBinding(ControlConfig.Binding.JOY_RIGHT, pointerId(ControlConfig.Binding.JOY_RIGHT), wantRight);
        }
    }

    private static void touchBinding(ControlConfig.Binding binding, int pointerId, boolean down) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service == null || !service.hasBinding(binding)) return;
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady()) {
            float[] p = service.bindingPoint(binding);
            bridge.touch(pointerId, p[0], p[1], down);
        } else if (down) {
            // Accessibility fallback can tap, but cannot emulate persistent multi-touch reliably.
            service.tapBinding(binding);
        }
    }

    private static void tapBinding(ControlConfig.Binding binding) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service == null || !service.hasBinding(binding)) return;
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady()) {
            float[] p = service.bindingPoint(binding);
            bridge.tap(p[0], p[1]);
        } else {
            service.tapBinding(binding);
        }
    }

    private static void key(int keyCode) {
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady()) bridge.key(keyCode);
    }

    private static void releaseModeState(ControlConfig.Mode mode) {
        if (mode == ControlConfig.Mode.MOUSE) {
            CursorAccessibilityService service = CursorAccessibilityService.getInstance();
            if (mouseDragging && service != null) {
                ShizukuBridge bridge = ShizukuBridge.get();
                if (bridge != null && bridge.isReady()) {
                    bridge.button(service.cursorX(), service.cursorY(), MotionEvent.BUTTON_PRIMARY, false);
                }
            }
            mousePressPending = false;
            mouseDragging = false;
        }
        if (mode == ControlConfig.Mode.TOUCH) {
            if (touchUp) touchBinding(ControlConfig.Binding.JOY_UP, pointerId(ControlConfig.Binding.JOY_UP), false);
            if (touchDown) touchBinding(ControlConfig.Binding.JOY_DOWN, pointerId(ControlConfig.Binding.JOY_DOWN), false);
            if (touchLeft) touchBinding(ControlConfig.Binding.JOY_LEFT, pointerId(ControlConfig.Binding.JOY_LEFT), false);
            if (touchRight) touchBinding(ControlConfig.Binding.JOY_RIGHT, pointerId(ControlConfig.Binding.JOY_RIGHT), false);
            if (stickPressed) touchBinding(ControlConfig.Binding.STICK_CLICK, pointerId(ControlConfig.Binding.STICK_CLICK), false);
            touchUp = touchDown = touchLeft = touchRight = false;
        }
        dpadDirection = 0;
        nextDpadRepeatMs = 0L;
    }

    public static synchronized void reset() {
        releaseModeState(routedMode);
        rawJoyX = 0f;
        rawJoyY = 0f;
        joyX = 0f;
        joyY = 0f;
        accelX = accelY = accelZ = Float.NaN;
        gyroX = gyroY = gyroZ = gyroW = Float.NaN;
        pitch = yaw = roll = Float.NaN;
        topPressed = false;
        stickPressed = false;
        topGestureUsed = false;
        resetTopClicks();
        disableTypingMode();
        disableScrollMode();
        mousePressPending = false;
        mouseDragging = false;
        motionDetector.reset();
        calibratedMotionDetector.reset();
        motionTelemetryDetector.reset();
        liveMotionDirection = null;
        liveMotionTimestampMs = 0L;
        lastMotion = "—";
    }

    private static int cardinalDirection(float x, float y, float threshold) {
        float ax = Math.abs(x), ay = Math.abs(y);
        if (Math.max(ax, ay) < threshold) return 0;
        if (ax >= ay) return x >= 0f ? 4 : 3;
        return y >= 0f ? 1 : 2;
    }

    private static int pointerId(ControlConfig.Binding binding) {
        switch (binding) {
            case JOY_UP: return 1;
            case JOY_DOWN: return 2;
            case JOY_LEFT: return 3;
            case JOY_RIGHT: return 4;
            case STICK_CLICK: return 5;
            case TOP_CLICK: return 6;
            default: return 9;
        }
    }

    private static MotionTelemetryDetector.Direction toLiveDirection(MotionGestureDetector.Direction direction) {
        switch (direction) {
            case LEFT: return MotionTelemetryDetector.Direction.LEFT;
            case RIGHT: return MotionTelemetryDetector.Direction.RIGHT;
            case UP: return MotionTelemetryDetector.Direction.UP;
            case DOWN: return MotionTelemetryDetector.Direction.DOWN;
            default: throw new IllegalArgumentException();
        }
    }

    private static ControlConfig.MotionDirection toConfigDirection(MotionGestureDetector.Direction direction) {
        switch (direction) {
            case LEFT: return ControlConfig.MotionDirection.LEFT;
            case RIGHT: return ControlConfig.MotionDirection.RIGHT;
            case UP: return ControlConfig.MotionDirection.UP;
            case DOWN: return ControlConfig.MotionDirection.DOWN;
            default: throw new IllegalArgumentException();
        }
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }
}
