package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compact battery icon whose fill amount and color reflect the reported percentage. */
public final class BatteryLevelView extends View {
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int level = -1;
    private int outlineColor = Color.GRAY;
    private int successColor = Color.rgb(70, 218, 112);
    private int warningColor = Color.rgb(244, 190, 83);
    private int dangerColor = Color.rgb(255, 76, 87);
    private final float density;

    public BatteryLevelView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
    }

    public void setColors(int outline, int success, int warning, int danger) {
        outlineColor = outline;
        successColor = success;
        warningColor = warning;
        dangerColor = danger;
        invalidate();
    }

    public void setLevel(int value) {
        int next = value < 0 ? -1 : Math.max(0, Math.min(100, value));
        if (level == next) return;
        level = next;
        invalidate();
    }

    public int level() { return level; }

    public int currentFillColor() {
        if (level < 0) return outlineColor;
        if (level <= 20) return dangerColor;
        if (level <= 50) return warningColor;
        return successColor;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float stroke = Math.max(1f, 1.5f * density);
        float terminalW = Math.max(2f * density, w * 0.10f);
        float bodyRight = w - terminalW - stroke;
        RectF body = new RectF(stroke, stroke * 1.4f, bodyRight, h - stroke * 1.4f);
        float radius = Math.min(body.height(), body.width()) * 0.18f;

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(stroke);
        outlinePaint.setColor(outlineColor);
        canvas.drawRoundRect(body, radius, radius, outlinePaint);

        outlinePaint.setStyle(Paint.Style.FILL);
        RectF terminal = new RectF(bodyRight + stroke * 0.8f, h * 0.34f, w - stroke * 0.2f, h * 0.66f);
        canvas.drawRoundRect(terminal, stroke, stroke, outlinePaint);

        if (level <= 0) return;
        float inset = stroke * 1.8f;
        RectF inner = new RectF(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset);
        if (inner.width() <= 0 || inner.height() <= 0) return;

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(currentFillColor());
        float fillRight = inner.left + inner.width() * (level / 100f);
        canvas.save();
        canvas.clipRect(inner.left, inner.top, fillRight, inner.bottom);
        canvas.drawRoundRect(inner, Math.max(1f, radius - inset * 0.5f),
                Math.max(1f, radius - inset * 0.5f), fillPaint);
        canvas.restore();
    }
}
