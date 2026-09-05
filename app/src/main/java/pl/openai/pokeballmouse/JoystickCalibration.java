package pl.openai.pokeballmouse;

/**
 * Re-centers an already normalized joystick axis around a user-selected resting point.
 * The remaining range on each side is rescaled independently so both directions still
 * reach -1/+1 and therefore have symmetric cursor speed after calibration.
 */
public final class JoystickCalibration {
    private JoystickCalibration() {}

    public static float applyAxis(float raw, float center) {
        raw = clamp(raw);
        center = Math.max(-0.85f, Math.min(0.85f, center));

        if (raw >= center) {
            float span = Math.max(0.05f, 1f - center);
            return clamp((raw - center) / span);
        } else {
            float span = Math.max(0.05f, 1f + center);
            return clamp((raw - center) / span);
        }
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}
