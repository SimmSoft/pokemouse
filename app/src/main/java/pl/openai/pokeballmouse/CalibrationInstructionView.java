package pl.openai.pokeballmouse;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Small looped visual instruction used by the calibration wizard. */
public final class CalibrationInstructionView extends View {
    public enum Type {
        TABLE,
        JOY_UP, JOY_DOWN, JOY_LEFT, JOY_RIGHT,
        MOTION_UP, MOTION_DOWN, MOTION_LEFT, MOTION_RIGHT
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private ValueAnimator animator;
    private float progress;
    private Type type = Type.TABLE;
    private final boolean dark;
    private final int foreground;
    private final int muted;
    private final int accent;
    private final int surface;

    public CalibrationInstructionView(Context context) {
        super(context);
        dark = ThemePrefs.isDark(context);
        foreground = dark ? Color.rgb(242,244,247) : Color.rgb(31,35,41);
        muted = dark ? Color.rgb(174,180,190) : Color.rgb(98,105,116);
        accent = dark ? Color.rgb(255,68,80) : Color.rgb(220,47,61);
        surface = dark ? Color.rgb(31,36,43) : Color.rgb(249,250,252);
        setMinimumHeight(dp(126));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        startAnimation();
    }

    public void setType(Type type) {
        this.type = type == null ? Type.TABLE : type;
        startAnimation();
        invalidate();
    }

    private void startAnimation() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(type.name().startsWith("MOTION_") ? 4000L : 1800L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        final float cx = w * 0.5f;
        final float cy = h * 0.55f;
        final float r = Math.min(w, h) * 0.22f;

        if (type == Type.TABLE) {
            drawTable(canvas, cx, cy, r);
            return;
        }

        boolean motion = type.name().startsWith("MOTION_");
        float dx = 0f, dy = 0f;
        if (motion) {
            // For the first ~40% of the loop the ball stays still while Top pulses.
            // Then the ball moves once in the requested direction and returns.
            float move = Math.max(0f, (progress - 0.75f) / 0.25f);
            float pulse = move <= 0f ? 0f : (float)Math.sin(Math.min(1f, move) * Math.PI);
            float amount = dp(17) * pulse;
            switch (type) {
                case MOTION_UP: dy = -amount; break;
                case MOTION_DOWN: dy = amount; break;
                case MOTION_LEFT: dx = -amount; break;
                case MOTION_RIGHT: dx = amount; break;
                default: break;
            }
        }

        canvas.save();
        canvas.translate(dx, dy);
        drawPokeball(canvas, cx, cy, r);
        if (motion) drawTopButtonPulse(canvas, cx, cy, r);
        else drawJoystick(canvas, cx, cy, r);
        canvas.restore();

        drawDirectionArrow(canvas, cx, cy, r, type, motion);
    }

    private void drawTable(Canvas canvas, float cx, float cy, float r) {
        float breathe = 1f + 0.025f * (float)Math.sin(progress * Math.PI * 2f);
        canvas.save();
        canvas.scale(breathe, breathe, cx, cy);
        drawPokeball(canvas, cx, cy - dp(4), r);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(muted);
        float y = cy + r + dp(13);
        canvas.drawLine(cx - r * 1.7f, y, cx + r * 1.7f, y, paint);
        canvas.drawLine(cx - r * 1.25f, y, cx - r * 1.45f, y + dp(18), paint);
        canvas.drawLine(cx + r * 1.25f, y, cx + r * 1.45f, y + dp(18), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(muted);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(11));
        canvas.drawText("6 s", cx, y + dp(22), paint);
    }

    private void drawPokeball(Canvas canvas, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(surface);
        canvas.drawCircle(cx, cy, r + dp(4), paint);

        RectF top = new RectF(cx-r, cy-r, cx+r, cy);
        paint.setColor(Color.rgb(240, 56, 67));
        canvas.drawArc(top, 180f, 180f, true, paint);

        RectF bottom = new RectF(cx-r, cy, cx+r, cy+r);
        paint.setColor(Color.rgb(244, 245, 247));
        canvas.drawArc(bottom, 0f, 180f, true, paint);

        paint.setColor(Color.rgb(36, 39, 43));
        canvas.drawRect(cx-r, cy-dp(3), cx+r, cy+dp(3), paint);
        canvas.drawCircle(cx, cy, r*0.28f, paint);
        paint.setColor(Color.rgb(235,237,240));
        canvas.drawCircle(cx, cy, r*0.16f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(muted);
        canvas.drawCircle(cx, cy, r + dp(4), paint);
    }

    private void drawJoystick(Canvas canvas, float cx, float cy, float r) {
        float t = (float)Math.sin(progress * Math.PI * 2f);
        float d = dp(7) * Math.max(0f, t);
        float x = cx, y = cy;
        switch (type) {
            case JOY_UP: y -= d; break;
            case JOY_DOWN: y += d; break;
            case JOY_LEFT: x -= d; break;
            case JOY_RIGHT: x += d; break;
            default: break;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(42,45,50));
        canvas.drawCircle(x, y, r*0.15f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, r*0.15f, paint);
    }

    private void drawTopButtonPulse(Canvas canvas, float cx, float cy, float r) {
        float pulse = 0.5f + 0.5f * (float)Math.sin(Math.min(progress / 0.75f, 1f) * Math.PI * 4f);
        float x = cx;
        float y = cy - r - dp(5);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        canvas.drawCircle(x, y, dp(5), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb((int)(80 + 120*pulse), Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(x, y, dp(8) + dp(3)*pulse, paint);
    }

    private void drawDirectionArrow(Canvas canvas, float cx, float cy, float r, Type type, boolean motion) {
        float alpha = 1f;
        if (motion && progress < 0.73f) alpha = 0.25f;
        float bounce = dp(4) * (float)Math.sin(progress * Math.PI * 2f);
        float x = cx, y = cy;
        int direction = 0; // 0 up, 1 down, 2 left, 3 right
        switch (type) {
            case JOY_UP: case MOTION_UP: y = cy-r-dp(23)-bounce; direction=0; break;
            case JOY_DOWN: case MOTION_DOWN: y = cy+r+dp(23)+bounce; direction=1; break;
            case JOY_LEFT: case MOTION_LEFT: x = cx-r-dp(27)-bounce; direction=2; break;
            case JOY_RIGHT: case MOTION_RIGHT: x = cx+r+dp(27)+bounce; direction=3; break;
            default: return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int)(255*alpha), Color.red(foreground), Color.green(foreground), Color.blue(foreground)));
        path.reset();
        float a=dp(8), b=dp(10);
        if(direction==0){path.moveTo(x,y-b);path.lineTo(x-a,y+b);path.lineTo(x+a,y+b);}
        else if(direction==1){path.moveTo(x,y+b);path.lineTo(x-a,y-b);path.lineTo(x+a,y-b);}
        else if(direction==2){path.moveTo(x-b,y);path.lineTo(x+b,y-a);path.lineTo(x+b,y+a);}
        else {path.moveTo(x+b,y);path.lineTo(x-b,y-a);path.lineTo(x-b,y+a);}
        path.close();
        canvas.drawPath(path, paint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
