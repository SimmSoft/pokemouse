package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Small centered circular live joystick visualizer for Diagnostics. */
public final class JoystickDiagnosticView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x;
    private float y;
    private boolean dark = true;

    public JoystickDiagnosticView(Context context) { super(context); }
    public JoystickDiagnosticView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setDark(boolean value) { dark = value; invalidate(); }
    public void setPosition(float nx, float ny) {
        x = Math.max(-1f, Math.min(1f, nx));
        y = Math.max(-1f, Math.min(1f, ny));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth()/2f, cy = getHeight()/2f;
        float r = Math.min(getWidth(), getHeight()) * 0.39f;
        int line = dark ? Color.rgb(93, 99, 110) : Color.rgb(170, 176, 185);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(line);
        canvas.drawCircle(cx, cy, r, paint);
        canvas.drawLine(cx-r, cy, cx+r, cy, paint);
        canvas.drawLine(cx, cy-r, cx, cy+r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 66, 78));
        float dotX = cx + x * r * 0.78f;
        float dotY = cy - y * r * 0.78f;
        canvas.drawCircle(dotX, dotY, dp(6), paint);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
