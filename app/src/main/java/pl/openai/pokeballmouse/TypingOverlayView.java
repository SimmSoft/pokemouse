package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/**
 * Read-only typing overlay controlled entirely by Poké Ball Plus.
 *
 * The radial mode deliberately exposes one character per slot. There are no
 * ABC/DEF-style groups: point the joystick at the character and press the
 * Top button once. The center remains Space. 123 / #+= / ABC are real keys and
 * switch between letters, numbers and symbols.
 */
public final class TypingOverlayView extends View {
    public interface Listener {
        void onText(String text);
        void onBackspace();
        void onEnter();
    }

    private enum Page { LETTERS, NUMBERS, SYMBOLS }

    private static final String[] RADIAL_LETTERS = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            "123","⌫","↵"
    };
    private static final String[] RADIAL_NUMBERS = {
            "0","1","2","3","4","5","6","7","8","9",
            ".",",","-","+","/","*",":",";","(",")","%","@","#",
            "#+=","ABC","⌫","↵"
    };
    private static final String[] RADIAL_SYMBOLS = {
            "!","?","'","\"","_","&","€","$","£","¥","[","]","{","}",
            "<",">","=","\\","|","~","^","°","•","…",
            "ABC","123","⌫","↵"
    };

    private static final String[][] GRID_LETTERS = {
            {"Q","W","E","R","T","Y","U","I","O","P"},
            {"A","S","D","F","G","H","J","K","L"},
            {"Z","X","C","V","B","N","M"},
            {"Ą","Ć","Ę","Ł","Ń","Ó","Ś","Ź","Ż"},
            {"123","SPACE","⌫","↵"}
    };
    private static final String[][] GRID_NUMBERS = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"-","/",":",";","(",")","%","@","#"},
            {".",",","+","*","=","_","€","$","£"},
            {"ABC","#+=","SPACE","⌫","↵"}
    };
    private static final String[][] GRID_SYMBOLS = {
            {"!","?","'","\"","&","|","~","^","°"},
            {"[","]","{","}","<",">","\\","•","…"},
            {"€","$","£","¥","#","@","%","+","="},
            {"ABC","123","SPACE","⌫","↵"}
    };

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Listener listener;

    private ControlConfig.TypingMode mode;
    private Page page = Page.LETTERS;
    private float joyX;
    private float joyY;
    private int gridRow;
    private int gridCol;
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

        panelPaint.setColor(Color.argb(228, 23, 27, 33));
        keyPaint.setColor(Color.rgb(48, 54, 63));
        selectedPaint.setColor(Color.rgb(255, 68, 80));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mutedTextPaint.setColor(Color.rgb(190, 195, 204));
        mutedTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setMode(ControlConfig.TypingMode mode) {
        this.mode = mode;
        gridRow = gridCol = 0;
        lastGridDirection = 0;
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

    public void backspace() {
        if (listener != null) listener.onBackspace();
    }

    private void selectRadial() {
        float magnitude = magnitude();
        if (magnitude < 0.24f) {
            if (listener != null) listener.onText(" ");
            return;
        }
        String[] keys = radialKeys();
        int slot = radialSlot(joyX, joyY, keys.length);
        activateKey(keys[Math.max(0, Math.min(slot, keys.length - 1))]);
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
        String[][] grid = grid();
        if (direction == 1) gridRow--;
        else if (direction == 2) gridRow++;
        else if (direction == 3) gridCol--;
        else if (direction == 4) gridCol++;

        if (gridRow < 0) gridRow = grid.length - 1;
        if (gridRow >= grid.length) gridRow = 0;
        int cols = grid[gridRow].length;
        if (gridCol < 0) gridCol = cols - 1;
        if (gridCol >= cols) gridCol = cols - 1;
        invalidate();
    }

    private void selectGrid() {
        String[][] grid = grid();
        int row = Math.max(0, Math.min(gridRow, grid.length - 1));
        int col = Math.max(0, Math.min(gridCol, grid[row].length - 1));
        activateKey(grid[row][col]);
    }

    private void activateKey(String key) {
        if (key == null) return;
        switch (key) {
            case "⌫":
                if (listener != null) listener.onBackspace();
                return;
            case "↵":
                if (listener != null) listener.onEnter();
                return;
            case "SPACE":
                if (listener != null) listener.onText(" ");
                return;
            case "123":
                setPage(Page.NUMBERS);
                return;
            case "#+=":
                setPage(Page.SYMBOLS);
                return;
            case "ABC":
                setPage(Page.LETTERS);
                return;
            default:
                if (listener != null) listener.onText(key.toLowerCase(Locale.ROOT));
        }
    }

    private void setPage(Page next) {
        page = next;
        gridRow = gridCol = 0;
        lastGridDirection = 0;
        nextGridRepeatMs = 0L;
        invalidate();
    }

    private String[] radialKeys() {
        switch (page) {
            case NUMBERS: return RADIAL_NUMBERS;
            case SYMBOLS: return RADIAL_SYMBOLS;
            case LETTERS:
            default: return RADIAL_LETTERS;
        }
    }

    private String[][] grid() {
        switch (page) {
            case NUMBERS: return GRID_NUMBERS;
            case SYMBOLS: return GRID_SYMBOLS;
            case LETTERS:
            default: return GRID_LETTERS;
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
        float cy = h * 0.63f;
        float radius = Math.min(w * 0.39f, 154f * density);
        String[] keys = radialKeys();
        int selected = magnitude() >= 0.24f ? radialSlot(joyX, joyY, keys.length) : -1;

        panelPaint.setColor(Color.argb(224, 21, 25, 31));
        canvas.drawCircle(cx, cy, radius + 34f * density, panelPaint);
        panelPaint.setStyle(Paint.Style.STROKE);
        panelPaint.setStrokeWidth(1.2f * density);
        panelPaint.setColor(Color.argb(190, 110, 117, 128));
        canvas.drawCircle(cx, cy, radius, panelPaint);
        panelPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < keys.length; i++) {
            float angle = slotAngle(i, keys.length);
            float x = cx + (float)Math.cos(angle) * radius;
            float y = cy + (float)Math.sin(angle) * radius;
            String label = keys[i];
            boolean special = label.length() > 1 || "⌫".equals(label) || "↵".equals(label);
            drawRadialLabel(canvas, label, x, y, selected == i, special ? 11f : 16f);
        }

        if (selected < 0) {
            selectedPaint.setColor(Color.rgb(255, 68, 80));
            canvas.drawCircle(cx, cy, 24f * density, selectedPaint);
            textPaint.setTextSize(12f * density);
            canvas.drawText(getContext().getString(R.string.typing_space_key), cx,
                    cy + textPaint.getTextSize() * 0.34f, textPaint);
        }

        mutedTextPaint.setTextSize(12f * density);
        canvas.drawText(pageLabel(), cx, cy - radius - 25f * density, mutedTextPaint);
        canvas.drawText(getContext().getString(R.string.typing_radial_footer), cx,
                cy + radius + 30f * density, mutedTextPaint);
    }

    private void drawRadialLabel(Canvas canvas, String label, float x, float y, boolean selected, float sp) {
        float r = selected ? 17f * density : 14f * density;
        if (selected) canvas.drawCircle(x, y, r, selectedPaint);
        textPaint.setTextSize(sp * density);
        canvas.drawText(label, x, y + textPaint.getTextSize() * 0.34f, textPaint);
    }

    private void drawGrid(Canvas canvas) {
        String[][] grid = grid();
        float w = getWidth();
        float h = getHeight();
        float panelLeft = 14f * density;
        float panelRight = w - 14f * density;
        float panelBottom = h - 46f * density;
        float rowH = 47f * density;
        float panelTop = panelBottom - grid.length * rowH - 50f * density;
        RectF panel = new RectF(panelLeft, panelTop, panelRight, panelBottom);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);

        mutedTextPaint.setTextSize(12f * density);
        canvas.drawText(pageLabel() + "  ·  " + getContext().getString(R.string.typing_keyboard_footer),
                w / 2f, panelTop + 25f * density, mutedTextPaint);

        float y = panelTop + 43f * density;
        for (int r = 0; r < grid.length; r++) {
            String[] row = grid[r];
            float gap = 4f * density;
            float usable = panelRight - panelLeft - 20f * density;
            float keyW = (usable - gap * (row.length - 1)) / row.length;
            float x = panelLeft + 10f * density;
            for (int c = 0; c < row.length; c++) {
                RectF key = new RectF(x, y, x + keyW, y + rowH - 6f * density);
                Paint kp = (r == gridRow && c == Math.min(gridCol, row.length - 1))
                        ? selectedPaint : keyPaint;
                canvas.drawRoundRect(key, 8f * density, 8f * density, kp);
                String display = "SPACE".equals(row[c]) ? getContext().getString(R.string.typing_space_key) : row[c];
                textPaint.setTextSize((display.length() > 3 ? 10f : 15f) * density);
                canvas.drawText(display, key.centerX(), key.centerY() + textPaint.getTextSize() * 0.34f, textPaint);
                x += keyW + gap;
            }
            y += rowH;
        }
    }

    private String pageLabel() {
        switch (page) {
            case NUMBERS: return "123";
            case SYMBOLS: return "#+=";
            case LETTERS:
            default: return "ABC";
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
