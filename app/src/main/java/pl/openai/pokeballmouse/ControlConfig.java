package pl.openai.pokeballmouse;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Persistent user configuration for all Poké Ball Plus control modes. */
public final class ControlConfig {
    public enum Mode { MOUSE, DPAD, TOUCH }

    public enum TypingMode { RADIAL, KEYBOARD }

    public enum Binding {
        JOY_UP("Joystick ↑"),
        JOY_DOWN("Joystick ↓"),
        JOY_LEFT("Joystick ←"),
        JOY_RIGHT("Joystick →"),
        STICK_CLICK("Klik joysticka"),
        TOP_CLICK("Górny przycisk");

        public final String label;
        Binding(String label) { this.label = label; }
    }

    public enum MotionDirection {
        LEFT("Ruch kulą ←"), RIGHT("Ruch kulą →"), UP("Ruch kulą ↑"), DOWN("Ruch kulą ↓");
        public final String label;
        MotionDirection(String label) { this.label = label; }
    }

    public enum Action {
        NONE("Brak"),
        BACK("Wstecz"),
        HOME("Ekran główny"),
        RECENTS("Ostatnie aplikacje"),
        NOTIFICATIONS("Powiadomienia"),
        QUICK_SETTINGS("Szybkie ustawienia"),
        DPAD_UP("D-pad ↑"),
        DPAD_DOWN("D-pad ↓"),
        DPAD_LEFT("D-pad ←"),
        DPAD_RIGHT("D-pad →"),
        ENTER("Enter / OK"),
        TOUCH_JOY_UP("Dotknij punktu Joystick ↑"),
        TOUCH_JOY_DOWN("Dotknij punktu Joystick ↓"),
        TOUCH_JOY_LEFT("Dotknij punktu Joystick ←"),
        TOUCH_JOY_RIGHT("Dotknij punktu Joystick →"),
        TOUCH_STICK("Dotknij punktu Klik joysticka"),
        TOUCH_TOP("Dotknij punktu Górny przycisk");

        public final String label;
        Action(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private static final String PREFS = "control_config_v2";
    private final SharedPreferences prefs;

    public ControlConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyMotionDefaults();
    }

    private void migrateLegacyMotionDefaults() {
        if (prefs.getBoolean("motion_defaults_none_v052", false)) return;
        String left = prefs.getString("motion_action_left", null);
        String right = prefs.getString("motion_action_right", null);
        String up = prefs.getString("motion_action_up", null);
        String down = prefs.getString("motion_action_down", null);
        boolean anyStored = left != null || right != null || up != null || down != null;
        boolean onlyLegacy = (left == null || Action.BACK.name().equals(left))
                && (right == null || Action.RECENTS.name().equals(right))
                && (up == null || Action.HOME.name().equals(up))
                && (down == null || Action.NOTIFICATIONS.name().equals(down));
        SharedPreferences.Editor edit = prefs.edit().putBoolean("motion_defaults_none_v052", true);
        // Previous builds could persist their old suggested defaults via Spinner callbacks.
        // Reset only that exact legacy combination; preserve any genuinely custom mapping.
        if (anyStored && onlyLegacy) {
            edit.putString("motion_action_left", Action.NONE.name())
                    .putString("motion_action_right", Action.NONE.name())
                    .putString("motion_action_up", Action.NONE.name())
                    .putString("motion_action_down", Action.NONE.name());
        }
        edit.apply();
    }

    public Mode mode() {
        try { return Mode.valueOf(prefs.getString("mode", Mode.MOUSE.name())); }
        catch (Throwable ignored) { return Mode.MOUSE; }
    }

    public void setMode(Mode mode) { prefs.edit().putString("mode", mode.name()).apply(); }


    public TypingMode typingMode() {
        try { return TypingMode.valueOf(prefs.getString("typing_mode", TypingMode.RADIAL.name())); }
        catch (Throwable ignored) { return TypingMode.RADIAL; }
    }

    public void setTypingMode(TypingMode mode) {
        prefs.edit().putString("typing_mode", mode.name()).apply();
    }

    public boolean fakeCenterEnabled() { return prefs.getBoolean("joy_fake_center_enabled", false); }
    public float fakeCenterX() { return prefs.getFloat("joy_fake_center_x", 0f); }
    public float fakeCenterY() { return prefs.getFloat("joy_fake_center_y", 0f); }

    public void setFakeCenter(float x, float y) {
        prefs.edit()
                .putBoolean("joy_fake_center_enabled", true)
                .putFloat("joy_fake_center_x", clampSigned(x))
                .putFloat("joy_fake_center_y", clampSigned(y))
                .apply();
    }

    public void clearFakeCenter() {
        prefs.edit()
                .putBoolean("joy_fake_center_enabled", false)
                .remove("joy_fake_center_x")
                .remove("joy_fake_center_y")
                .apply();
    }

    public boolean motionEnabled() { return prefs.getBoolean("motion_enabled", true); }
    public void setMotionEnabled(boolean enabled) { prefs.edit().putBoolean("motion_enabled", enabled).apply(); }

    /** High-pass acceleration threshold in approximate g units. */
    public float motionThreshold() { return Math.max(0.32f, Math.min(1.20f, prefs.getFloat("motion_threshold", 0.52f))); }
    public void setMotionThreshold(float value) {
        prefs.edit().putFloat("motion_threshold", Math.max(0.32f, Math.min(1.20f, value))).apply();
    }

    public boolean motionSwapAxes() { return prefs.getBoolean("motion_swap_axes", false); }
    public void setMotionSwapAxes(boolean value) { prefs.edit().putBoolean("motion_swap_axes", value).apply(); }
    public boolean motionInvertX() { return prefs.getBoolean("motion_invert_x", false); }
    public void setMotionInvertX(boolean value) { prefs.edit().putBoolean("motion_invert_x", value).apply(); }
    public boolean motionInvertY() { return prefs.getBoolean("motion_invert_y", false); }
    public void setMotionInvertY(boolean value) { prefs.edit().putBoolean("motion_invert_y", value).apply(); }

    public Action motionAction(MotionDirection direction) {
        String key = "motion_action_" + direction.name().toLowerCase(Locale.ROOT);
        Action fallback = Action.NONE;
        try { return Action.valueOf(prefs.getString(key, fallback.name())); }
        catch (Throwable ignored) { return fallback; }
    }

    public void setMotionAction(MotionDirection direction, Action action) {
        String key = "motion_action_" + direction.name().toLowerCase(Locale.ROOT);
        prefs.edit().putString(key, action.name()).apply();
    }

    public void setTouchPoint(Binding binding, float normalizedX, float normalizedY) {
        String base = "touch_" + binding.name().toLowerCase(Locale.ROOT);
        prefs.edit()
                .putFloat(base + "_x", clamp01(normalizedX))
                .putFloat(base + "_y", clamp01(normalizedY))
                .putBoolean(base + "_set", true)
                .apply();
    }

    public boolean hasTouchPoint(Binding binding) {
        return prefs.getBoolean("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_set", false);
    }

    public float touchX(Binding binding) {
        return prefs.getFloat("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_x", 0.5f);
    }

    public float touchY(Binding binding) {
        return prefs.getFloat("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_y", 0.5f);
    }

    public void clearTouchPoint(Binding binding) {
        String base = "touch_" + binding.name().toLowerCase(Locale.ROOT);
        prefs.edit().remove(base + "_x").remove(base + "_y").remove(base + "_set").apply();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clampSigned(float v) { return Math.max(-0.80f, Math.min(0.80f, v)); }
}
