package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Full-screen, non-interactive visual cursor layer.
 *
 * The pointer is intentionally a small rounded Android-style wedge rather than the
 * sharp desktop arrow or a plain rotated triangle. The visible tip is the click hotspot.
 */
public final class CursorOverlayView extends View {
    private static final float HOTSPOT_X_DP = 1.25f;
    private static final float HOTSPOT_Y_DP = 1.25f;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();

    private float pointerX;
    private float pointerY;

    public CursorOverlayView(Context context) {
        super(context);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);

        shadowPaint.setColor(Color.argb(45, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(Color.rgb(5, 5, 6));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);

        // User-requested subtle light rim: visible on dark surfaces, still understated on white.
        outlinePaint.setColor(Color.argb(215, 255, 255, 255));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(0.78f));
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);

        buildPointerPath();
    }

    public float hotspotXpx() { return dp(HOTSPOT_X_DP); }
    public float hotspotYpx() { return dp(HOTSPOT_Y_DP); }

    public void setPointerPosition(float x, float y) {
        if (Math.abs(pointerX - x) < 0.12f && Math.abs(pointerY - y) < 0.12f) return;
        pointerX = x;
        pointerY = y;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(pointerX - hotspotXpx(), pointerY - hotspotYpx());

        // Tiny hard shadow instead of a blurred software shadow, so cursor movement stays cheap.
        canvas.save();
        canvas.translate(dp(0.48f), dp(0.62f));
        canvas.drawPath(pointerPath, shadowPaint);
        canvas.restore();

        canvas.drawPath(pointerPath, fillPaint);
        canvas.drawPath(pointerPath, outlinePaint);
        canvas.restore();
    }

    private void buildPointerPath() {
        // Logical size ~17 x 20 dp. The concave lower edge and rounded lower lobe match
        // the softer Android 14 pointer reference more closely than a simple triangle.
        pointerPath.reset();
        pointerPath.moveTo(dp(1.70f), dp(1.60f));
        pointerPath.cubicTo(dp(1.05f), dp(1.15f),
                dp(0.65f), dp(1.85f),
                dp(0.78f), dp(2.95f));
        pointerPath.lineTo(dp(2.20f), dp(16.60f));
        pointerPath.cubicTo(dp(2.35f), dp(18.45f),
                dp(4.45f), dp(19.15f),
                dp(5.65f), dp(17.50f));
        pointerPath.lineTo(dp(8.55f), dp(13.25f));
        pointerPath.cubicTo(dp(9.15f), dp(12.40f),
                dp(9.90f), dp(12.05f),
                dp(10.95f), dp(12.20f));
        pointerPath.lineTo(dp(14.50f), dp(12.65f));
        pointerPath.cubicTo(dp(16.05f), dp(12.85f),
                dp(16.95f), dp(11.05f),
                dp(15.65f), dp(10.05f));
        pointerPath.lineTo(dp(3.45f), dp(1.80f));
        pointerPath.cubicTo(dp(2.85f), dp(1.35f),
                dp(2.20f), dp(1.25f),
                dp(1.70f), dp(1.60f));
        pointerPath.close();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
