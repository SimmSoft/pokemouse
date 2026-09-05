package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small centered circular live joystick visualizer for Diagnostics.
 * Reads the volatile InputRouter values directly every display frame so it is not limited
 * by the slower status polling loop used by the rest of the screen.
 */
public final class JoystickDiagnosticView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dark = true;
    private boolean running;

    public JoystickDiagnosticView(Context context) { super(context); }
    public JoystickDiagnosticView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setDark(boolean value) {
        dark = value;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float x = clamp(InputRouter.joyX());
        float y = clamp(InputRouter.joyY());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.39f;

        int line = dark ? Color.rgb(93, 99, 110) : Color.rgb(170, 176, 185);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(line);
        canvas.drawCircle(cx, cy, r, paint);
        canvas.drawLine(cx - r, cy, cx + r, cy, paint);
        canvas.drawLine(cx, cy - r, cx, cy + r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 66, 78));
        float dotX = cx + x * r * 0.78f;
        float dotY = cy - y * r * 0.78f;
        canvas.drawCircle(dotX, dotY, dp(5.5f), paint);

        // Keep this one tiny view synced to the display refresh rate. It is intentionally
        // independent from MainActivity's slower 500 ms status refresh.
        if (running && isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
