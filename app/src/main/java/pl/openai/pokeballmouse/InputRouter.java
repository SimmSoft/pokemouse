package pl.openai.pokeballmouse;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Central state machine translating Poké Ball Plus reports into the selected control mode. */
public final class InputRouter {
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

    private static ControlConfig config;
    private static ControlConfig.Mode routedMode = ControlConfig.Mode.MOUSE;
    private static final MotionGestureDetector motionDetector = new MotionGestureDetector();
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
        if (config == null) config = new ControlConfig(context);
        routedMode = config.mode();
    }

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
        long now = SystemClock.uptimeMillis();

        if (!oldTop && top) {
            topGestureUsed = false;
            topHoldStartMs = now;
            motionTelemetryDetector.reset();
            liveMotionDirection = null;
            liveMotionTimestampMs = 0L;
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

        // Live preview obeys the same 300 ms arming rule as actual actions. During that
        // delay the baseline follows the hand, so pressing Top halfway through a swing
        // cannot turn that already-started movement into a gesture.
        if (top && liveMotionDirection == null) {
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

        if (cfg().motionEnabled() && !topGestureUsed) {
            MotionGestureDetector.Direction gesture = motionDetector.update(
                    motionX, motionY, motionZ, top, cfg().motionThreshold(), now);
            if (gesture != null) {
                topGestureUsed = true;
                lastMotion = gesture.name();
                ActionExecutor.execute(cfg().motionAction(toConfigDirection(gesture)));
            }
        } else {
            // Feed release/idle state so the detector rearms for the next Top hold.
            motionDetector.update(motionX, motionY, motionZ, false, cfg().motionThreshold(), now);
        }

        if (oldTop && !top) {
            boolean armedGestureHold = cfg().motionEnabled()
                    && now - topHoldStartMs >= MotionGestureDetector.ARM_DELAY_MS;
            // A short Top press keeps its normal button function. Once Top has been held
            // long enough to arm gesture mode, releasing it without a gesture does nothing
            // instead of producing an accidental right-click/back action.
            if (!topGestureUsed && !armedGestureHold) routeTopTap();
            topGestureUsed = false;
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


    public static synchronized void setJoystickCenterFromCurrent() {
        cfg().setJoystickCenter(rawJoyX, rawJoyY);
        applyJoystickCalibration();
    }

    public static synchronized void clearJoystickCenter() {
        cfg().clearJoystickCenter();
        applyJoystickCalibration();
    }

    private static void applyJoystickCalibration() {
        if (cfg().joystickCenterCalibrated()) {
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
        mousePressPending = false;
        mouseDragging = false;
        motionDetector.reset();
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
