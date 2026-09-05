package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Compact Poké Ball connection animation used inside the main connection card. */
public final class ConnectionOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PokeballService.Phase phase = PokeballService.Phase.DISCONNECTED;
    private long startMs = SystemClock.uptimeMillis();

    public ConnectionOrbView(Context context) { super(context); init(); }
    public ConnectionOrbView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(104)));
        setContentDescription("Poké Ball Plus connection status");
    }

    public void setPhase(PokeballService.Phase value) {
        if (value == null) value = PokeballService.Phase.DISCONNECTED;
        if (phase != value) {
            phase = value;
            startMs = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float min = Math.min(getWidth(), getHeight());
        float ballR = Math.min(dp(31), min * 0.26f);
        float ringR = ballR + dp(13);
        long elapsed = SystemClock.uptimeMillis() - startMs;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(4));
        paint.setStrokeCap(Paint.Cap.ROUND);
        int neutral = Color.rgb(145, 151, 160);
        int ringColor = neutral;
        if (phase == PokeballService.Phase.CONNECTED) ringColor = Color.rgb(65, 214, 108);
        else if (phase == PokeballService.Phase.ERROR) ringColor = Color.rgb(255, 73, 84);
        else if (phase == PokeballService.Phase.SEARCHING || phase == PokeballService.Phase.CONNECTING)
            ringColor = Color.rgb(244, 246, 249);
        paint.setColor(ringColor);
        RectF ring = new RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR);

        if (phase == PokeballService.Phase.SEARCHING) {
            // Seamless loop: all animated angles make complete 360-degree revolutions.
            // No counter-rotating partial multiplier means there is no visible snap at loop end.
            float a = loopAngle(elapsed, 1600L);
            float b = loopAngle(elapsed + 800L, 1600L);
            paint.setAlpha(45);
            canvas.drawCircle(cx, cy, ringR + dp(8), paint);
            paint.setAlpha(255);
            canvas.drawArc(ring, a, 74f, false, paint);
            canvas.drawArc(ring, b, 74f, false, paint);
            RectF inner = new RectF(cx - ringR + dp(7), cy - ringR + dp(7),
                    cx + ringR - dp(7), cy + ringR - dp(7));
            paint.setAlpha(115);
            canvas.drawArc(inner, loopAngle(elapsed, 2300L), 92f, false, paint);
            paint.setAlpha(255);
            postInvalidateOnAnimation();
        } else if (phase == PokeballService.Phase.CONNECTING) {
            float a = loopAngle(elapsed, 1250L);
            paint.setAlpha(55);
            canvas.drawCircle(cx, cy, ringR, paint);
            paint.setAlpha(255);
            canvas.drawArc(ring, a, 235f, false, paint);
            postInvalidateOnAnimation();
        } else {
            canvas.drawCircle(cx, cy, ringR, paint);
        }

        drawPokeball(canvas, cx, cy, ballR);

        if (phase == PokeballService.Phase.CONNECTED) {
            float badgeR = dp(10);
            float bx = cx + ringR * 0.72f;
            float by = cy + ringR * 0.72f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(65, 214, 108));
            canvas.drawCircle(bx, by, badgeR, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(16, 35, 21));
            canvas.drawLine(bx - dp(4), by, bx - dp(1), by + dp(3), paint);
            canvas.drawLine(bx - dp(1), by + dp(3), bx + dp(5), by - dp(4), paint);
        }
    }

    private static float loopAngle(long elapsed, long periodMs) {
        long m = Math.floorMod(elapsed, periodMs);
        return (m / (float) periodMs) * 360f;
    }

    private void drawPokeball(Canvas canvas, float cx, float cy, float r) {
        RectF oval = new RectF(cx-r, cy-r, cx+r, cy+r);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(247, 247, 247));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(Color.rgb(235, 61, 70));
        canvas.drawArc(oval, 180f, 180f, true, paint);
        paint.setColor(Color.rgb(31, 33, 36));
        canvas.drawRect(cx-r, cy-dp(3.2f), cx+r, cy+dp(3.2f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(25, 27, 30));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(31, 33, 36));
        canvas.drawCircle(cx, cy, r * 0.28f, paint);
        paint.setColor(Color.rgb(247, 247, 247));
        canvas.drawCircle(cx, cy, r * 0.17f, paint);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
