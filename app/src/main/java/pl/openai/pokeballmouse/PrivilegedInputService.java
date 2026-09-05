package pl.openai.pokeballmouse;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivilegedInputService extends IPrivilegedInput.Stub {
    private Object inputManager;
    private Method injectInputEvent;
    private Method setActionButton;

    private int buttons;
    private final Map<Integer, Long> buttonDownTimes = new HashMap<>();

    private static final class TouchPoint {
        final int id;
        final float x;
        final float y;
        TouchPoint(int id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }

    private final Map<Integer, TouchPoint> touches = new HashMap<>();
    private long touchDownTime;

    public PrivilegedInputService() { initializeReflection(); }
    public PrivilegedInputService(Context context) { initializeReflection(); }

    private void initializeReflection() {
        try {
            Class<?> global = Class.forName("android.hardware.input.InputManagerGlobal");
            Method getInstance = global.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManager = getInstance.invoke(null);
            injectInputEvent = global.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEvent.setAccessible(true);
        } catch (Throwable first) {
            try {
                Method getInstance = InputManager.class.getDeclaredMethod("getInstance");
                getInstance.setAccessible(true);
                inputManager = getInstance.invoke(null);
                injectInputEvent = InputManager.class.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
                injectInputEvent.setAccessible(true);
            } catch (Throwable second) {
                throw new IllegalStateException("Nie można uzyskać InputManager", second);
            }
        }
        try {
            setActionButton = MotionEvent.class.getDeclaredMethod("setActionButton", int.class);
            setActionButton.setAccessible(true);
        } catch (Throwable ignored) {
            setActionButton = null;
        }
    }

    @Override
    public synchronized boolean injectMouseMove(float x, float y) {
        long now = SystemClock.uptimeMillis();
        int action = buttons == 0 ? MotionEvent.ACTION_HOVER_MOVE : MotionEvent.ACTION_MOVE;
        long downTime = oldestDownTime(now);
        return inject(mouseEvent(downTime, now, action, x, y, buttons, 0));
    }

    @Override
    public synchronized boolean injectMouseButton(float x, float y, int button, boolean down) {
        long now = SystemClock.uptimeMillis();
        boolean ok = true;
        if (down) {
            if ((buttons & button) != 0) return true;
            buttonDownTimes.put(button, now);
            buttons |= button;
            long downTime = oldestDownTime(now);
            ok &= inject(mouseEvent(downTime, now, MotionEvent.ACTION_DOWN, x, y, buttons, button));
            ok &= inject(mouseEvent(downTime, now, MotionEvent.ACTION_BUTTON_PRESS, x, y, buttons, button));
        } else {
            if ((buttons & button) == 0) return true;
            long downTime = oldestDownTime(now);
            ok &= inject(mouseEvent(downTime, now, MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttons, button));
            buttons &= ~button;
            buttonDownTimes.remove(button);
            if (buttons == 0) {
                ok &= inject(mouseEvent(downTime, now, MotionEvent.ACTION_UP, x, y, 0, button));
            } else {
                ok &= inject(mouseEvent(oldestDownTime(now), now, MotionEvent.ACTION_MOVE, x, y, buttons, 0));
            }
        }
        return ok;
    }

    @Override
    public synchronized boolean injectKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        int source = isDpadKey(keyCode) ? InputDevice.SOURCE_DPAD : InputDevice.SOURCE_KEYBOARD;
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, source);
        KeyEvent up = new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, source);
        return inject(down) && inject(up);
    }

    @Override
    public synchronized boolean injectTouchPointer(int pointerId, float x, float y, boolean down) {
        if (pointerId < 0 || pointerId > 15) return false;
        long now = SystemClock.uptimeMillis();
        if (down) {
            if (touches.containsKey(pointerId)) return true;
            if (touches.isEmpty()) touchDownTime = now;
            touches.put(pointerId, new TouchPoint(pointerId, x, y));
            List<TouchPoint> ordered = orderedTouches();
            int index = indexOf(ordered, pointerId);
            int action = ordered.size() == 1
                    ? MotionEvent.ACTION_DOWN
                    : MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            return inject(touchEvent(touchDownTime, now, action, ordered));
        }

        TouchPoint existing = touches.get(pointerId);
        if (existing == null) return true;
        List<TouchPoint> ordered = orderedTouches();
        int index = indexOf(ordered, pointerId);
        int action = ordered.size() == 1
                ? MotionEvent.ACTION_UP
                : MotionEvent.ACTION_POINTER_UP | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        boolean ok = inject(touchEvent(touchDownTime, now, action, ordered));
        touches.remove(pointerId);
        if (touches.isEmpty()) touchDownTime = 0L;
        return ok;
    }

    @Override
    public synchronized boolean injectTap(float x, float y) {
        int id = 15;
        if (touches.containsKey(id)) return false;
        boolean down = injectTouchPointer(id, x, y, true);
        boolean up = injectTouchPointer(id, x, y, false);
        return down && up;
    }

    private List<TouchPoint> orderedTouches() {
        List<Integer> ids = new ArrayList<>(touches.keySet());
        Collections.sort(ids);
        List<TouchPoint> result = new ArrayList<>(ids.size());
        for (int id : ids) result.add(touches.get(id));
        return result;
    }

    private int indexOf(List<TouchPoint> points, int pointerId) {
        for (int i = 0; i < points.size(); i++) if (points.get(i).id == pointerId) return i;
        return 0;
    }

    private MotionEvent touchEvent(long downTime, long eventTime, int action, List<TouchPoint> points) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[points.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.size()];
        for (int i = 0; i < points.size(); i++) {
            TouchPoint point = points.get(i);
            MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
            p.id = point.id;
            p.toolType = MotionEvent.TOOL_TYPE_FINGER;
            props[i] = p;

            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
            c.x = point.x;
            c.y = point.y;
            c.pressure = 1f;
            c.size = 1f;
            coords[i] = c;
        }
        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                points.size(),
                props,
                coords,
                0,
                0,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
        );
    }

    private boolean isDpadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    private long oldestDownTime(long fallback) {
        long result = fallback;
        for (long value : buttonDownTimes.values()) result = Math.min(result, value);
        return result;
    }

    private MotionEvent mouseEvent(long downTime, long eventTime, int action,
                                   float x, float y, int buttonState, int actionButton) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_HOVER_MOVE) ? 0f : 1f;
        coords.size = 1f;

        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},
                0,
                buttonState,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_MOUSE,
                0
        );
        if (actionButton != 0 && setActionButton != null) {
            try { setActionButton.invoke(event, actionButton); } catch (Throwable ignored) {}
        }
        return event;
    }

    private boolean inject(InputEvent event) {
        try {
            Object result = injectInputEvent.invoke(inputManager, event, 0);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable t) {
            return false;
        } finally {
            if (event instanceof MotionEvent) ((MotionEvent) event).recycle();
        }
    }

    @Override
    public synchronized void destroy() {
        touches.clear();
        System.exit(0);
    }
}
