package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small visual-only pointer modelled after the compact Android 14 arrow cursor. */
public final class CursorOverlayView extends View {
    private static final float HOTSPOT_X_DP = 1.5f;
    private static final float HOTSPOT_Y_DP = 1.4f;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();

    public CursorOverlayView(Context context) {
        super(context);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        shadowPaint.setColor(Color.argb(60, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(Color.rgb(7, 7, 8));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);

        edgePaint.setColor(Color.rgb(48, 48, 50));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(0.55f));
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public float hotspotXpx() { return dp(HOTSPOT_X_DP); }
    public float hotspotYpx() { return dp(HOTSPOT_Y_DP); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        float left = dp(1.0f);
        float top = dp(0.9f);
        float sx = Math.max(0.1f, (Math.min(w - dp(1f), dp(17f)) - left) / 15f);
        float sy = Math.max(0.1f, (Math.min(h - dp(1f), dp(23f)) - top) / 21f);

        // Compact Android-style arrow: small tip, straight left edge and short stem.
        pointerPath.reset();
        pointerPath.moveTo(left + 0.5f * sx, top + 0.5f * sy);
        pointerPath.lineTo(left + 0.7f * sx, top + 16.0f * sy);
        pointerPath.quadTo(left + 0.8f * sx, top + 17.0f * sy,
                left + 1.8f * sx, top + 16.2f * sy);
        pointerPath.lineTo(left + 5.5f * sx, top + 12.8f * sy);
        pointerPath.lineTo(left + 9.2f * sx, top + 20.3f * sy);
        pointerPath.quadTo(left + 9.7f * sx, top + 21.3f * sy,
                left + 10.7f * sx, top + 20.8f * sy);
        pointerPath.lineTo(left + 13.0f * sx, top + 19.6f * sy);
        pointerPath.quadTo(left + 14.0f * sx, top + 19.1f * sy,
                left + 13.4f * sx, top + 18.1f * sy);
        pointerPath.lineTo(left + 9.8f * sx, top + 10.9f * sy);
        pointerPath.lineTo(left + 14.2f * sx, top + 10.8f * sy);
        pointerPath.quadTo(left + 15.5f * sx, top + 10.8f * sy,
                left + 14.6f * sx, top + 9.9f * sy);
        pointerPath.close();

        canvas.save();
        canvas.translate(dp(0.75f), dp(1.1f));
        canvas.drawPath(pointerPath, shadowPaint);
        canvas.restore();
        canvas.drawPath(pointerPath, fillPaint);
        canvas.drawPath(pointerPath, edgePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
