package pl.openai.pokeballmouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Guided per-device calibration wizard. */
public final class CalibrationWizard {
    public interface Callback { void onFinished(boolean completed); }

    private final Activity activity;
    private final DeviceProfileStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private DeviceProfileStore.Profile profile;
    private AlertDialog dialog;

    private double sumJoyX, sumJoyY, sumAx, sumAy, sumAz, sumAccelSq;
    private int idleSamples;
    private float centerX, centerY;
    private float upX, upY, downX, downY, leftX, leftY, rightX, rightY;
    private final float[][][] motionSamples = new float[4][2][3];
    private int motionDirectionIndex;
    private int motionRepeat;

    private boolean motionCaptureRunning;
    private boolean topSeen;
    private long topStartMs;
    private float baseAx, baseAy, baseAz;
    private float peakDx, peakDy, peakDz, peakMagnitude;

    public CalibrationWizard(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.store = DeviceProfileStore.get();
    }

    public void start(DeviceProfileStore.Profile profile) {
        if (profile == null || !PokeballService.isConnected()) {
            Toast.makeText(activity, activity.getString(R.string.calibration_connect_first), Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onFinished(false);
            return;
        }
        this.profile = profile;
        showNameStep();
    }

    private void showNameStep() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(profile.name);
        input.setSelectAllOnFocus(true);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.calibration_title)
                .setMessage(activity.getString(R.string.calibration_name_message, profile.id))
                .setView(input)
                .setPositiveButton(R.string.calibration_continue, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) store.rename(profile.key, name);
            profile = store.activeProfile();
            dialog.dismiss();
            showStationaryIntro();
        }));
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showStationaryIntro() {
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.calibration_stationary_title)
                .setMessage(R.string.calibration_stationary_message)
                .setPositiveButton(R.string.calibration_start, (d, w) -> runStationaryCountdown())
                .setNegativeButton(R.string.calibration_skip, (d, w) -> finish(false))
                .setCancelable(false)
                .show();
    }

    private void runStationaryCountdown() {
        InputRouter.setCalibrationMode(true);
        sumJoyX = sumJoyY = sumAx = sumAy = sumAz = sumAccelSq = 0d;
        idleSamples = 0;
        TextView status = new TextView(activity);
        status.setTextSize(20f);
        status.setPadding(48, 36, 48, 36);
        status.setText(R.string.calibration_do_not_touch);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.calibration_stationary_title)
                .setView(status)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .show();
        long start = SystemClock.uptimeMillis();
        Runnable sampler = new Runnable() {
            @Override public void run() {
                if (!InputRouter.calibrationMode()) return;
                float jx = InputRouter.rawJoyX(), jy = InputRouter.rawJoyY();
                float ax = InputRouter.accelX(), ay = InputRouter.accelY(), az = InputRouter.accelZ();
                if (finite(ax) && finite(ay) && finite(az)) {
                    sumJoyX += jx; sumJoyY += jy;
                    sumAx += ax; sumAy += ay; sumAz += az;
                    sumAccelSq += ax * ax + ay * ay + az * az;
                    idleSamples++;
                }
                long elapsed = SystemClock.uptimeMillis() - start;
                int left = Math.max(0, 6 - (int)(elapsed / 1000L));
                status.setText(activity.getString(R.string.calibration_countdown, left));
                if (elapsed >= 6000L) {
                    if (idleSamples < 20) { Toast.makeText(activity, R.string.calibration_no_data, Toast.LENGTH_SHORT).show(); finish(false); return; }
                    centerX = (float)(sumJoyX / idleSamples);
                    centerY = (float)(sumJoyY / idleSamples);
                    dialog.dismiss();
                    showJoystickStep(0);
                } else handler.postDelayed(this, 20L);
            }
        };
        handler.post(sampler);
    }

    private void showJoystickStep(int index) {
        final int[] title = {R.string.calibration_joy_up, R.string.calibration_joy_down,
                R.string.calibration_joy_left, R.string.calibration_joy_right};
        if (index >= 4) { startMotionStep(0, 0); return; }
        dialog = new AlertDialog.Builder(activity)
                .setTitle(title[index])
                .setMessage(R.string.calibration_joystick_message)
                .setPositiveButton(R.string.calibration_save_position, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            float x = InputRouter.rawJoyX(), y = InputRouter.rawJoyY();
            switch (index) {
                case 0: upX=x; upY=y; break; case 1: downX=x; downY=y; break;
                case 2: leftX=x; leftY=y; break; case 3: rightX=x; rightY=y; break;
            }
            dialog.dismiss();
            showJoystickStep(index + 1);
        }));
        dialog.show();
    }

    private void startMotionStep(int directionIndex, int repeat) {
        motionDirectionIndex = directionIndex;
        motionRepeat = repeat;
        if (directionIndex >= 4) { saveCalibration(); return; }
        int[] names = {R.string.motion_detected_left, R.string.motion_detected_right,
                R.string.motion_detected_up, R.string.motion_detected_down};
        String direction = activity.getString(names[directionIndex]);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.calibration_motion_title, direction, repeat + 1, 2))
                .setMessage(R.string.calibration_motion_message)
                .setPositiveButton(R.string.calibration_record, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.setMessage(activity.getString(R.string.calibration_wait_top));
            beginMotionCapture();
        }));
        dialog.show();
    }

    private void beginMotionCapture() {
        motionCaptureRunning = true;
        topSeen = false;
        peakMagnitude = 0f;
        long timeoutStart = SystemClock.uptimeMillis();
        Runnable poll = new Runnable() {
            @Override public void run() {
                if (!motionCaptureRunning || !InputRouter.calibrationMode()) return;
                long now = SystemClock.uptimeMillis();
                if (now - timeoutStart > 6000L) { retryMotion(); return; }
                boolean top = InputRouter.topPressed();
                float ax=InputRouter.accelX(), ay=InputRouter.accelY(), az=InputRouter.accelZ();
                if (!topSeen) {
                    if (top) {
                        topSeen = true; topStartMs = now; baseAx=ax; baseAy=ay; baseAz=az;
                        dialog.setMessage(activity.getString(R.string.calibration_move_now));
                    }
                } else if (top) {
                    if (now - topStartMs < 100L) {
                        baseAx += (ax-baseAx)*0.5f; baseAy += (ay-baseAy)*0.5f; baseAz += (az-baseAz)*0.5f;
                    } else {
                        float dx=ax-baseAx, dy=ay-baseAy, dz=az-baseAz;
                        float mag=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                        if (mag > peakMagnitude) { peakMagnitude=mag; peakDx=dx; peakDy=dy; peakDz=dz; }
                    }
                } else {
                    if (peakMagnitude < 0.24f) { retryMotion(); return; }
                    float inv=1f/peakMagnitude;
                    motionSamples[motionDirectionIndex][motionRepeat][0]=peakDx*inv;
                    motionSamples[motionDirectionIndex][motionRepeat][1]=peakDy*inv;
                    motionSamples[motionDirectionIndex][motionRepeat][2]=peakDz*inv;
                    motionCaptureRunning=false;
                    dialog.dismiss();
                    int nextRepeat=motionRepeat+1, nextDirection=motionDirectionIndex;
                    if (nextRepeat>=2) { nextRepeat=0; nextDirection++; }
                    startMotionStep(nextDirection,nextRepeat);
                    return;
                }
                handler.postDelayed(this, 16L);
            }
        };
        handler.post(poll);
    }

    private void retryMotion() {
        motionCaptureRunning=false;
        Toast.makeText(activity, R.string.calibration_motion_retry, Toast.LENGTH_SHORT).show();
        if (dialog != null) dialog.dismiss();
        startMotionStep(motionDirectionIndex, motionRepeat);
    }

    private void saveCalibration() {
        float minX=Math.min(leftX,rightX), maxX=Math.max(leftX,rightX);
        float minY=Math.min(upY,downY), maxY=Math.max(upY,downY);
        double meanAccelSq=sumAccelSq/Math.max(1,idleSamples);
        double mx=sumAx/Math.max(1,idleSamples), my=sumAy/Math.max(1,idleSamples), mz=sumAz/Math.max(1,idleSamples);
        float noise=(float)Math.sqrt(Math.max(0d, meanAccelSq-(mx*mx+my*my+mz*mz)));
        store.saveJoystickCalibration(profile.key,centerX,centerY,minX,maxX,minY,maxY,Math.max(0.05f,noise*3f));
        float[][] averaged=new float[4][3];
        for(int d=0;d<4;d++) {
            averaged[d][0]=motionSamples[d][0][0]+motionSamples[d][1][0];
            averaged[d][1]=motionSamples[d][0][1]+motionSamples[d][1][1];
            averaged[d][2]=motionSamples[d][0][2]+motionSamples[d][1][2];
        }
        store.saveMotionTemplates(profile.key,averaged);
        store.markPrompted(profile.key,true);
        InputRouter.setCalibrationMode(false);
        InputRouter.setActiveDevice(profile.address, profile.name);
        Toast.makeText(activity,R.string.calibration_complete,Toast.LENGTH_LONG).show();
        if(callback!=null) callback.onFinished(true);
    }

    private void finish(boolean completed) {
        motionCaptureRunning=false;
        handler.removeCallbacksAndMessages(null);
        InputRouter.setCalibrationMode(false);
        if (profile != null) store.markPrompted(profile.key,true);
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if(callback!=null) callback.onFinished(completed);
    }

    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
}
