package pl.openai.pokeballmouse;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

public final class ThemePrefs {
    public enum Mode { SYSTEM, LIGHT, DARK }

    private static final String PREFS = "appearance_config";
    private static final String KEY_THEME = "theme";

    private ThemePrefs() {}

    public static Mode get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try { return Mode.valueOf(prefs.getString(KEY_THEME, Mode.SYSTEM.name())); }
        catch (Throwable ignored) { return Mode.SYSTEM; }
    }

    public static void set(Context context, Mode mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, mode.name()).apply();
    }

    public static boolean isDark(Context context) {
        Mode mode = get(context);
        if (mode == Mode.DARK) return true;
        if (mode == Mode.LIGHT) return false;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int resolveTheme(Context context) {
        return isDark(context) ? R.style.AppTheme_Dark : R.style.AppTheme_Light;
    }
}
