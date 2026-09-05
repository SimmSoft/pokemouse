package pl.openai.pokeballmouse;

/**
 * Six-direction detector for the Motion gestures live preview.
 * The caller resets it at the start of a Top hold and stops feeding it after the first
 * accepted direction, so the preview cannot bounce between movement and return impulses.
 */
public final class MotionTelemetryDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN, FORWARD, BACKWARD }

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean initialized;
    private Direction candidate;
    private int candidateSamples;

    private static final float ABSOLUTE_NOISE_FLOOR_G = 0.40f;
    private static final float MAX_SECOND_AXIS_RATIO = 0.70f;
    private static final float STRONG_IMPULSE_MULTIPLIER = 1.40f;

    public void reset() {
        initialized = false;
        baseX = baseY = baseZ = 0f;
        candidate = null;
        candidateSamples = 0;
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

        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float absZ = Math.abs(dz);
        float dominant = Math.max(absX, Math.max(absY, absZ));
        float effectiveThreshold = Math.max(ABSOLUTE_NOISE_FLOOR_G, threshold);
        if (dominant < effectiveThreshold) {
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        float second = secondLargest(absX, absY, absZ);
        if (second > dominant * MAX_SECOND_AXIS_RATIO) return null;

        Direction direction;
        if (absX >= absY && absX >= absZ) {
            direction = dx >= 0f ? Direction.RIGHT : Direction.LEFT;
        } else if (absY >= absX && absY >= absZ) {
            direction = dy >= 0f ? Direction.UP : Direction.DOWN;
        } else {
            direction = dz >= 0f ? Direction.FORWARD : Direction.BACKWARD;
        }

        if (dominant >= effectiveThreshold * STRONG_IMPULSE_MULTIPLIER) return direction;
        if (direction == candidate) candidateSamples++;
        else {
            candidate = direction;
            candidateSamples = 1;
        }
        return candidateSamples >= 2 ? direction : null;
    }

    private static float secondLargest(float a, float b, float c) {
        float max = Math.max(a, Math.max(b, c));
        float min = Math.min(a, Math.min(b, c));
        return a + b + c - max - min;
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
