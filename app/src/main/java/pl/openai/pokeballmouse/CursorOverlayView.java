package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Visual-only mouse pointer inspired by the Android 14 system cursor.
 *
 * The view itself is always installed as a non-touchable accessibility overlay;
 * all touch input must pass through to the app underneath.
 */
public final class CursorOverlayView extends View {
    // The interaction coordinate (cursorX/cursorY) maps to the arrow tip.
    private static final float HOTSPOT_X_DP = 2.4f;
    private static final float HOTSPOT_Y_DP = 2.2f;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();

    public CursorOverlayView(Context context) {
        super(context);

        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Software layer is intentional: it gives a small, soft cursor shadow.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        shadowPaint.setColor(Color.TRANSPARENT);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setShadowLayer(dp(2.2f), dp(1.0f), dp(1.4f), Color.argb(95, 0, 0, 0));

        outlinePaint.setColor(Color.argb(175, 245, 245, 245));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(dp(0.85f));

        fillPaint.setColor(Color.rgb(10, 10, 10));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public float hotspotXpx() { return dp(HOTSPOT_X_DP); }
    public float hotspotYpx() { return dp(HOTSPOT_Y_DP); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        // Normalized Android-style pointer shape. The arrow tip is deliberately
        // near the top-left so the visible tip is also the click hotspot.
        float left = dp(1.6f);
        float top = dp(1.4f);
        float right = Math.min(w - dp(1.5f), dp(27.5f));
        float bottom = Math.min(h - dp(1.5f), dp(36.5f));

        float sx = (right - left) / 26f;
        float sy = (bottom - top) / 35f;

        pointerPath.reset();
        pointerPath.moveTo(left + 0.9f * sx, top + 0.7f * sy);          // tip
        pointerPath.lineTo(left + 24.7f * sx, top + 20.0f * sy);
        pointerPath.quadTo(left + 25.7f * sx, top + 21.0f * sy,
                left + 24.0f * sx, top + 22.0f * sy);
        pointerPath.lineTo(left + 16.7f * sx, top + 24.0f * sy);
        pointerPath.lineTo(left + 21.4f * sx, top + 32.7f * sy);
        pointerPath.quadTo(left + 22.1f * sx, top + 34.1f * sy,
                left + 20.6f * sx, top + 34.8f * sy);
        pointerPath.lineTo(left + 16.9f * sx, top + 35.0f * sy);
        pointerPath.quadTo(left + 15.8f * sx, top + 34.9f * sy,
                left + 15.2f * sx, top + 33.8f * sy);
        pointerPath.lineTo(left + 10.8f * sx, top + 25.6f * sy);
        pointerPath.lineTo(left + 5.1f * sx, top + 31.2f * sy);
        pointerPath.quadTo(left + 3.9f * sx, top + 32.3f * sy,
                left + 3.5f * sx, top + 30.5f * sy);
        pointerPath.close();

        canvas.drawPath(pointerPath, shadowPaint);
        canvas.drawPath(pointerPath, fillPaint);
        canvas.drawPath(pointerPath, outlinePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
