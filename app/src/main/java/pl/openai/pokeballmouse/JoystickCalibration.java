package pl.openai.pokeballmouse;

/** Joystick calibration helpers for manual centering and full per-device calibration. */
public final class JoystickCalibration {
    private JoystickCalibration() {}

    public static float applyAxis(float raw, float center) {
        return applyAxis(raw, center, -1f, 1f);
    }

    public static float applyAxis(float raw, float center, float min, float max) {
        raw = clamp(raw);
        center = clampCenter(center);
        min = clamp(min);
        max = clamp(max);
        if (min >= center - 0.05f) min = -1f;
        if (max <= center + 0.05f) max = 1f;
        if (raw >= center) {
            return clamp((raw - center) / Math.max(0.05f, max - center));
        }
        return clamp((raw - center) / Math.max(0.05f, center - min));
    }

    private static float clampCenter(float v) { return Math.max(-0.85f, Math.min(0.85f, v)); }
    private static float clamp(float value) { return Math.max(-1f, Math.min(1f, value)); }
}
