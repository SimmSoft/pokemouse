package pl.openai.pokeballmouse;

/**
 * Detects one deliberate four-way flick for each Top-button hold.
 *
 * Gesture arming is intentional: Top must already be held for ARM_DELAY_MS before
 * motion can trigger. While arming, the baseline follows the hand, so pressing Top
 * in the middle of a swing does not accidentally classify that swing. After the first
 * accepted direction the detector stays consumed until Top is released; the natural
 * return/braking movement is therefore ignored.
 *
 * Poké Ball Plus orientation varies in the hand. Vertical motion is normally reported
 * strongly on Y, while lateral motion can appear on either X or Z. For horizontal
 * gestures we therefore use the stronger of X/Z. This preserves reliable up/down while
 * making left/right usable in the normal upright grip.
 */
public final class MotionGestureDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    public static final long ARM_DELAY_MS = 300L;

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean baselineInitialized;
    private boolean holdActive;
    private boolean consumed;
    private long holdStartMs;

    private Direction candidate;
    private int candidateSamples;

    private static final float ABSOLUTE_NOISE_FLOOR_G = 0.34f;
    private static final float STRONG_IMPULSE_MULTIPLIER = 1.48f;
    private static final float AXIS_DOMINANCE = 1.12f;

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
            // Calm rolling reference while Top is not held.
            followBaseline(ax, ay, az, baselineInitialized ? 0.16f : 1f);
            baselineInitialized = true;
            holdActive = false;
            consumed = false;
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        if (!holdActive) {
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

        // User must hold Top first. During this interval, deliberately follow the hand.
        // If Top was pressed in the middle of a swing, that swing becomes part of the
        // baseline instead of being interpreted as a gesture after the delay expires.
        if (nowMs - holdStartMs < ARM_DELAY_MS) {
            followBaseline(ax, ay, az, 0.42f);
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float absZ = Math.abs(dz);

        // Upright grip: Y is vertical. Lateral translation may land on X or Z depending
        // on the exact rotation of the ball in the hand, so choose the stronger one.
        float horizontalValue = absX >= absZ ? dx : dz;
        float horizontal = Math.max(absX, absZ);
        float vertical = absY;
        float dominant = Math.max(horizontal, vertical);
        float effectiveThreshold = Math.max(ABSOLUTE_NOISE_FLOOR_G, threshold);

        if (dominant < effectiveThreshold) {
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        Direction direction;
        if (vertical >= horizontal * AXIS_DOMINANCE) {
            direction = dy >= 0f ? Direction.UP : Direction.DOWN;
        } else if (horizontal >= vertical * AXIS_DOMINANCE) {
            direction = horizontalValue >= 0f ? Direction.RIGHT : Direction.LEFT;
        } else {
            // Diagonal/ambiguous impulse: wait for a cleaner sample rather than guessing.
            candidate = null;
            candidateSamples = 0;
            return null;
        }

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

    private void followBaseline(float x, float y, float z, float alpha) {
        baseX += (x - baseX) * alpha;
        baseY += (y - baseY) * alpha;
        baseZ += (z - baseZ) * alpha;
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
