package pl.openai.pokeballmouse;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class CursorAccessibilityService extends AccessibilityService {
    private static volatile CursorAccessibilityService instance;

    private WindowManager windowManager;
    private CursorOverlayView cursorView;
    private WindowManager.LayoutParams params;
    private TouchPickerOverlayView pickerView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ControlConfig config;

    private float cursorX;
    private float cursorY;
    private float maxX;
    private float maxY;
    private long lastFrameNanos;
    private boolean cursorVisible = false;

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
        lastFrameNanos = System.nanoTime();
        handler.post(frame);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removePicker();
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

        int cursorWidth = Math.round(32f * getResources().getDisplayMetrics().density);
        int cursorHeight = Math.round(42f * getResources().getDisplayMetrics().density);
        cursorView = new CursorOverlayView(this);
        // Accessibility may stay enabled all the time; the mouse cursor must not.
        cursorView.setVisibility(View.GONE);
        cursorVisible = false;
        params = new WindowManager.LayoutParams(
                cursorWidth,
                cursorHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.round(cursorX - cursorView.hotspotXpx());
        params.y = Math.round(cursorY - cursorView.hotspotYpx());
        windowManager.addView(cursorView, params);
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
            lastFrameNanos = now;

            boolean mouseMode = InputRouter.mode() == ControlConfig.Mode.MOUSE;
            boolean connected = PokeballService.isConnected();
            boolean cursorShouldBeVisible = connected && mouseMode && pickerView == null;
            setCursorVisible(cursorShouldBeVisible);

            if (connected && mouseMode) {
                float x = filtered(InputRouter.joyX());
                float y = filtered(InputRouter.joyY());
                if (x != 0f || y != 0f) {
                    float speed = MAX_SPEED_DP_PER_SEC * getResources().getDisplayMetrics().density;
                    float magnitude = Math.min(1f, (float) Math.sqrt(x * x + y * y));
                    float accel = 0.22f + 0.78f * magnitude * magnitude;
                    cursorX += x * speed * accel * dt;
                    cursorY -= y * speed * accel * dt;
                    cursorX = clamp(cursorX, 0f, maxX);
                    cursorY = clamp(cursorY, 0f, maxY);
                    updateOverlayPosition();
                }

                ShizukuBridge bridge = ShizukuBridge.get();
                if (bridge != null && bridge.isReady()) bridge.move(cursorX, cursorY);
            }
            handler.postDelayed(this, 16L);
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

    /** Returns the configured binding point in current absolute screen pixels. */
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
