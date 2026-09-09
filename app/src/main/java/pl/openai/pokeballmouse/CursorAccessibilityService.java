package pl.openai.pokeballmouse;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

public class CursorAccessibilityService extends AccessibilityService {
    private static volatile CursorAccessibilityService instance;

    private WindowManager windowManager;
    private CursorOverlayView cursorView;
    private WindowManager.LayoutParams params;
    private TouchPickerOverlayView pickerView;
    private TypingOverlayView typingView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ControlConfig config;

    private float cursorX;
    private float cursorY;
    private float maxX;
    private float maxY;
    private long lastFrameNanos;
    private boolean cursorVisible;
    private boolean frameRunning;
    private boolean scrollGestureInProgress;

    private static final float DEAD_ZONE = 0.18f;
    private static final float MAX_SPEED_DP_PER_SEC = 1050f;

    public static CursorAccessibilityService getInstance() { return instance; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        config = new ControlConfig(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showCursor();
        lastFrameNanos = 0L;
        frameRunning = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    public void onDestroy() {
        frameRunning = false;
        try { Choreographer.getInstance().removeFrameCallback(frameCallback); } catch (Throwable ignored) {}
        handler.removeCallbacksAndMessages(null);
        removePicker();
        removeTypingOverlay();
        if (cursorView != null && windowManager != null) {
            try { windowManager.removeView(cursorView); } catch (Throwable ignored) {}
        }
        cursorView = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void showCursor() {
        Rect bounds = screenBounds();
        maxX = Math.max(1f, bounds.width() - 1f);
        maxY = Math.max(1f, bounds.height() - 1f);
        cursorX = bounds.width() / 2f;
        cursorY = bounds.height() / 2f;

        // Deliberately compact: close to the Android 14 pointer rather than a desktop-size cursor.
        int cursorWidth = Math.round(15f * getResources().getDisplayMetrics().density);
        int cursorHeight = Math.round(18f * getResources().getDisplayMetrics().density);
        cursorView = new CursorOverlayView(this);
        cursorView.setVisibility(View.GONE);
        cursorVisible = false;
        params = new WindowManager.LayoutParams(
                cursorWidth,
                cursorHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.round(cursorX - cursorView.hotspotXpx());
        params.y = Math.round(cursorY - cursorView.hotspotYpx());
        windowManager.addView(cursorView, params);
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!frameRunning) return;
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos;
            float dt = Math.min(0.05f, Math.max(0.001f,
                    (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
            lastFrameNanos = frameTimeNanos;

            boolean mouseMode = InputRouter.mode() == ControlConfig.Mode.MOUSE;
            boolean connected = PokeballService.isConnected();
            boolean cursorShouldBeVisible = connected && mouseMode && pickerView == null
                    && !InputRouter.typingActive() && !InputRouter.scrollMode();
            setCursorVisible(cursorShouldBeVisible);

            if (connected && mouseMode && !InputRouter.typingActive() && !InputRouter.scrollMode()) {
                float x = filtered(InputRouter.joyX());
                float y = filtered(InputRouter.joyY());
                if (x != 0f || y != 0f) {
                    float speed = MAX_SPEED_DP_PER_SEC * getResources().getDisplayMetrics().density;
                    float magnitude = Math.min(1f, (float) Math.sqrt(x * x + y * y));
                    float accel = 0.22f + 0.78f * magnitude * magnitude;
                    float oldX = cursorX;
                    float oldY = cursorY;
                    cursorX = clamp(cursorX + x * speed * accel * dt, 0f, maxX);
                    cursorY = clamp(cursorY - y * speed * accel * dt, 0f, maxY);
                    if (Math.abs(cursorX - oldX) >= 0.35f || Math.abs(cursorY - oldY) >= 0.35f) {
                        updateOverlayPosition();
                        // Shizuku receives mouse movement only while an actual drag is in progress.
                        // Merely showing/moving the visual pointer therefore cannot interfere with
                        // normal finger touches on the app underneath.
                        InputRouter.onMouseCursorMoved(cursorX, cursorY);
                    }
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void setCursorVisible(boolean visible) {
        if (cursorView == null || cursorVisible == visible) return;
        cursorVisible = visible;
        cursorView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private float filtered(float v) {
        float a = Math.abs(v);
        if (a <= DEAD_ZONE) return 0f;
        float normalized = (a - DEAD_ZONE) / (1f - DEAD_ZONE);
        return Math.copySign(normalized, v);
    }

    private void updateOverlayPosition() {
        if (cursorView == null || windowManager == null || params == null) return;
        params.x = Math.round(cursorX - cursorView.hotspotXpx());
        params.y = Math.round(cursorY - cursorView.hotspotYpx());
        try { windowManager.updateViewLayout(cursorView, params); }
        catch (IllegalArgumentException ignored) {}
    }

    private Rect screenBounds() {
        if (Build.VERSION.SDK_INT >= 30) return windowManager.getCurrentWindowMetrics().getBounds();
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return new Rect(0, 0, size.x, size.y);
    }

    private void refreshBounds() {
        if (windowManager == null) return;
        Rect bounds = screenBounds();
        maxX = Math.max(1f, bounds.width() - 1f);
        maxY = Math.max(1f, bounds.height() - 1f);
        cursorX = clamp(cursorX, 0f, maxX);
        cursorY = clamp(cursorY, 0f, maxY);
        updateOverlayPosition();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.post(this::refreshBounds);
    }

    public float cursorX() { return cursorX; }
    public float cursorY() { return cursorY; }

    public boolean hasBinding(ControlConfig.Binding binding) { return config.hasTouchPoint(binding); }

    public float[] bindingPoint(ControlConfig.Binding binding) {
        Rect b = screenBounds();
        return new float[]{config.touchX(binding) * b.width(), config.touchY(binding) * b.height()};
    }

    public void tapBinding(ControlConfig.Binding binding) {
        if (!hasBinding(binding)) return;
        float[] p = bindingPoint(binding);
        dispatchTap(p[0], p[1], 70L);
    }

    public void clickAtCursor() { dispatchTap(cursorX, cursorY, 70L); }
    public void longPressAtCursor() { dispatchTap(cursorX, cursorY, 650L); }

    public void setTypingVisible(boolean visible, ControlConfig.TypingMode mode) {
        handler.post(() -> {
            if (!visible) {
                removeTypingOverlay();
                return;
            }
            if (windowManager == null) return;
            if (typingView != null) {
                typingView.setMode(mode);
                return;
            }
            typingView = new TypingOverlayView(this, mode, new TypingOverlayView.Listener() {
                @Override public void onText(String text) { insertFocusedText(text); }
                @Override public void onBackspace() { deleteFocusedText(); }
                @Override public void onEnter() {
                    if (!insertFocusedText("\n")) {
                        ShizukuBridge bridge = ShizukuBridge.get();
                        if (bridge != null && bridge.isReady()) bridge.key(android.view.KeyEvent.KEYCODE_ENTER);
                    }
                }
            });
            WindowManager.LayoutParams typingParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            typingParams.gravity = Gravity.TOP | Gravity.START;
            try { windowManager.addView(typingView, typingParams); }
            catch (Throwable t) { typingView = null; }
        });
    }

    public void updateTypingJoystick(float x, float y, long nowMs) {
        handler.post(() -> { if (typingView != null) typingView.updateJoystick(x, y, nowMs); });
    }

    public void selectTypingKey() {
        handler.post(() -> { if (typingView != null) typingView.select(); });
    }

    public void typingBackspace() {
        handler.post(() -> {
            if (typingView != null) typingView.backspace();
            else deleteFocusedText();
        });
    }

    private void removeTypingOverlay() {
        if (typingView != null && windowManager != null) {
            try { windowManager.removeView(typingView); } catch (Throwable ignored) {}
        }
        typingView = null;
    }

    private AccessibilityNodeInfo editableFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node != null && node.isEditable()) return node;
        }
        // TYPE_ACCESSIBILITY_OVERLAY should not steal input focus, but some OEMs report
        // a different active window while the overlay is visible. Search all windows as a fallback.
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                AccessibilityNodeInfo windowRoot = window != null ? window.getRoot() : null;
                if (windowRoot == null) continue;
                AccessibilityNodeInfo node = windowRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (node != null && node.isEditable()) return node;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean insertFocusedText(String insertion) {
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) {
            Toast.makeText(this, R.string.typing_no_field, Toast.LENGTH_SHORT).show();
            return false;
        }
        CharSequence currentCs = node.getText();
        String current = currentCs == null ? "" : currentCs.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end < 0 || start > current.length() || end > current.length()) {
            start = end = current.length();
        }
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        String next = current.substring(0, left) + insertion + current.substring(right);
        Bundle set = new Bundle();
        set.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, set);
        if (ok) {
            int caret = left + insertion.length();
            Bundle selection = new Bundle();
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret);
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
        }
        if (!ok) Toast.makeText(this, R.string.typing_cannot_write, Toast.LENGTH_SHORT).show();
        return ok;
    }

    private boolean deleteFocusedText() {
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) {
            Toast.makeText(this, R.string.typing_no_field, Toast.LENGTH_SHORT).show();
            return false;
        }
        CharSequence currentCs = node.getText();
        String current = currentCs == null ? "" : currentCs.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end < 0 || start > current.length() || end > current.length()) start = end = current.length();
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        if (left == right) {
            if (left <= 0) return true;
            left--;
        }
        String next = current.substring(0, left) + current.substring(right);
        Bundle set = new Bundle();
        set.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, set);
        if (ok) {
            Bundle selection = new Bundle();
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, left);
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, left);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
        }
        return ok;
    }

    public void scrollByJoystick(float x, float y) {
        handler.post(() -> {
            if (scrollGestureInProgress || windowManager == null) return;
            float ax = Math.abs(x), ay = Math.abs(y);
            if (Math.max(ax, ay) < 0.38f) return;
            Rect b = screenBounds();
            float cx = b.width() * 0.50f;
            float cy = b.height() * 0.54f;
            float distance = Math.min(b.width(), b.height()) * 0.16f;
            float endX = cx;
            float endY = cy;
            if (ax >= ay) endX = cx - Math.copySign(distance, x);
            else endY = cy + Math.copySign(distance, y);
            Path path = new Path();
            path.moveTo(cx, cy);
            path.lineTo(endX, endY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 95L);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            scrollGestureInProgress = true;
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) { scrollGestureInProgress = false; }
                @Override public void onCancelled(GestureDescription gestureDescription) { scrollGestureInProgress = false; }
            }, handler);
            if (!accepted) scrollGestureInProgress = false;
        });
    }

    public void beginTouchPick(ControlConfig.Binding binding) {
        handler.post(() -> {
            if (windowManager == null) return;
            removePicker();
            String bindingLabel = UiLabels.binding(this, binding);
            pickerView = new TouchPickerOverlayView(this, bindingLabel, (rawX, rawY) -> {
                Rect b = screenBounds();
                float nx = clamp(rawX / Math.max(1f, b.width()), 0f, 1f);
                float ny = clamp(rawY / Math.max(1f, b.height()), 0f, 1f);
                config.setTouchPoint(binding, nx, ny);
                Toast.makeText(this,
                        getString(R.string.tap_saved, bindingLabel, Math.round(nx * 100), Math.round(ny * 100)),
                        Toast.LENGTH_SHORT).show();
                removePicker();
            });
            WindowManager.LayoutParams pickerParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            pickerParams.gravity = Gravity.TOP | Gravity.START;
            try { windowManager.addView(pickerView, pickerParams); }
            catch (Throwable t) { pickerView = null; }
        });
    }

    private void removePicker() {
        if (pickerView != null && windowManager != null) {
            try { windowManager.removeView(pickerView); } catch (Throwable ignored) {}
        }
        pickerView = null;
    }

    public boolean performBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean performHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean performRecents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public boolean performNotifications() { return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }
    public boolean performQuickSettings() { return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); }

    private void dispatchTap(float x, float y, long durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, durationMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
