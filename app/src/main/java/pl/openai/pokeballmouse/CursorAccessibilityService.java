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
    private String lastEditableViewId;
    private int lastEditableWindowId = -1;
    private final Rect lastEditableBounds = new Rect();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ControlConfig config;

    private float cursorX;
    private float cursorY;
    private float maxX;
    private float maxY;
    private long lastFrameNanos;
    private boolean cursorVisible;
    private boolean frameRunning;
    private boolean scrollGestureInFlight;

    private static final float DEAD_ZONE = 0.18f;
    private static final float SCROLL_DEAD_ZONE = 0.24f;
    private static final float MAX_SPEED_DP_PER_SEC = 1050f;
    private static final long SCROLL_GESTURE_DURATION_MS = 90L;
    private static final float SCROLL_MIN_DISTANCE_DP = 54f;
    private static final float SCROLL_MAX_DISTANCE_DP = 190f;

    public static CursorAccessibilityService getInstance() { return instance; }

    /** Keeps the accessibility service idle when no Poké Ball Plus is connected. */
    public void setPokeballConnected(boolean connected) {
        handler.post(() -> {
            if (connected) {
                if (cursorView == null && windowManager != null) showCursor();
                if (!frameRunning) {
                    lastFrameNanos = 0L;
                    frameRunning = true;
                    Choreographer.getInstance().postFrameCallback(frameCallback);
                }
            } else {
                frameRunning = false;
                try { Choreographer.getInstance().removeFrameCallback(frameCallback); } catch (Throwable ignored) {}
                setCursorVisible(false);
                removePicker();
                removeTypingOverlay();
                scrollGestureInFlight = false;
                if (cursorView != null && windowManager != null) {
                    try { windowManager.removeView(cursorView); } catch (Throwable ignored) {}
                    cursorView = null;
                }
            }
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        config = new ControlConfig(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setPokeballConnected(PokeballService.isConnected());
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

        // Keep one full-screen NOT_TOUCHABLE overlay and move only the drawing inside it.
        // This avoids a WindowManager IPC/updateViewLayout call for every joystick frame.
        cursorView = new CursorOverlayView(this);
        cursorView.setVisibility(View.GONE);
        cursorVisible = false;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(cursorView, params);
        cursorView.setPointerPosition(cursorX, cursorY);
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
            boolean typing = InputRouter.typingActive();
            boolean scrollMode = InputRouter.scrollMode();
            boolean cursorShouldBeVisible = connected && mouseMode && !typing && !scrollMode && pickerView == null;
            setCursorVisible(cursorShouldBeVisible);

            if (connected && mouseMode && !typing && !scrollMode) {
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

    private float scrollFiltered(float v) {
        float a = Math.abs(v);
        if (a <= SCROLL_DEAD_ZONE) return 0f;
        float normalized = (a - SCROLL_DEAD_ZONE) / (1f - SCROLL_DEAD_ZONE);
        return Math.copySign(normalized, v);
    }

    public void onScrollModeChanged(boolean enabled) {
        handler.post(() -> {
            if (!enabled) scrollGestureInFlight = false;
            setCursorVisible(PokeballService.isConnected()
                    && InputRouter.mode() == ControlConfig.Mode.MOUSE
                    && !enabled && pickerView == null);
        });
    }

    private void dispatchScrollFromJoystick(float x, float y) {
        if (scrollGestureInFlight || pickerView != null) return;
        float magnitude = Math.min(1f, (float)Math.sqrt(x*x + y*y));
        if (magnitude <= 0.001f) return;

        Rect b = screenBounds();
        float density = getResources().getDisplayMetrics().density;
        float minDistance = SCROLL_MIN_DISTANCE_DP * density;
        float maxDistance = SCROLL_MAX_DISTANCE_DP * density;
        float distance = minDistance + (maxDistance - minDistance) * magnitude * magnitude;

        float cx = b.left + b.width() * 0.50f;
        float cy = b.top + b.height() * 0.50f;
        // Standard scroll semantics: joystick down scrolls the page down, which is
        // implemented by an upward finger swipe. Horizontal scrolling follows the same rule.
        float endX = cx - (x / magnitude) * distance;
        float endY = cy + (y / magnitude) * distance;
        float marginX = b.width() * 0.12f;
        float marginY = b.height() * 0.12f;
        endX = clamp(endX, b.left + marginX, b.right - marginX);
        endY = clamp(endY, b.top + marginY, b.bottom - marginY);

        Path path = new Path();
        path.moveTo(cx, cy);
        path.lineTo(endX, endY);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, SCROLL_GESTURE_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        scrollGestureInFlight = true;
        boolean accepted = dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                scrollGestureInFlight = false;
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                scrollGestureInFlight = false;
            }
        }, null);
        if (!accepted) scrollGestureInFlight = false;
    }

    private void updateOverlayPosition() {
        if (cursorView == null) return;
        cursorView.setPointerPosition(cursorX, cursorY);
    }

    private Rect screenBounds() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Maximum metrics describe the whole logical display, not the foreground app's
            // current window. This keeps saved Tap-screen points global across apps and
            // Samsung power-saving / reduced-window layouts.
            Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
            return new Rect(0, 0, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
        }
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return new Rect(0, 0, Math.max(1, size.x), Math.max(1, size.y));
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
        return new float[]{
                b.left + config.touchX(binding) * b.width(),
                b.top + config.touchY(binding) * b.height()
        };
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
                    AccessibilityNodeInfo node = editableFocus();
                    boolean ok = node != null && insertTextIntoNode(node, "\n");
                    if (!ok) {
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
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            typingParams.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 28) {
                typingParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try { windowManager.addView(typingView, typingParams); }
            catch (Throwable t) { typingView = null; }
        });
    }

    public void updateTypingJoystick(float x, float y, long nowMs) {
        handler.post(() -> { if (typingView != null) typingView.updateJoystick(x, y, nowMs); });
    }

    public void captureTypingSelection() {
        handler.post(() -> { if (typingView != null) typingView.captureSelection(); });
    }

    public void clearCapturedTypingSelection() {
        handler.post(() -> { if (typingView != null) typingView.clearCapturedSelection(); });
    }

    public void selectTypingKey() {
        handler.post(() -> { if (typingView != null) typingView.select(); });
    }

    public void typingBackspace() {
        handler.post(() -> {
            if (!deleteFocusedText()) {
                ShizukuBridge bridge = ShizukuBridge.get();
                if (bridge != null && bridge.isReady()) bridge.key(android.view.KeyEvent.KEYCODE_DEL);
            }
        });
    }

    private void removeTypingOverlay() {
        if (typingView != null && windowManager != null) {
            try { windowManager.removeView(typingView); } catch (Throwable ignored) {}
        }
        typingView = null;
    }

    /**
     * Finds the real editable node even when an accessibility overlay temporarily becomes
     * the active window. We first use input focus, then the last EditText/WebView field seen
     * in accessibility events, and finally a conservative single-editable-field fallback.
     */
    private AccessibilityNodeInfo editableFocus() {
        AccessibilityNodeInfo direct = focusedEditable(getRootInActiveWindow());
        if (direct != null) return direct;

        AccessibilityNodeInfo remembered = null;
        AccessibilityNodeInfo onlyEditable = null;
        int editableCount = 0;
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window == null) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                AccessibilityNodeInfo focused = focusedEditable(root);
                if (focused != null) return focused;

                if (lastEditableViewId != null && !lastEditableViewId.isEmpty()) {
                    try {
                        java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(lastEditableViewId);
                        if (matches != null) {
                            for (AccessibilityNodeInfo n : matches) {
                                if (n != null && n.isEditable() && n.isVisibleToUser()) {
                                    if (window.getId() == lastEditableWindowId) return n;
                                    remembered = n;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
                queue.add(root);
                while (!queue.isEmpty() && editableCount < 3) {
                    AccessibilityNodeInfo n = queue.removeFirst();
                    if (n.isEditable() && n.isVisibleToUser()) {
                        Rect b = new Rect();
                        n.getBoundsInScreen(b);
                        if (!lastEditableBounds.isEmpty() && Rect.intersects(b, lastEditableBounds)) remembered = n;
                        editableCount++;
                        onlyEditable = n;
                    }
                    for (int i = 0; i < n.getChildCount(); i++) {
                        AccessibilityNodeInfo child = n.getChild(i);
                        if (child != null) queue.addLast(child);
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (remembered != null) return remembered;
        return editableCount == 1 ? onlyEditable : null;
    }

    private AccessibilityNodeInfo focusedEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        try {
            AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node != null && node.isEditable() && node.isVisibleToUser()) return node;
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean insertFocusedText(String insertion) {
        AccessibilityNodeInfo node = editableFocus();
        if (node != null && insertTextIntoNode(node, insertion)) return true;

        // ACTION_SET_TEXT is not implemented by every custom editor/WebView. Shizuku can
        // inject genuine keyboard events into the field that still owns Android input focus.
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge != null && bridge.isReady() && bridge.text(insertion)) return true;

        Toast.makeText(this, node == null ? R.string.typing_no_field : R.string.typing_cannot_write,
                Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean insertTextIntoNode(AccessibilityNodeInfo node, String insertion) {
        if (node == null) return false;
        try {
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
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean deleteFocusedText() {
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return false;
        try {
            CharSequence currentCs = node.getText();
            String current = currentCs == null ? "" : currentCs.toString();
            int start = node.getTextSelectionStart();
            int end = node.getTextSelectionEnd();
            if (start < 0 || end < 0 || start > current.length() || end > current.length()) {
                start = end = current.length();
            }
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
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Keeps the newer repeated 95 ms swipe feel used by the current scroll mode. */
    public void scrollByJoystick(float x, float y) {
        handler.post(() -> {
            if (scrollGestureInFlight || windowManager == null || pickerView != null) return;
            float ax = Math.abs(x), ay = Math.abs(y);
            if (Math.max(ax, ay) < 0.38f) return;
            Rect b = screenBounds();
            float cx = b.left + b.width() * 0.50f;
            float cy = b.top + b.height() * 0.54f;
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
            scrollGestureInFlight = true;
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) { scrollGestureInFlight = false; }
                @Override public void onCancelled(GestureDescription gestureDescription) { scrollGestureInFlight = false; }
            }, handler);
            if (!accepted) scrollGestureInFlight = false;
        });
    }

    public void beginTouchPick(ControlConfig.Binding binding) {
        handler.post(() -> {
            if (windowManager == null) return;
            removePicker();
            String bindingLabel = UiLabels.binding(this, binding);
            pickerView = new TouchPickerOverlayView(this, bindingLabel, (rawX, rawY) -> {
                Rect b = screenBounds();
                float nx = clamp((rawX - b.left) / Math.max(1f, b.width()), 0f, 1f);
                float ny = clamp((rawY - b.top) / Math.max(1f, b.height()), 0f, 1f);
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
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            pickerParams.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 28) {
                pickerParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !PokeballService.isConnected()) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || !source.isEditable()) return;
        try {
            lastEditableViewId = source.getViewIdResourceName();
            lastEditableWindowId = source.getWindowId();
            source.getBoundsInScreen(lastEditableBounds);
        } catch (Throwable ignored) {}
    }

    @Override public void onInterrupt() {}
}
