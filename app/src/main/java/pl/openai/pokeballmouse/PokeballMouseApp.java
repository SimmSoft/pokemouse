package pl.openai.pokeballmouse;

import android.app.Application;

public class PokeballMouseApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        LanguagePrefs.apply(this);
        InputRouter.init(this);
        ShizukuBridge.init(this);
    }
}
