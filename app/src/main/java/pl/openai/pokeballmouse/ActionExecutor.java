package pl.openai.pokeballmouse;

import android.view.KeyEvent;

/** Executes configurable actions using Accessibility where possible and Shizuku where needed. */
public final class ActionExecutor {
    private ActionExecutor() {}

    public static void execute(ControlConfig.Action action) {
        if (action == null || action == ControlConfig.Action.NONE) return;
        CursorAccessibilityService accessibility = CursorAccessibilityService.getInstance();
        ShizukuBridge shizuku = ShizukuBridge.get();

        switch (action) {
            case BACK:
                if (accessibility != null && accessibility.performBack()) return;
                key(shizuku, KeyEvent.KEYCODE_BACK); return;
            case HOME:
                if (accessibility != null && accessibility.performHome()) return;
                key(shizuku, KeyEvent.KEYCODE_HOME); return;
            case RECENTS:
                if (accessibility != null && accessibility.performRecents()) return;
                key(shizuku, KeyEvent.KEYCODE_APP_SWITCH); return;
            case NOTIFICATIONS:
                if (accessibility != null) accessibility.performNotifications();
                return;
            case QUICK_SETTINGS:
                if (accessibility != null) accessibility.performQuickSettings();
                return;
            case DPAD_UP: key(shizuku, KeyEvent.KEYCODE_DPAD_UP); return;
            case DPAD_DOWN: key(shizuku, KeyEvent.KEYCODE_DPAD_DOWN); return;
            case DPAD_LEFT: key(shizuku, KeyEvent.KEYCODE_DPAD_LEFT); return;
            case DPAD_RIGHT: key(shizuku, KeyEvent.KEYCODE_DPAD_RIGHT); return;
            case ENTER: key(shizuku, KeyEvent.KEYCODE_DPAD_CENTER); return;
            case TOUCH_JOY_UP: tap(ControlConfig.Binding.JOY_UP); return;
            case TOUCH_JOY_DOWN: tap(ControlConfig.Binding.JOY_DOWN); return;
            case TOUCH_JOY_LEFT: tap(ControlConfig.Binding.JOY_LEFT); return;
            case TOUCH_JOY_RIGHT: tap(ControlConfig.Binding.JOY_RIGHT); return;
            case TOUCH_STICK: tap(ControlConfig.Binding.STICK_CLICK); return;
            case TOUCH_TOP: tap(ControlConfig.Binding.TOP_CLICK); return;
            default:
        }
    }

    private static void key(ShizukuBridge shizuku, int keyCode) {
        if (shizuku != null && shizuku.isReady()) shizuku.key(keyCode);
    }

    private static void tap(ControlConfig.Binding binding) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service != null) service.tapBinding(binding);
    }
}
