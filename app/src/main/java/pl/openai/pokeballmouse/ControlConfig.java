package pl.openai.pokeballmouse;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Persistent control configuration, scoped to the active Poké Ball Plus profile. */
public final class ControlConfig {
    public enum Mode { MOUSE, DPAD, TOUCH }

    public enum TypingMode { RADIAL, KEYBOARD }

    public enum RadialConfirmMode { TOP, RELEASE }

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

    private String scopedKey(String base) {
        try {
            DeviceProfileStore.Profile profile = DeviceProfileStore.get().activeProfile();
            if (profile != null && profile.key != null && !profile.key.isEmpty()) {
                return "device_" + profile.key + "_" + base;
            }
        } catch (Throwable ignored) {}
        return base;
    }

    private boolean bool(String base, boolean fallback) {
        String scoped = scopedKey(base);
        if (!scoped.equals(base) && prefs.contains(scoped)) return prefs.getBoolean(scoped, fallback);
        return prefs.getBoolean(base, fallback);
    }

    private float number(String base, float fallback) {
        String scoped = scopedKey(base);
        if (!scoped.equals(base) && prefs.contains(scoped)) return prefs.getFloat(scoped, fallback);
        return prefs.getFloat(base, fallback);
    }

    private String textValue(String base, String fallback) {
        String scoped = scopedKey(base);
        if (!scoped.equals(base) && prefs.contains(scoped)) return prefs.getString(scoped, fallback);
        return prefs.getString(base, fallback);
    }

    private SharedPreferences.Editor edit() { return prefs.edit(); }

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
        try { return Mode.valueOf(textValue("mode", Mode.MOUSE.name())); }
        catch (Throwable ignored) { return Mode.MOUSE; }
    }

    public void setMode(Mode mode) { edit().putString(scopedKey("mode"), mode.name()).apply(); }


    public TypingMode typingMode() {
        try { return TypingMode.valueOf(textValue("typing_mode", TypingMode.RADIAL.name())); }
        catch (Throwable ignored) { return TypingMode.RADIAL; }
    }

    public void setTypingMode(TypingMode mode) {
        edit().putString(scopedKey("typing_mode"), mode.name()).apply();
    }

    public RadialConfirmMode radialConfirmMode() {
        try { return RadialConfirmMode.valueOf(textValue("typing_radial_confirm", RadialConfirmMode.TOP.name())); }
        catch (Throwable ignored) { return RadialConfirmMode.TOP; }
    }

    public void setRadialConfirmMode(RadialConfirmMode mode) {
        edit().putString(scopedKey("typing_radial_confirm"), mode.name()).apply();
    }

    public float typingOverlayOpacity() {
        return Math.max(0.35f, Math.min(0.95f, number("typing_overlay_opacity", 0.68f)));
    }

    public void setTypingOverlayOpacity(float value) {
        edit().putFloat(scopedKey("typing_overlay_opacity"), Math.max(0.35f, Math.min(0.95f, value))).apply();
    }

    /** Temporary/quick center override. It takes precedence over the saved device calibration. */
    public boolean fakeCenterEnabled() { return bool("joy_fake_center_enabled", false); }
    public float fakeCenterX() { return number("joy_fake_center_x", 0f); }
    public float fakeCenterY() { return number("joy_fake_center_y", 0f); }

    public void setFakeCenter(float x, float y) {
        edit()
                .putBoolean(scopedKey("joy_fake_center_enabled"), true)
                .putFloat(scopedKey("joy_fake_center_x"), clampSignedCenter(x))
                .putFloat(scopedKey("joy_fake_center_y"), clampSignedCenter(y))
                .apply();
    }

    public void clearFakeCenter() {
        edit()
                .putBoolean(scopedKey("joy_fake_center_enabled"), false)
                .remove(scopedKey("joy_fake_center_x"))
                .remove(scopedKey("joy_fake_center_y"))
                .apply();
    }

    public boolean motionEnabled() { return bool("motion_enabled", true); }
    public void setMotionEnabled(boolean enabled) { edit().putBoolean(scopedKey("motion_enabled"), enabled).apply(); }

    /** High-pass acceleration threshold in approximate g units. */
    public float motionThreshold() { return Math.max(0.32f, Math.min(1.20f, number("motion_threshold", 0.52f))); }
    public void setMotionThreshold(float value) {
        edit().putFloat(scopedKey("motion_threshold"), Math.max(0.32f, Math.min(1.20f, value))).apply();
    }

    public boolean motionSwapAxes() { return bool("motion_swap_axes", false); }
    public void setMotionSwapAxes(boolean value) { edit().putBoolean(scopedKey("motion_swap_axes"), value).apply(); }
    public boolean motionInvertX() { return bool("motion_invert_x", false); }
    public void setMotionInvertX(boolean value) { edit().putBoolean(scopedKey("motion_invert_x"), value).apply(); }
    public boolean motionInvertY() { return bool("motion_invert_y", false); }
    public void setMotionInvertY(boolean value) { edit().putBoolean(scopedKey("motion_invert_y"), value).apply(); }

    public Action motionAction(MotionDirection direction) {
        String key = "motion_action_" + direction.name().toLowerCase(Locale.ROOT);
        Action fallback = Action.NONE;
        try { return Action.valueOf(textValue(key, fallback.name())); }
        catch (Throwable ignored) { return fallback; }
    }

    public void setMotionAction(MotionDirection direction, Action action) {
        String key = "motion_action_" + direction.name().toLowerCase(Locale.ROOT);
        edit().putString(scopedKey(key), action.name()).apply();
    }


    public boolean joystickCenterCalibrated() {
        return bool("joystick_center_set", false);
    }

    public float joystickCenterX() {
        return number("joystick_center_x", 0f);
    }

    public float joystickCenterY() {
        return number("joystick_center_y", 0f);
    }

    public void setJoystickCenter(float x, float y) {
        edit()
                .putBoolean(scopedKey("joystick_center_set"), true)
                .putFloat(scopedKey("joystick_center_x"), clampSignedCenter(x))
                .putFloat(scopedKey("joystick_center_y"), clampSignedCenter(y))
                .apply();
    }

    public void clearJoystickCenter() {
        edit()
                .putBoolean(scopedKey("joystick_center_set"), false)
                .remove(scopedKey("joystick_center_x"))
                .remove(scopedKey("joystick_center_y"))
                .apply();
    }

    private static float clampSignedCenter(float value) {
        return Math.max(-0.85f, Math.min(0.85f, value));
    }

    public void setTouchPoint(Binding binding, float normalizedX, float normalizedY) {
        String base = "touch_" + binding.name().toLowerCase(Locale.ROOT);
        edit()
                .putFloat(scopedKey(base + "_x"), clamp01(normalizedX))
                .putFloat(scopedKey(base + "_y"), clamp01(normalizedY))
                .putBoolean(scopedKey(base + "_set"), true)
                .apply();
    }

    public boolean hasTouchPoint(Binding binding) {
        return bool("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_set", false);
    }

    public float touchX(Binding binding) {
        return number("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_x", 0.5f);
    }

    public float touchY(Binding binding) {
        return number("touch_" + binding.name().toLowerCase(Locale.ROOT) + "_y", 0.5f);
    }

    public void clearTouchPoint(Binding binding) {
        String base = "touch_" + binding.name().toLowerCase(Locale.ROOT);
        edit().remove(scopedKey(base + "_x")).remove(scopedKey(base + "_y")).putBoolean(scopedKey(base + "_set"), false).apply();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
