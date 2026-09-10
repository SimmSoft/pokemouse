package pl.openai.pokeballmouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Guided per-device calibration wizard with visual step instructions. */
public final class CalibrationWizard {
    public interface Callback { void onFinished(boolean completed); }

    private static final long MOTION_HOLD_MS = 3000L;

    private final Activity activity;
    private final DeviceProfileStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private DeviceProfileStore.Profile profile;
    private AlertDialog dialog;
    private TextView stepStatus;

    private final boolean dark;
    private final int surface;
    private final int surfaceRaised;
    private final int outline;
    private final int textPrimary;
    private final int textSecondary;
    private final int accent;

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
    private float sumMotionDx, sumMotionDy, sumMotionDz, sumMotionWeight;
    private int motionSamplesCount;

    public CalibrationWizard(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.store = DeviceProfileStore.get();
        dark = ThemePrefs.isDark(activity);
        if (dark) {
            surface = Color.rgb(24, 28, 34);
            surfaceRaised = Color.rgb(31, 36, 43);
            outline = Color.rgb(55, 61, 70);
            textPrimary = Color.rgb(242, 244, 247);
            textSecondary = Color.rgb(174, 180, 190);
            accent = Color.rgb(255, 68, 80);
        } else {
            surface = Color.WHITE;
            surfaceRaised = Color.rgb(249, 250, 252);
            outline = Color.rgb(220, 224, 230);
            textPrimary = Color.rgb(31, 35, 41);
            textSecondary = Color.rgb(98, 105, 116);
            accent = Color.rgb(220, 47, 61);
        }
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
        input.setTextColor(textPrimary);
        input.setHintTextColor(textSecondary);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(roundRect(surfaceRaised, outline, 12));

        LinearLayout panel = buildPanel(
                activity.getString(R.string.calibration_title),
                activity.getString(R.string.calibration_name_message, profile.id),
                CalibrationInstructionView.Type.TABLE);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(10);
        panel.addView(input, inputLp);
        addBrand(panel);

        dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setPositiveButton(R.string.calibration_continue, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .create();
        dialog.setCancelable(false);
        dialog.setOnShowListener(d -> {
            styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) store.rename(profile.key, name);
                profile = store.activeProfile();
                dialog.dismiss();
                showStationaryIntro();
            });
        });
        dialog.show();
    }

    private void showStationaryIntro() {
        LinearLayout panel = buildPanel(
                activity.getString(R.string.calibration_stationary_title),
                activity.getString(R.string.calibration_stationary_message),
                CalibrationInstructionView.Type.TABLE);
        addBrand(panel);
        dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setPositiveButton(R.string.calibration_start, (d, w) -> runStationaryCountdown())
                .setNegativeButton(R.string.calibration_skip, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> styleDialog(dialog));
        dialog.show();
    }

    private void runStationaryCountdown() {
        InputRouter.setCalibrationMode(true);
        sumJoyX = sumJoyY = sumAx = sumAy = sumAz = sumAccelSq = 0d;
        idleSamples = 0;

        LinearLayout panel = buildPanel(
                activity.getString(R.string.calibration_stationary_title),
                activity.getString(R.string.calibration_do_not_touch),
                CalibrationInstructionView.Type.TABLE);
        stepStatus = statusText(activity.getString(R.string.calibration_countdown, 6));
        panel.addView(stepStatus);
        addBrand(panel);

        dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> styleDialog(dialog));
        dialog.show();

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
                if (stepStatus != null) stepStatus.setText(activity.getString(R.string.calibration_countdown, left));
                if (elapsed >= 6000L) {
                    if (idleSamples < 20) {
                        Toast.makeText(activity, R.string.calibration_no_data, Toast.LENGTH_SHORT).show();
                        finish(false);
                        return;
                    }
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
        final CalibrationInstructionView.Type[] visual = {
                CalibrationInstructionView.Type.JOY_UP,
                CalibrationInstructionView.Type.JOY_DOWN,
                CalibrationInstructionView.Type.JOY_LEFT,
                CalibrationInstructionView.Type.JOY_RIGHT
        };
        if (index >= 4) { startMotionStep(0, 0); return; }

        LinearLayout panel = buildPanel(
                activity.getString(title[index]),
                activity.getString(R.string.calibration_joystick_message),
                visual[index]);
        addBrand(panel);
        dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setPositiveButton(R.string.calibration_save_position, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                float x = InputRouter.rawJoyX(), y = InputRouter.rawJoyY();
                switch (index) {
                    case 0: upX=x; upY=y; break;
                    case 1: downX=x; downY=y; break;
                    case 2: leftX=x; leftY=y; break;
                    case 3: rightX=x; rightY=y; break;
                    default: break;
                }
                dialog.dismiss();
                showJoystickStep(index + 1);
            });
        });
        dialog.show();
    }

    private void startMotionStep(int directionIndex, int repeat) {
        motionDirectionIndex = directionIndex;
        motionRepeat = repeat;
        if (directionIndex >= 4) { saveCalibration(); return; }

        int[] names = {R.string.motion_detected_left, R.string.motion_detected_right,
                R.string.motion_detected_up, R.string.motion_detected_down};
        CalibrationInstructionView.Type[] visual = {
                CalibrationInstructionView.Type.MOTION_LEFT,
                CalibrationInstructionView.Type.MOTION_RIGHT,
                CalibrationInstructionView.Type.MOTION_UP,
                CalibrationInstructionView.Type.MOTION_DOWN
        };
        String direction = activity.getString(names[directionIndex]);
        LinearLayout panel = buildPanel(
                activity.getString(R.string.calibration_motion_title, direction, repeat + 1, 2),
                activity.getString(R.string.calibration_motion_message),
                visual[directionIndex]);
        stepStatus = statusText(activity.getString(R.string.calibration_motion_ready));
        panel.addView(stepStatus);
        addBrand(panel);

        dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setPositiveButton(R.string.calibration_record, null)
                .setNegativeButton(R.string.appearance_cancel, (d, w) -> finish(false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                if (stepStatus != null) stepStatus.setText(activity.getString(R.string.calibration_wait_top));
                beginMotionCapture();
            });
        });
        dialog.show();
    }

    private void beginMotionCapture() {
        motionCaptureRunning = true;
        topSeen = false;
        peakMagnitude = 0f;
        peakDx = peakDy = peakDz = 0f;
        sumMotionDx = sumMotionDy = sumMotionDz = sumMotionWeight = 0f;
        motionSamplesCount = 0;
        long timeoutStart = SystemClock.uptimeMillis();
        Runnable poll = new Runnable() {
            @Override public void run() {
                if (!motionCaptureRunning || !InputRouter.calibrationMode()) return;
                long now = SystemClock.uptimeMillis();
                if (now - timeoutStart > 15000L) { retryMotion(); return; }
                boolean top = InputRouter.topPressed();
                float ax=InputRouter.accelX(), ay=InputRouter.accelY(), az=InputRouter.accelZ();
                if (!topSeen) {
                    if (top) {
                        topSeen = true;
                        topStartMs = now;
                        baseAx=ax; baseAy=ay; baseAz=az;
                    }
                } else if (top) {
                    long held = now - topStartMs;
                    if (held < MOTION_HOLD_MS) {
                        // Keep following the stationary baseline while Top is held.
                        baseAx += (ax-baseAx)*0.22f;
                        baseAy += (ay-baseAy)*0.22f;
                        baseAz += (az-baseAz)*0.22f;
                        int seconds = Math.max(1, (int)Math.ceil((MOTION_HOLD_MS - held) / 1000.0));
                        if (stepStatus != null) {
                            stepStatus.setText(activity.getString(R.string.calibration_hold_top_countdown, seconds));
                        }
                    } else {
                        if (stepStatus != null) stepStatus.setText(activity.getString(R.string.calibration_move_now));
                        float dx=ax-baseAx, dy=ay-baseAy, dz=az-baseAz;
                        float mag=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                        if (mag > peakMagnitude) { peakMagnitude=mag; peakDx=dx; peakDy=dy; peakDz=dz; }
                        if (mag >= 0.18f) {
                            float weight = Math.min(2f, mag);
                            sumMotionDx += dx * weight;
                            sumMotionDy += dy * weight;
                            sumMotionDz += dz * weight;
                            sumMotionWeight += weight;
                            motionSamplesCount++;
                        }
                    }
                } else {
                    if (!topSeen || now - topStartMs < MOTION_HOLD_MS) {
                        retryMotion();
                        return;
                    }
                    float sampleX, sampleY, sampleZ;
                    float sampleMagnitude;
                    if (sumMotionWeight > 0.0001f && motionSamplesCount > 0) {
                        sampleX = sumMotionDx / sumMotionWeight;
                        sampleY = sumMotionDy / sumMotionWeight;
                        sampleZ = sumMotionDz / sumMotionWeight;
                        sampleMagnitude = (float)Math.sqrt(sampleX*sampleX + sampleY*sampleY + sampleZ*sampleZ);
                    } else {
                        sampleX = peakDx;
                        sampleY = peakDy;
                        sampleZ = peakDz;
                        sampleMagnitude = peakMagnitude;
                    }
                    if (sampleMagnitude < 0.22f && peakMagnitude >= 0.24f) {
                        sampleX = peakDx;
                        sampleY = peakDy;
                        sampleZ = peakDz;
                        sampleMagnitude = peakMagnitude;
                    }
                    if (sampleMagnitude < 0.22f) {
                        retryMotion();
                        return;
                    }
                    float inv = 1f / sampleMagnitude;
                    motionSamples[motionDirectionIndex][motionRepeat][0] = sampleX * inv;
                    motionSamples[motionDirectionIndex][motionRepeat][1] = sampleY * inv;
                    motionSamples[motionDirectionIndex][motionRepeat][2] = sampleZ * inv;
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

    private LinearLayout buildPanel(String titleValue, String messageValue,
                                    CalibrationInstructionView.Type visualType) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_launcher_pokeball);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        icon.setBackground(roundRect(surfaceRaised, outline, 12));
        header.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(titleValue, 20f, true, textPrimary);
        TextView message = text(messageValue, 13f, false, textSecondary);
        message.setLineSpacing(0f, 1.12f);
        message.setPadding(0, dp(3), 0, 0);
        copy.addView(title);
        copy.addView(message);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        header.addView(copy, copyLp);
        panel.addView(header);

        if (visualType != null) {
            CalibrationInstructionView visual = new CalibrationInstructionView(activity);
            visual.setType(visualType);
            LinearLayout.LayoutParams visualLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
            visualLp.topMargin = dp(10);
            panel.addView(visual, visualLp);
        }
        return panel;
    }

    private TextView statusText(String value) {
        TextView status = text(value, 14f, true, textPrimary);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(roundRect(surfaceRaised, outline, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        status.setLayoutParams(lp);
        return status;
    }

    private void addBrand(LinearLayout panel) {
        TextView brand = text(activity.getString(R.string.calibration_brand), 11f, false, textSecondary);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(14), 0, 0);
        panel.addView(brand);
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(roundRect(surface, outline, 18));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) negative.setTextColor(textSecondary);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) positive.setTextColor(accent);
    }

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        bg.setStroke(dp(1), stroke);
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
}
