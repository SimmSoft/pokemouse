package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Read-only visual typing overlay controlled entirely by the Poké Ball Plus.
 * It never consumes touch, so the user's finger can still interact with the app underneath.
 */
public final class TypingOverlayView extends View {
    public interface Listener {
        void onText(String text);
        void onBackspace();
        void onEnter();
    }

    private static final String[] RADIAL_GROUPS = {
            "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "↵"
    };
    private static final String[][] GRID = {
            {"Q","W","E","R","T","Y","U","I","O","P"},
            {"A","S","D","F","G","H","J","K","L"},
            {"Z","X","C","V","B","N","M"},
            {"Ą","Ć","Ę","Ł","Ń","Ó","Ś","Ź","Ż"},
            {"⌫","SPACE","↵"}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Listener listener;

    private ControlConfig.TypingMode mode;
    private float joyX;
    private float joyY;
    private int radialGroup = -1;
    private int gridRow = 0;
    private int gridCol = 0;
    private int lastGridDirection;
    private long nextGridRepeatMs;

    public TypingOverlayView(Context context, ControlConfig.TypingMode mode, Listener listener) {
        super(context);
        this.mode = mode;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setColor(Color.argb(228, 23, 27, 33));
        selectedPaint.setColor(Color.rgb(255, 68, 80));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mutedTextPaint.setColor(Color.rgb(190, 195, 204));
        mutedTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setMode(ControlConfig.TypingMode mode) {
        this.mode = mode;
        radialGroup = -1;
        gridRow = gridCol = 0;
        invalidate();
    }

    public void updateJoystick(float x, float y, long nowMs) {
        joyX = x;
        joyY = y;
        if (mode == ControlConfig.TypingMode.KEYBOARD) updateGridSelection(nowMs);
        invalidate();
    }

    public void select() {
        if (mode == ControlConfig.TypingMode.RADIAL) selectRadial();
        else selectGrid();
    }

    public void backspace() { if (listener != null) listener.onBackspace(); }

    private void selectRadial() {
        float magnitude = (float) Math.sqrt(joyX * joyX + joyY * joyY);
        if (radialGroup < 0) {
            if (magnitude < 0.24f) {
                if (listener != null) listener.onText(" ");
                return;
            }
            int group = radialSlot(joyX, joyY, RADIAL_GROUPS.length);
            if (group == RADIAL_GROUPS.length - 1) {
                if (listener != null) listener.onEnter();
                return;
            }
            radialGroup = group;
            invalidate();
            return;
        }

        String letters = RADIAL_GROUPS[radialGroup];
        if (magnitude < 0.20f) {
            radialGroup = -1;
            invalidate();
            return;
        }
        int index = radialSlot(joyX, joyY, letters.length());
        if (index >= 0 && index < letters.length() && listener != null) {
            listener.onText(String.valueOf(Character.toLowerCase(letters.charAt(index))));
        }
        radialGroup = -1;
        invalidate();
    }

    private void updateGridSelection(long nowMs) {
        int direction = cardinalDirection(joyX, joyY, 0.46f);
        if (direction == 0) {
            lastGridDirection = 0;
            nextGridRepeatMs = 0L;
            return;
        }
        if (direction != lastGridDirection) {
            moveGrid(direction);
            lastGridDirection = direction;
            nextGridRepeatMs = nowMs + 430L;
            return;
        }
        if (nowMs >= nextGridRepeatMs) {
            moveGrid(direction);
            nextGridRepeatMs = nowMs + 125L;
        }
    }

    private void moveGrid(int direction) {
        if (direction == 1) gridRow--;
        else if (direction == 2) gridRow++;
        else if (direction == 3) gridCol--;
        else if (direction == 4) gridCol++;
        if (gridRow < 0) gridRow = GRID.length - 1;
        if (gridRow >= GRID.length) gridRow = 0;
        int cols = GRID[gridRow].length;
        if (gridCol < 0) gridCol = cols - 1;
        if (gridCol >= cols) gridCol = cols - 1;
        invalidate();
    }

    private void selectGrid() {
        String key = GRID[gridRow][Math.max(0, Math.min(gridCol, GRID[gridRow].length - 1))];
        if ("⌫".equals(key)) {
            if (listener != null) listener.onBackspace();
        } else if ("SPACE".equals(key)) {
            if (listener != null) listener.onText(" ");
        } else if ("↵".equals(key)) {
            if (listener != null) listener.onEnter();
        } else if (listener != null) {
            listener.onText(key.toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == ControlConfig.TypingMode.RADIAL) drawRadial(canvas);
        else drawGrid(canvas);
    }

    private void drawRadial(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.66f;
        float radius = Math.min(w * 0.37f, 145f * density);

        paint.setColor(Color.argb(224, 21, 25, 31));
        canvas.drawCircle(cx, cy, radius + 36f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(Color.argb(190, 110, 117, 128));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        if (radialGroup < 0) {
            for (int i = 0; i < RADIAL_GROUPS.length; i++) {
                float angle = slotAngle(i, RADIAL_GROUPS.length);
                float x = cx + (float)Math.cos(angle) * radius;
                float y = cy + (float)Math.sin(angle) * radius;
                boolean selected = magnitude() >= 0.24f && radialSlot(joyX, joyY, RADIAL_GROUPS.length) == i;
                drawRadialLabel(canvas, RADIAL_GROUPS[i], x, y, selected, 16f);
            }
            textPaint.setTextSize(15f * density);
            canvas.drawText("•", cx, cy + 5f * density, textPaint);
            mutedTextPaint.setTextSize(11f * density);
            canvas.drawText(getContext().getString(R.string.typing_radial_center_hint), cx, cy + 28f * density, mutedTextPaint);
        } else {
            String letters = RADIAL_GROUPS[radialGroup];
            for (int i = 0; i < letters.length(); i++) {
                float angle = slotAngle(i, letters.length());
                float x = cx + (float)Math.cos(angle) * radius * 0.83f;
                float y = cy + (float)Math.sin(angle) * radius * 0.83f;
                boolean selected = magnitude() >= 0.20f && radialSlot(joyX, joyY, letters.length()) == i;
                drawRadialLabel(canvas, String.valueOf(letters.charAt(i)), x, y, selected, 24f);
            }
            mutedTextPaint.setTextSize(12f * density);
            canvas.drawText(getContext().getString(R.string.typing_choose_letter), cx, cy + 5f * density, mutedTextPaint);
        }

        mutedTextPaint.setTextSize(12f * density);
        canvas.drawText(getContext().getString(R.string.typing_radial_footer), cx, cy + radius + 28f * density, mutedTextPaint);
    }

    private void drawRadialLabel(Canvas canvas, String label, float x, float y, boolean selected, float sp) {
        float r = selected ? 25f * density : 21f * density;
        if (selected) canvas.drawCircle(x, y, r, selectedPaint);
        textPaint.setTextSize(sp * density);
        canvas.drawText(label, x, y + textPaint.getTextSize() * 0.34f, textPaint);
    }

    private void drawGrid(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float panelLeft = 18f * density;
        float panelRight = w - 18f * density;
        float panelBottom = h - 54f * density;
        float rowH = 47f * density;
        float panelTop = panelBottom - GRID.length * rowH - 44f * density;
        RectF panel = new RectF(panelLeft, panelTop, panelRight, panelBottom);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, paint);

        mutedTextPaint.setTextSize(12f * density);
        canvas.drawText(getContext().getString(R.string.typing_keyboard_footer), w / 2f,
                panelTop + 24f * density, mutedTextPaint);

        float y = panelTop + 42f * density;
        for (int r = 0; r < GRID.length; r++) {
            String[] row = GRID[r];
            float gap = 4f * density;
            float usable = panelRight - panelLeft - 20f * density;
            float keyW = (usable - gap * (row.length - 1)) / row.length;
            float x = panelLeft + 10f * density;
            for (int c = 0; c < row.length; c++) {
                RectF key = new RectF(x, y, x + keyW, y + rowH - 6f * density);
                Paint kp = (r == gridRow && c == Math.min(gridCol, row.length - 1)) ? selectedPaint : paint;
                int old = kp.getColor();
                if (kp == paint) kp.setColor(Color.rgb(48, 54, 63));
                canvas.drawRoundRect(key, 8f * density, 8f * density, kp);
                if (kp == paint) kp.setColor(old);
                String display = "SPACE".equals(row[c]) ? getContext().getString(R.string.typing_space_key) : row[c];
                textPaint.setTextSize(("SPACE".equals(row[c]) ? 11f : 14f) * density);
                canvas.drawText(display, key.centerX(), key.centerY() + textPaint.getTextSize() * 0.34f, textPaint);
                x += keyW + gap;
            }
            y += rowH;
        }
    }

    private float magnitude() { return (float)Math.sqrt(joyX * joyX + joyY * joyY); }

    private static int radialSlot(float x, float y, int count) {
        if (count <= 0) return 0;
        double angle = Math.atan2(-y, x);
        if (angle < -Math.PI / 2) angle += Math.PI * 2;
        double normalized = angle + Math.PI / 2;
        int slot = (int)Math.floor((normalized / (Math.PI * 2)) * count + 0.5);
        slot %= count;
        if (slot < 0) slot += count;
        return slot;
    }

    private static float slotAngle(int index, int count) {
        return (float)(-Math.PI / 2 + (Math.PI * 2 * index / count));
    }

    private static int cardinalDirection(float x, float y, float threshold) {
        float ax = Math.abs(x), ay = Math.abs(y);
        if (Math.max(ax, ay) < threshold) return 0;
        if (ax >= ay) return x >= 0f ? 4 : 3;
        return y >= 0f ? 1 : 2;
    }
}
