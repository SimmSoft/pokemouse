package pl.openai.pokeballmouse;

/**
 * Lightweight six-direction detector used only for the live Motion gestures UI.
 * It is independent from the Top+gesture action detector, so moving the ball can be
 * visualized even when Top is not held.
 */
public final class MotionTelemetryDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN, FORWARD, BACKWARD }

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean initialized;
    private long lastEventMs;

    private static final float BASELINE_ALPHA = 0.10f;
    private static final long COOLDOWN_MS = 150L;

    public void reset() {
        initialized = false;
        baseX = baseY = baseZ = 0f;
        lastEventMs = 0L;
    }

    public Direction update(float ax, float ay, float az, float threshold, long nowMs) {
        if (!isFinite(ax) || !isFinite(ay) || !isFinite(az)) return null;
        if (!initialized) {
            baseX = ax;
            baseY = ay;
            baseZ = az;
            initialized = true;
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;

        baseX += (ax - baseX) * BASELINE_ALPHA;
        baseY += (ay - baseY) * BASELINE_ALPHA;
        baseZ += (az - baseZ) * BASELINE_ALPHA;

        if (nowMs - lastEventMs < COOLDOWN_MS) return null;

        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float absZ = Math.abs(dz);
        float dominant = Math.max(absX, Math.max(absY, absZ));
        if (dominant < threshold) return null;

        // Reject heavily diagonal motion so the UI does not flicker between directions.
        float second = secondLargest(absX, absY, absZ);
        if (second > dominant * 0.86f) return null;

        lastEventMs = nowMs;
        if (absX >= absY && absX >= absZ) {
            return dx >= 0f ? Direction.RIGHT : Direction.LEFT;
        }
        if (absY >= absX && absY >= absZ) {
            return dy >= 0f ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0f ? Direction.FORWARD : Direction.BACKWARD;
    }

    private static float secondLargest(float a, float b, float c) {
        if (a >= b) {
            if (b >= c) return b;
            return Math.min(a, c);
        }
        if (a >= c) return a;
        return Math.min(b, c);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
