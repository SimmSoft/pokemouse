package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class CursorOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CursorOverlayView(Context context) {
        super(context);
        fill.setColor(Color.argb(220, 20, 20, 20));
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(dp(2f));
        stroke.setStyle(Paint.Style.STROKE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - dp(2f);
        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawCircle(cx, cy, r, stroke);
        canvas.drawLine(cx - r * .55f, cy, cx + r * .55f, cy, stroke);
        canvas.drawLine(cx, cy - r * .55f, cx, cy + r * .55f, stroke);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
