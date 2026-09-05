package pl.openai.pokeballmouse;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public final class PhoneFeedback {
    private PhoneFeedback() {}

    public static boolean lightDetectedVibration(Context context) {
        return vibrate(context, 30L, 45);
    }

    public static boolean testVibration(Context context) {
        return vibrate(context, 90L, 105);
    }

    public static boolean testSound() {
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 65);
            boolean started = tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
            if (!started) {
                tone.release();
                return false;
            }
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 260L);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean vibrate(Context context, long durationMs, int amplitude) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return false;
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
            return true;
        } catch (RuntimeException | SecurityException ex) {
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
