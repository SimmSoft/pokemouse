package pl.openai.pokeballmouse;

/** Piecewise joystick recentering that preserves the full -1..+1 travel on both sides. */
public final class JoystickCalibration {
    private JoystickCalibration() {}

    public static float applyAxis(float raw, float center) {
        raw = clamp(raw);
        center = Math.max(-0.80f, Math.min(0.80f, center));
        if (Math.abs(raw - center) < 0.0001f) return 0f;
        float out;
        if (raw > center) {
            out = (raw - center) / Math.max(0.001f, 1f - center);
        } else {
            out = (raw - center) / Math.max(0.001f, 1f + center);
        }
        return clamp(out);
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }
}
