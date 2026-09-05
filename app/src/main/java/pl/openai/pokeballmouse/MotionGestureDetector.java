package pl.openai.pokeballmouse;

/**
 * Detects one deliberate four-way flick for each Top-button hold.
 *
 * The arming delay is intentionally short: long enough to ignore the physical bump from
 * pressing Top, but short enough that a quick flick still feels immediate. Horizontal
 * motion is measured as the full X/Z-plane magnitude, not just one axis. This matters
 * because the Poké Ball Plus can be rotated in the hand and a real left/right movement
 * often lands partly on X and partly on Z.
 */
public final class MotionGestureDetector {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    /** Short button-settle period before motion can trigger. */
    public static final long ARM_DELAY_MS = 100L;

    private float baseX;
    private float baseY;
    private float baseZ;
    private boolean baselineInitialized;
    private boolean holdActive;
    private boolean consumed;
    private long holdStartMs;

    private Direction candidate;
    private int candidateSamples;

    // Learned positive lateral axis in the device X/Z plane. It is intentionally kept
    // across Top holds so left/right remains stable while the ball is held similarly.
    private boolean lateralAxisLearned;
    private float lateralAxisX = 1f;
    private float lateralAxisZ = 0f;

    private static final float ABSOLUTE_NOISE_FLOOR_G = 0.30f;
    private static final float STRONG_IMPULSE_MULTIPLIER = 1.18f;
    // Horizontal gets a small preference in ambiguous hand arcs because vertical motion
    // already has a dedicated Y axis while lateral motion is split between X and Z.
    private static final float VERTICAL_DOMINANCE = 1.06f;
    private static final float LATERAL_RELEARN_RATIO = 0.38f;

    public void reset() {
        baselineInitialized = false;
        holdActive = false;
        consumed = false;
        holdStartMs = 0L;
        candidate = null;
        candidateSamples = 0;
        baseX = baseY = baseZ = 0f;
        lateralAxisLearned = false;
        lateralAxisX = 1f;
        lateralAxisZ = 0f;
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

        // Follow the hand only during the brief Top-button settle interval. This absorbs
        // the mechanical bump of pressing Top without making the user wait through a long hold.
        if (nowMs - holdStartMs < ARM_DELAY_MS) {
            followBaseline(ax, ay, az, 0.52f);
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;
        float vertical = Math.abs(dy);
        float horizontal = hypot(dx, dz);
        float dominant = Math.max(vertical, horizontal);
        float effectiveThreshold = Math.max(ABSOLUTE_NOISE_FLOOR_G, threshold);

        if (dominant < effectiveThreshold) {
            candidate = null;
            candidateSamples = 0;
            return null;
        }

        Direction direction;
        if (vertical >= horizontal * VERTICAL_DOMINANCE) {
            direction = dy >= 0f ? Direction.UP : Direction.DOWN;
        } else {
            float lateral = lateralProjection(dx, dz, horizontal);
            direction = lateral >= 0f ? Direction.RIGHT : Direction.LEFT;
        }

        // A genuinely quick flick can be only one input report long. Accept a strong
        // single impulse; weaker movements still require two agreeing samples.
        if (dominant >= effectiveThreshold * STRONG_IMPULSE_MULTIPLIER) {
            if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                refineLateralAxis(dx, dz, horizontal, direction);
            }
            consumed = true;
            return direction;
        }

        if (direction == candidate) candidateSamples++;
        else {
            candidate = direction;
            candidateSamples = 1;
        }
        if (candidateSamples >= 2) {
            if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                refineLateralAxis(dx, dz, horizontal, direction);
            }
            consumed = true;
            return direction;
        }
        return null;
    }

    private float lateralProjection(float dx, float dz, float horizontal) {
        if (horizontal < 0.0001f) return 0f;

        if (!lateralAxisLearned) {
            learnLateralAxis(dx, dz, horizontal);
        }

        float projected = dx * lateralAxisX + dz * lateralAxisZ;
        // If the ball was rotated substantially since the previous gesture, the learned
        // axis can become nearly perpendicular. Re-learn from the clean current impulse.
        if (Math.abs(projected) < horizontal * LATERAL_RELEARN_RATIO) {
            learnLateralAxis(dx, dz, horizontal);
            projected = dx * lateralAxisX + dz * lateralAxisZ;
        }
        return projected;
    }

    private void learnLateralAxis(float dx, float dz, float horizontal) {
        float nx = dx / horizontal;
        float nz = dz / horizontal;
        // Preserve the intuitive sign of whichever raw axis currently carries more of
        // the lateral motion. The user's existing "Invert X" option flips both X and Z.
        float rawReference = Math.abs(dx) >= Math.abs(dz) ? dx : dz;
        float sign = rawReference >= 0f ? 1f : -1f;
        lateralAxisX = nx * sign;
        lateralAxisZ = nz * sign;
        lateralAxisLearned = true;
    }

    private void refineLateralAxis(float dx, float dz, float horizontal, Direction direction) {
        if (horizontal < 0.0001f) return;
        float sign = direction == Direction.RIGHT ? 1f : -1f;
        float targetX = (dx / horizontal) * sign;
        float targetZ = (dz / horizontal) * sign;
        if (!lateralAxisLearned) {
            lateralAxisX = targetX;
            lateralAxisZ = targetZ;
            lateralAxisLearned = true;
            return;
        }
        lateralAxisX = lateralAxisX * 0.82f + targetX * 0.18f;
        lateralAxisZ = lateralAxisZ * 0.82f + targetZ * 0.18f;
        float length = hypot(lateralAxisX, lateralAxisZ);
        if (length > 0.0001f) {
            lateralAxisX /= length;
            lateralAxisZ /= length;
        }
    }

    private void followBaseline(float x, float y, float z, float alpha) {
        baseX += (x - baseX) * alpha;
        baseY += (y - baseY) * alpha;
        baseZ += (z - baseZ) * alpha;
    }

    private static float hypot(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
