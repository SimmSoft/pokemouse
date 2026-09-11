package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compact live Poké Ball Plus preview for button diagnostics. */
public final class PokeballDiagnosticView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean topPressed;
    private boolean stickPressed;
    private boolean dark;

    public PokeballDiagnosticView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2.2f));
    }

    public void setDark(boolean value) {
        dark = value;
        invalidate();
    }

    public void setPressed(boolean top, boolean stick) {
        if (topPressed == top && stickPressed == stick) return;
        topPressed = top;
        stickPressed = stick;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) * 0.76f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f + dp(5);
        RectF ball = new RectF(left, top, left + size, top + size);
        float cx = ball.centerX();
        float cy = ball.centerY();

        canvas.save();
        canvas.clipPath(circlePath(cx, cy, size / 2f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(226, 49, 60));
        canvas.drawRect(ball.left, ball.top, ball.right, cy, paint);
        paint.setColor(Color.rgb(245, 246, 248));
        canvas.drawRect(ball.left, cy, ball.right, ball.bottom, paint);
        paint.setColor(Color.rgb(28, 31, 36));
        canvas.drawRect(ball.left, cy - dp(5), ball.right, cy + dp(5), paint);
        canvas.restore();

        stroke.setColor(dark ? Color.rgb(225, 228, 233) : Color.rgb(53, 58, 65));
        canvas.drawCircle(cx, cy, size / 2f, stroke);

        // Front joystick / center button.
        float ringR = size * 0.17f;
        paint.setColor(Color.rgb(22, 24, 28));
        canvas.drawCircle(cx, cy, ringR, paint);
        paint.setColor(stickPressed ? Color.rgb(69, 214, 113) : Color.rgb(215, 219, 225));
        canvas.drawCircle(cx, cy, ringR * 0.58f, paint);
        stroke.setColor(stickPressed ? Color.rgb(114, 242, 150) : Color.rgb(112, 118, 128));
        canvas.drawCircle(cx, cy, ringR * 0.58f, stroke);

        // The physical Top button is represented above/right of the shell.
        float bw = size * 0.25f;
        float bh = size * 0.11f;
        RectF topButton = new RectF(ball.right - bw * 1.75f, ball.top - bh * 0.18f,
                ball.right - bw * 0.75f, ball.top + bh * 0.82f);
        paint.setColor(topPressed ? Color.rgb(69, 214, 113) : (dark ? Color.rgb(72, 78, 88) : Color.rgb(192, 197, 205)));
        canvas.drawRoundRect(topButton, bh / 2f, bh / 2f, paint);
        stroke.setColor(topPressed ? Color.rgb(114, 242, 150) : (dark ? Color.rgb(155, 161, 171) : Color.rgb(104, 111, 121)));
        canvas.drawRoundRect(topButton, bh / 2f, bh / 2f, stroke);
    }

    private android.graphics.Path circlePath(float cx, float cy, float r) {
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
        return path;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
