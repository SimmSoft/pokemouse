package pl.openai.pokeballmouse;

/**
 * Template-based four-way gesture detector learned from a specific Poké Ball Plus profile.
 *
 * <p>Instead of committing to the very first spike, this detector keeps the strongest
 * confident sample during a Top hold and can finalize the gesture on release. That makes
 * short wrist flicks far more stable because the return motion or a brief button bump is
 * less likely to steal the decision.</p>
 */
public final class CalibratedMotionGestureDetector {
    private static final float MIN_ACCEPT_DOT = 0.44f;
    private static final float MIN_ACCEPT_MARGIN = 0.05f;
    private static final float CLEAR_EARLY_DOT = 0.84f;
    private static final float CLEAR_EARLY_MARGIN = 0.16f;

    private float baseX, baseY, baseZ;
    private boolean holdActive;
    private boolean consumed;
    private long holdStartMs;

    private MotionGestureDetector.Direction bestDirection;
    private float bestScore;
    private float bestDot;
    private float bestMargin;
    private float bestMagnitude;

    public void reset() {
        baseX = baseY = baseZ = 0f;
        holdActive = false;
        consumed = false;
        holdStartMs = 0L;
        bestDirection = null;
        bestScore = Float.NEGATIVE_INFINITY;
        bestDot = -2f;
        bestMargin = -2f;
        bestMagnitude = 0f;
    }

    public MotionGestureDetector.Direction update(float ax, float ay, float az, boolean top,
                                                  float threshold, long nowMs,
                                                  DeviceProfileStore.MotionTemplates templates) {
        if (templates == null || !finite(ax) || !finite(ay) || !finite(az)) return null;

        float effective = Math.max(0.26f, threshold * 0.82f);

        if (!top) {
            MotionGestureDetector.Direction result = null;
            if (holdActive && !consumed) {
                result = finalizeGesture(effective);
            }
            holdActive = false;
            consumed = false;
            holdStartMs = 0L;
            clearBest();
            return result;
        }

        if (!holdActive) {
            holdActive = true;
            consumed = false;
            holdStartMs = nowMs;
            baseX = ax;
            baseY = ay;
            baseZ = az;
            clearBest();
            return null;
        }

        if (consumed) return null;

        if (nowMs - holdStartMs < MotionGestureDetector.ARM_DELAY_MS) {
            baseX += (ax - baseX) * 0.52f;
            baseY += (ay - baseY) * 0.52f;
            baseZ += (az - baseZ) * 0.52f;
            clearBest();
            return null;
        }

        float dx = ax - baseX;
        float dy = ay - baseY;
        float dz = az - baseZ;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (magnitude < effective) return null;

        float nx = dx / magnitude;
        float ny = dy / magnitude;
        float nz = dz / magnitude;

        MotionGestureDetector.Direction best = null;
        float bestLocalDot = -2f;
        float secondDot = -2f;
        for (MotionGestureDetector.Direction direction : MotionGestureDetector.Direction.values()) {
            float[] t = templates.vector(direction);
            float dot = nx * t[0] + ny * t[1] + nz * t[2];
            if (dot > bestLocalDot) {
                secondDot = bestLocalDot;
                bestLocalDot = dot;
                best = direction;
            } else if (dot > secondDot) {
                secondDot = dot;
            }
        }

        if (best == null) return null;
        float margin = bestLocalDot - secondDot;
        // Slightly reward stronger impulses and unambiguous matches.
        float normalizedStrength = Math.min(1.35f, magnitude / effective);
        float score = bestLocalDot + margin * 0.55f + (normalizedStrength - 1f) * 0.12f;

        if (bestLocalDot >= MIN_ACCEPT_DOT - 0.06f && score > bestScore) {
            bestDirection = best;
            bestScore = score;
            bestDot = bestLocalDot;
            bestMargin = margin;
            bestMagnitude = magnitude;
        }

        if (bestLocalDot >= CLEAR_EARLY_DOT && margin >= CLEAR_EARLY_MARGIN
                && magnitude >= effective * 1.10f) {
            consumed = true;
            return best;
        }
        return null;
    }

    private MotionGestureDetector.Direction finalizeGesture(float effective) {
        if (bestDirection == null) return null;
        if (bestMagnitude < effective) return null;
        if (bestDot >= 0.58f) return bestDirection;
        if (bestDot >= MIN_ACCEPT_DOT && bestMargin >= MIN_ACCEPT_MARGIN) return bestDirection;
        return null;
    }

    private void clearBest() {
        bestDirection = null;
        bestScore = Float.NEGATIVE_INFINITY;
        bestDot = -2f;
        bestMargin = -2f;
        bestMagnitude = 0f;
    }

    private static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
