package pl.openai.pokeballmouse;

/** Template-based four-way gesture detector learned from a specific Poké Ball Plus profile. */
public final class CalibratedMotionGestureDetector {
    private float baseX, baseY, baseZ;
    private boolean holdActive;
    private boolean consumed;
    private long holdStartMs;
    private MotionGestureDetector.Direction candidate;
    private int candidateSamples;

    public void reset() {
        baseX = baseY = baseZ = 0f;
        holdActive = false;
        consumed = false;
        holdStartMs = 0L;
        candidate = null;
        candidateSamples = 0;
    }

    public MotionGestureDetector.Direction update(float ax, float ay, float az, boolean top,
                                                   float threshold, long nowMs,
                                                   DeviceProfileStore.MotionTemplates templates) {
        if (templates == null || !finite(ax) || !finite(ay) || !finite(az)) return null;
        if (!top) {
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
            baseX = ax; baseY = ay; baseZ = az;
            candidate = null; candidateSamples = 0;
            return null;
        }
        if (consumed) return null;
        if (nowMs - holdStartMs < MotionGestureDetector.ARM_DELAY_MS) {
            baseX += (ax - baseX) * 0.50f;
            baseY += (ay - baseY) * 0.50f;
            baseZ += (az - baseZ) * 0.50f;
            candidate = null; candidateSamples = 0;
            return null;
        }

        float dx = ax - baseX, dy = ay - baseY, dz = az - baseZ;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float effective = Math.max(0.28f, threshold * 0.86f);
        if (magnitude < effective) {
            candidate = null; candidateSamples = 0;
            return null;
        }
        float nx = dx / magnitude, ny = dy / magnitude, nz = dz / magnitude;
        MotionGestureDetector.Direction best = null;
        float bestDot = -2f;
        for (MotionGestureDetector.Direction d : MotionGestureDetector.Direction.values()) {
            float[] t = templates.vector(d);
            float dot = nx * t[0] + ny * t[1] + nz * t[2];
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        if (best == null || bestDot < 0.47f) return null;
        if (magnitude >= effective * 1.15f && bestDot >= 0.58f) {
            consumed = true;
            return best;
        }
        if (best == candidate) candidateSamples++; else { candidate = best; candidateSamples = 1; }
        if (candidateSamples >= 2) {
            consumed = true;
            return best;
        }
        return null;
    }

    private static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
}
