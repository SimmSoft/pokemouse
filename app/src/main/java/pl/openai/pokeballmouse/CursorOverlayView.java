package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Compact rounded Android-style pointer. Deliberately no desktop-style stem/tail:
 * the silhouette is the soft triangular pointer shown by recent Android versions.
 */
public final class CursorOverlayView extends View {
    private static final float HOTSPOT_X_DP = 1.2f;
    private static final float HOTSPOT_Y_DP = 1.2f;

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

        shadowPaint.setColor(Color.argb(45, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(Color.rgb(8, 8, 9));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);

        edgePaint.setColor(Color.rgb(55, 55, 58));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(0.45f));
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public float hotspotXpx() { return dp(HOTSPOT_X_DP); }
    public float hotspotYpx() { return dp(HOTSPOT_Y_DP); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float sx = getWidth() / dp(15f);
        float sy = getHeight() / dp(18f);

        // Rounded triangle inspired by the Android 14 pointer reference supplied by the user.
        // Tip is upper-left, with a soft vertical rear edge and no protruding stem.
        pointerPath.reset();
        pointerPath.moveTo(dp(1.6f) * sx, dp(1.5f) * sy);
        pointerPath.cubicTo(dp(1.0f) * sx, dp(1.1f) * sy,
                dp(0.65f) * sx, dp(1.8f) * sy,
                dp(0.72f) * sx, dp(2.7f) * sy);
        pointerPath.lineTo(dp(1.15f) * sx, dp(15.2f) * sy);
        pointerPath.cubicTo(dp(1.2f) * sx, dp(16.7f) * sy,
                dp(2.95f) * sx, dp(17.35f) * sy,
                dp(4.05f) * sx, dp(16.35f) * sy);
        pointerPath.lineTo(dp(13.65f) * sx, dp(10.55f) * sy);
        pointerPath.cubicTo(dp(14.8f) * sx, dp(9.85f) * sy,
                dp(14.7f) * sx, dp(8.65f) * sy,
                dp(13.55f) * sx, dp(7.95f) * sy);
        pointerPath.lineTo(dp(3.25f) * sx, dp(1.55f) * sy);
        pointerPath.cubicTo(dp(2.65f) * sx, dp(1.2f) * sy,
                dp(2.05f) * sx, dp(1.15f) * sy,
                dp(1.6f) * sx, dp(1.5f) * sy);
        pointerPath.close();

        canvas.save();
        canvas.translate(dp(0.55f), dp(0.7f));
        canvas.drawPath(pointerPath, shadowPaint);
        canvas.restore();
        canvas.drawPath(pointerPath, fillPaint);
        canvas.drawPath(pointerPath, edgePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
