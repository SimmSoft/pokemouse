package pl.openai.pokeballmouse;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Phone-only haptic used to acknowledge that a Poké Ball Plus was found. */
public final class PhoneFeedback {
    private PhoneFeedback() {}

    public static boolean detectedVibration(Context context) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return false;
        try {
            // Noticeably stronger than the previous 30 ms / amplitude 45 pulse,
            // but still short enough to feel like a connection acknowledgement.
            vibrator.vibrate(VibrationEffect.createOneShot(75L, 135));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            return manager != null ? manager.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
