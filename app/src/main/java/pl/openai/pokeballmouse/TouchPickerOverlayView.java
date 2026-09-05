package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** One-shot transparent full-screen overlay used to select a Tap screen coordinate. */
public final class TouchPickerOverlayView extends View {
    public interface Listener { void onPoint(float rawX, float rawY); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;
    private final Listener listener;
    private boolean completed;

    public TouchPickerOverlayView(Context context, String label, Listener listener) {
        super(context);
        this.label = label;
        this.listener = listener;
        setBackgroundColor(Color.argb(18, 0, 0, 0));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        paint.setColor(Color.argb(226, 20, 22, 27));
        canvas.drawRoundRect(16 * density, 18 * density,
                getWidth() - 16 * density, 92 * density,
                16 * density, 16 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(17 * density);
        canvas.drawText(getContext().getString(R.string.tap_overlay_title, label), 30 * density, 50 * density, paint);
        paint.setTextSize(13 * density);
        canvas.drawText(getContext().getString(R.string.tap_overlay_instruction),
                30 * density, 76 * density, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!completed && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            completed = true;
            listener.onPoint(event.getRawX(), event.getRawY());
        }
        return true;
    }
}
