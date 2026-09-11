package pl.openai.pokeballmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/** Read-only typing overlay controlled entirely by Poké Ball Plus. */
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

    // QWERTY rows mirror a normal phone keyboard: full top row, inset home row,
    // bottom letter row with Polish-character access/backspace, then function row.
    private static final String[][] GRID_LETTERS = {
            {"Q","W","E","R","T","Y","U","I","O","P"},
            {"A","S","D","F","G","H","J","K","L"},
            {"Z","X","C","V","B","N","M"},
            {"123","SPACE","↵","⌫"}
    };
    private static final String[][] GRID_NUMBERS = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"-","/",":",";","(",")","%","@","#"},
            {".",",","+","*","=","_","€","$","£"},
            {"ABC","#+=","SPACE","↵"}
    };
    private static final String[][] GRID_SYMBOLS = {
            {"Ą","Ć","Ę","Ł","Ń","Ó","Ś","Ź","Ż"},
            {"!","?","'","\"","&","|","~","^","°"},
            {"[","]","{","}","<",">","\\","•","…"},
            {"€","$","£","¥","#","@","%","+","="},
            {"ABC","123","SPACE","↵","⌫"}
    };

    private static final class KeySpec {
        final String label;
        final float span;
        KeySpec(String label, float span) { this.label = label; this.span = span; }
    }

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Listener listener;
    private final ControlConfig config;

    private ControlConfig.TypingMode mode;
    private Page page = Page.LETTERS;
    private float joyX;
    private float joyY;
    private int gridRow;
    private int gridCol;
    private int lastGridDirection;
    private long nextGridRepeatMs;
    private String capturedSelection;
    private String releaseCandidate;
    private boolean radialDeflected;

    public TypingOverlayView(Context context, ControlConfig.TypingMode mode,
                             ControlConfig config, Listener listener) {
        super(context);
        this.mode = mode;
        this.config = config;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setMode(ControlConfig.TypingMode mode) {
        this.mode = mode;
        gridRow = gridCol = 0;
        lastGridDirection = 0;
        capturedSelection = null;
        releaseCandidate = null;
        radialDeflected = false;
        invalidate();
    }

    public void refreshAppearance() { invalidate(); }

    public void updateJoystick(float x, float y, long nowMs) {
        joyX = x;
        joyY = y;
        if (mode == ControlConfig.TypingMode.KEYBOARD) {
            updateGridSelection(nowMs);
        } else {
            updateRadialReleaseSelection();
        }
        invalidate();
    }

    public void captureSelection() {
        capturedSelection = currentSelectionKey();
    }

    public void clearCapturedSelection() {
        capturedSelection = null;
        releaseCandidate = null;
        radialDeflected = false;
    }

    public void select() {
        // In release-confirm radial mode, Top is intentionally reserved for Space.
        // Characters/options are confirmed by returning the joystick to neutral.
        if (mode == ControlConfig.TypingMode.RADIAL
                && config.radialConfirmMode() == ControlConfig.RadialConfirmMode.RELEASE) {
            capturedSelection = null;
            releaseCandidate = null;
            radialDeflected = false;
            activateKey("SPACE");
            return;
        }
        String key = capturedSelection != null ? capturedSelection : currentSelectionKey();
        capturedSelection = null;
        activateKey(key);
    }

    public void backspace() {
        if (listener != null) listener.onBackspace();
    }

    private void updateRadialReleaseSelection() {
        if (config.radialConfirmMode() != ControlConfig.RadialConfirmMode.RELEASE) {
            releaseCandidate = null;
            radialDeflected = false;
            return;
        }
        float magnitude = magnitude();
        if (magnitude >= 0.42f) {
            radialDeflected = true;
            releaseCandidate = radialKeyAtCurrentDirection();
            return;
        }
        if (magnitude <= 0.20f && radialDeflected) {
            String key = releaseCandidate;
            releaseCandidate = null;
            radialDeflected = false;
            if (key != null) activateKey(key);
        }
    }

    private String currentSelectionKey() {
        if (mode == ControlConfig.TypingMode.RADIAL) {
            if (magnitude() < 0.24f) return "SPACE";
            return radialKeyAtCurrentDirection();
        }
        String[][] grid = grid();
        int row = Math.max(0, Math.min(gridRow, grid.length - 1));
        int col = Math.max(0, Math.min(gridCol, grid[row].length - 1));
        return grid[row][col];
    }

    private String radialKeyAtCurrentDirection() {
        String[] keys = radialKeys();
        int slot = radialSlot(joyX, joyY, keys.length);
        return keys[Math.max(0, Math.min(slot, keys.length - 1))];
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
            case "123": setPage(Page.NUMBERS); return;
            case "#+=": setPage(Page.SYMBOLS); return;
            case "ABC": setPage(Page.LETTERS); return;
            default:
                if (listener != null) listener.onText(key.toLowerCase(Locale.ROOT));
        }
    }

    private void setPage(Page next) {
        page = next;
        gridRow = gridCol = 0;
        lastGridDirection = 0;
        nextGridRepeatMs = 0L;
        capturedSelection = null;
        releaseCandidate = null;
        radialDeflected = false;
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
        updatePaintOpacity();
        if (mode == ControlConfig.TypingMode.RADIAL) drawRadial(canvas);
        else drawGrid(canvas);
    }

    private void updatePaintOpacity() {
        float opacity = config.typingOverlayOpacity();
        int panelA = Math.round(255f * opacity);
        int keyA = Math.min(245, Math.round(255f * Math.min(0.96f, opacity + 0.08f)));
        panelPaint.setColor(Color.argb(panelA, 21, 25, 31));
        keyPaint.setColor(Color.argb(keyA, 48, 54, 63));
        selectedPaint.setColor(Color.argb(Math.max(205, keyA), 255, 68, 80));
    }

    private void drawRadial(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float radius = Math.min(w * 0.42f, 172f * density);
        float outerRadius = radius + 35f * density;
        // Anchor to the bottom of the usable display so the wheel covers the IME area.
        float cy = h - outerRadius - 8f * density;
        if (cy < outerRadius + 8f * density) cy = h / 2f;
        String[] keys = radialKeys();
        int selected = magnitude() >= 0.24f ? radialSlot(joyX, joyY, keys.length) : -1;

        canvas.drawCircle(cx, cy, outerRadius, panelPaint);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.2f * density);
        int ringAlpha = Math.max(90, Math.round(190f * config.typingOverlayOpacity()));
        ring.setColor(Color.argb(ringAlpha, 110, 117, 128));
        canvas.drawCircle(cx, cy, radius, ring);

        for (int i = 0; i < keys.length; i++) {
            float angle = slotAngle(i, keys.length);
            float x = cx + (float)Math.cos(angle) * radius;
            float y = cy + (float)Math.sin(angle) * radius;
            String label = keys[i];
            boolean special = label.length() > 1 || "⌫".equals(label) || "↵".equals(label);
            drawRadialLabel(canvas, label, x, y, selected == i, special ? 11f : 16f);
        }

        selectedPaint.setColor(Color.argb(Math.max(205, selectedPaint.getAlpha()), 255, 68, 80));
        canvas.drawCircle(cx, cy, 26f * density, selectedPaint);
        textPaint.setTextSize(12f * density);
        canvas.drawText(getContext().getString(R.string.typing_space_key), cx,
                cy + textPaint.getTextSize() * 0.34f, textPaint);
    }

    private void drawRadialLabel(Canvas canvas, String label, float x, float y, boolean selected, float sp) {
        float r = selected ? 17f * density : 14f * density;
        if (selected) canvas.drawCircle(x, y, r, selectedPaint);
        textPaint.setTextSize(sp * density);
        canvas.drawText(displayLabel(label), x, y + textPaint.getTextSize() * 0.34f, textPaint);
    }

    private void drawGrid(Canvas canvas) {
        String[][] source = grid();
        KeySpec[][] layout = buildLayout(source);
        final float logicalCols = 10f;
        float w = getWidth();
        float h = getHeight();
        float panelLeft = 5f * density;
        float panelRight = w - 5f * density;
        float panelBottom = h - 5f * density;
        float panelPad = 9f * density;
        float gap = 4.5f * density;
        float rowH = 58f * density;
        float panelHeight = panelPad * 2f + layout.length * rowH + Math.max(0, layout.length - 1) * gap;
        float panelTop = panelBottom - panelHeight;
        RectF panel = new RectF(panelLeft, panelTop, panelRight, panelBottom);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);

        float usable = panel.width() - panelPad * 2f;
        float unit = (usable - gap * (logicalCols - 1f)) / logicalCols;
        float y = panelTop + panelPad;
        for (int r = 0; r < layout.length; r++) {
            KeySpec[] row = layout[r];
            float totalUnits = 0f;
            for (KeySpec key : row) totalUnits += key.span;
            float rowWidth = totalUnits * unit + Math.max(0, row.length - 1) * gap;
            float x = panelLeft + (panel.width() - rowWidth) / 2f;
            for (int c = 0; c < row.length; c++) {
                KeySpec spec = row[c];
                float keyW = spec.span * unit + Math.max(0f, spec.span - 1f) * gap;
                RectF key = new RectF(x, y, x + keyW, y + rowH);
                Paint kp = (r == gridRow && c == Math.min(gridCol, row.length - 1))
                        ? selectedPaint : keyPaint;
                canvas.drawRoundRect(key, 10f * density, 10f * density, kp);
                String display = displayLabel(spec.label);
                textPaint.setTextSize(keyTextSize(display) * density);
                canvas.drawText(display, key.centerX(), key.centerY() + textPaint.getTextSize() * 0.34f, textPaint);
                x += keyW + gap;
            }
            y += rowH + gap;
        }
    }

    private KeySpec[][] buildLayout(String[][] source) {
        KeySpec[][] rows = new KeySpec[source.length][];
        for (int r = 0; r < source.length; r++) {
            String[] row = source[r];
            rows[r] = new KeySpec[row.length];
            for (int c = 0; c < row.length; c++) rows[r][c] = new KeySpec(row[c], keySpan(row[c], row.length));
        }
        return rows;
    }

    private float keySpan(String key, int rowLength) {
        // Normal letters always use the same width. Only the bottom function rows expand.
        if (rowLength >= 8) return 1f;
        if (rowLength == 3) {
            if ("SPACE".equals(key)) return 5f;
            if ("↵".equals(key)) return 3f;
            return 2f;
        }
        if (rowLength == 4) {
            if ("SPACE".equals(key)) return 4f;
            return 2f;
        }
        if (rowLength == 5) {
            if ("SPACE".equals(key)) return 3.2f;
            if ("⌫".equals(key) || "↵".equals(key)) return 1.7f;
            return 1.7f;
        }
        return 1f;
    }

    private String displayLabel(String label) {
        if ("SPACE".equals(label)) return getContext().getString(R.string.typing_space_key);
        if ("↵".equals(label)) return "Enter";
        return label;
    }

    private float keyTextSize(String label) {
        if ("Enter".equals(label)) return 13f;
        if (label.length() > 5) return 10f;
        if (label.length() > 3) return 11.5f;
        if (label.length() > 1) return 13f;
        return 16f;
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
