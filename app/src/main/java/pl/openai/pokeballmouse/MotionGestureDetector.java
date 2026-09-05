package pl.openai.pokeballmouse;

/**
 * Detects deliberate one-handed flicks from Poké Ball Plus accelerometer samples.
 * A low-pass estimate removes gravity/orientation; detection runs on the residual impulse.
 */
public final class MotionGestureDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean initialized;
    private long lastGestureMs;

    private static final float BASELINE_ALPHA = 0.12f;
    private static final long COOLDOWN_MS = 420L;

    public void reset() {
        initialized = false;
        baseX = baseY = baseZ = 0f;
        lastGestureMs = 0L;
    }

    public Direction update(float ax, float ay, float az, boolean modifierHeld,
                            float threshold, long nowMs) {
        if (!isFinite(ax) || !isFinite(ay) || !isFinite(az)) return null;
        if (!initialized) {
            baseX = ax; baseY = ay; baseZ = az;
            initialized = true;
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;

        // Keep adapting slowly so normal orientation/gravity changes do not look like a gesture.
        baseX += (ax - baseX) * BASELINE_ALPHA;
        baseY += (ay - baseY) * BASELINE_ALPHA;
        baseZ += (az - baseZ) * BASELINE_ALPHA;

        if (!modifierHeld || nowMs - lastGestureMs < COOLDOWN_MS) return null;

        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float absZ = Math.abs(dz);
        float dominant = Math.max(absX, absY);
        if (dominant < threshold) return null;
        // Reject mostly forward/backward jolts and ambiguous diagonals.
        if (absZ > dominant * 1.25f) return null;
        float minor = Math.min(absX, absY);
        if (minor > dominant * 0.82f) return null;

        lastGestureMs = nowMs;
        if (absX >= absY) return dx >= 0f ? Direction.RIGHT : Direction.LEFT;
        return dy >= 0f ? Direction.UP : Direction.DOWN;
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
