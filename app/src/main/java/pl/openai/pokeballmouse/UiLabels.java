package pl.openai.pokeballmouse;

import android.content.Context;

/** Localized labels for persistent enum values. */
public final class UiLabels {
    private UiLabels() {}

    public static String binding(Context c, ControlConfig.Binding b) {
        switch (b) {
            case JOY_UP: return c.getString(R.string.binding_joy_up);
            case JOY_DOWN: return c.getString(R.string.binding_joy_down);
            case JOY_LEFT: return c.getString(R.string.binding_joy_left);
            case JOY_RIGHT: return c.getString(R.string.binding_joy_right);
            case STICK_CLICK: return c.getString(R.string.binding_stick_click);
            case TOP_CLICK: return c.getString(R.string.binding_top_click);
            default: return b.name();
        }
    }

    public static String direction(Context c, ControlConfig.MotionDirection d) {
        switch (d) {
            case LEFT: return c.getString(R.string.motion_left);
            case RIGHT: return c.getString(R.string.motion_right);
            case UP: return c.getString(R.string.motion_up);
            case DOWN: return c.getString(R.string.motion_down);
            default: return d.name();
        }
    }

    public static String action(Context c, ControlConfig.Action a) {
        switch (a) {
            case NONE: return c.getString(R.string.action_none);
            case BACK: return c.getString(R.string.action_back);
            case HOME: return c.getString(R.string.action_home);
            case RECENTS: return c.getString(R.string.action_recents);
            case NOTIFICATIONS: return c.getString(R.string.action_notifications);
            case QUICK_SETTINGS: return c.getString(R.string.action_quick_settings);
            case DPAD_UP: return c.getString(R.string.action_dpad_up);
            case DPAD_DOWN: return c.getString(R.string.action_dpad_down);
            case DPAD_LEFT: return c.getString(R.string.action_dpad_left);
            case DPAD_RIGHT: return c.getString(R.string.action_dpad_right);
            case ENTER: return c.getString(R.string.action_enter);
            case TOUCH_JOY_UP: return c.getString(R.string.action_touch_joy_up);
            case TOUCH_JOY_DOWN: return c.getString(R.string.action_touch_joy_down);
            case TOUCH_JOY_LEFT: return c.getString(R.string.action_touch_joy_left);
            case TOUCH_JOY_RIGHT: return c.getString(R.string.action_touch_joy_right);
            case TOUCH_STICK: return c.getString(R.string.action_touch_stick);
            case TOUCH_TOP: return c.getString(R.string.action_touch_top);
            default: return a.name();
        }
    }

    public static String theme(Context c, ThemePrefs.Mode mode) {
        switch (mode) {
            case SYSTEM: return c.getString(R.string.theme_system);
            case LIGHT: return c.getString(R.string.theme_light);
            case DARK: return c.getString(R.string.theme_dark);
            default: return mode.name();
        }
    }

    public static String language(Context c, LanguagePrefs.Mode mode) {
        return mode == LanguagePrefs.Mode.PL ? c.getString(R.string.language_polish)
                : c.getString(R.string.language_english);
    }
}
