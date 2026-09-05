package pl.openai.pokeballmouse;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Two-language in-app locale preference (Polish / English). */
public final class LanguagePrefs {
    public enum Mode { PL, EN }

    private static final String PREFS = "appearance_config";
    private static final String KEY_LANGUAGE = "language";

    private LanguagePrefs() {}

    public static Mode get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_LANGUAGE, null);
        if (stored != null) {
            try { return Mode.valueOf(stored); } catch (Throwable ignored) {}
        }
        String system = Locale.getDefault().getLanguage();
        return "pl".equalsIgnoreCase(system) ? Mode.PL : Mode.EN;
    }

    public static void set(Context context, Mode mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, mode.name()).apply();
        apply(context);
    }

    @SuppressWarnings("deprecation")
    public static void apply(Context context) {
        Mode mode = get(context);
        Locale locale = mode == Mode.PL ? new Locale("pl") : Locale.ENGLISH;
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }
}
