package pl.openai.pokeballmouse;

/**
 * Detects one deliberate flick for each Top-button hold.
 *
 * The important bit is the hold lifecycle: when Top is pressed we first take a fresh
 * accelerometer baseline, then accept only the first clear X/Y impulse. The detector
 * remains consumed until Top is released, so the natural hand return / braking impulse
 * cannot fire the opposite action.
 */
public final class MotionGestureDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean baselineInitialized;
    private boolean holdActive;
    private boolean consumed;
    private long holdStartMs;

    private Direction candidate;
    private int candidateSamples;

    private static final long SETTLE_MS = 55L;
    private static final float ABSOLUTE_NOISE_FLOOR_G = 0.32f;
    private static final float STRONG_IMPULSE_MULTIPLIER = 1.42f;
    private static final float MAX_MINOR_AXIS_RATIO = 0.68f;
    private static final float MAX_Z_RATIO = 1.05f;

    public void reset() {
        baselineInitialized = false;
        holdActive = false;
        consumed = false;
        holdStartMs = 0L;
        candidate = null;
        candidateSamples = 0;
        baseX = baseY = baseZ = 0f;
    }

    public Direction update(float ax, float ay, float az, boolean modifierHeld,
                            float threshold, long nowMs) {
        if (!isFinite(ax) || !isFinite(ay) || !isFinite(az)) return null;

        if (!modifierHeld) {
            // While Top is not held, keep a calm rolling gravity/orientation baseline.
            if (!baselineInitialized) {
                baseX = ax;
                baseY = ay;
                baseZ = az;
                baselineInitialized = true;
            } else {
                baseX += (ax - baseX) * 0.16f;
                baseY += (ay - baseY) * 0.16f;
                baseZ += (az - baseZ) * 0.16f;
            }
            holdActive = false;
            consumed = false;
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        if (!holdActive) {
            // Fresh baseline for this Top hold. This avoids stale orientation and makes
            // the threshold relative to how the user is actually holding the ball now.
            holdActive = true;
            consumed = false;
            holdStartMs = nowMs;
            baseX = ax;
            baseY = ay;
            baseZ = az;
            baselineInitialized = true;
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        if (consumed) return null;

        // Give the mechanical Top press a few milliseconds to settle. During this tiny
        // window we follow the sample so the press itself does not look like a flick.
        if (nowMs - holdStartMs < SETTLE_MS) {
            baseX += (ax - baseX) * 0.45f;
            baseY += (ay - baseY) * 0.45f;
            baseZ += (az - baseZ) * 0.45f;
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;

        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float absZ = Math.abs(dz);
        float dominant = Math.max(absX, absY);
        float effectiveThreshold = Math.max(ABSOLUTE_NOISE_FLOOR_G, threshold);

        if (dominant < effectiveThreshold) {
            candidate = null;
            candidateSamples = 0;
            return null;
        }
        if (absZ > dominant * MAX_Z_RATIO) return null;
        float minor = Math.min(absX, absY);
        if (minor > dominant * MAX_MINOR_AXIS_RATIO) return null;

        Direction direction = absX >= absY
                ? (dx >= 0f ? Direction.RIGHT : Direction.LEFT)
                : (dy >= 0f ? Direction.UP : Direction.DOWN);

        // Very clear impulses may trigger immediately. Borderline impulses must be seen
        // in two consecutive samples, which filters hand tremor at the lowest setting.
        if (dominant >= effectiveThreshold * STRONG_IMPULSE_MULTIPLIER) {
            consumed = true;
            return direction;
        }

        if (direction == candidate) candidateSamples++;
        else {
            candidate = direction;
            candidateSamples = 1;
        }
        if (candidateSamples >= 2) {
            consumed = true;
            return direction;
        }
        return null;
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
