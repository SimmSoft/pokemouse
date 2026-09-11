package pl.openai.pokeballmouse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String EXTRA_OPEN_PROFILE = "open_profile";
    private static final int PERMISSIONS_REQUEST = 4100;

    private static final class StatusRow {
        final ImageView icon;
        final TextView status;
        final Button button;
        StatusRow(ImageView icon, TextView status, Button button) { this.icon = icon; this.status = status; this.button = button; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EnumMap<ControlConfig.Binding, Button> mappingButtons =
            new EnumMap<>(ControlConfig.Binding.class);

    private StatusRow bluetoothRow;
    private StatusRow locationRow;
    private StatusRow accessibilityRow;
    private StatusRow shizukuRow;
    private TextView pokeballStatus;
    private TextView batteryStatus;
    private BatteryLevelView batteryIcon;
    private ConnectionOrbView connectionOrb;
    private TextView diagnosticsJoystick;
    private TextView joystickCenterStatus;
    private TextView profileStatus;
    private Button calibrationButton;
    private Button connectionActionButton;
    private String offeredCalibrationAddress;
    private CalibrationWizard calibrationWizard;
    private View batteryRow;
    private JoystickDiagnosticView joystickDiagnosticView;
    private TextView sensitivityText;
    private TextView motionDetected;
    private TextView motionSensors;
    private LinearLayout connectedSettingsContainer;
    private View touchCard;
    private PokeballDiagnosticView pokeballDiagnosticView;
    private boolean pendingConnect;
    private boolean pendingBluetoothControl;
    private ControlConfig config;
    private String renderedProfileKey;

    private boolean dark;
    private int background;
    private int surface;
    private int surfaceRaised;
    private int outline;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private int accentSoft;
    private int success;
    private int warning;
    private int danger;

    @Override protected void onCreate(Bundle savedInstanceState) {
        LanguagePrefs.apply(this);
        setTheme(ThemePrefs.resolveTheme(this));
        super.onCreate(savedInstanceState);

        InputRouter.init(this);
        ShizukuBridge.init(this);
        config = new ControlConfig(this);
        DeviceProfileStore.Profile startupProfile = DeviceProfileStore.get().activeProfile();
        renderedProfileKey = startupProfile != null ? startupProfile.key : null;

        initPalette();
        applyWindowPalette();
        buildUi();
        InputRouter.setStateListener((top, stick) -> runOnUiThread(this::updateButtonDiagnostics));
        updateButtonDiagnostics();
        requestRuntimePermissions();
        handler.post(statusUpdater);
        handler.post(telemetryUpdater);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_PROFILE, false)) {
            handler.postDelayed(this::showActiveProfileDialog, 350L);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_PROFILE, false)) {
            handler.postDelayed(this::showActiveProfileDialog, 150L);
        }
    }

    private void initPalette() {
        dark = ThemePrefs.isDark(this);
        if (dark) {
            background = Color.rgb(14, 17, 22);
            surface = Color.rgb(24, 28, 34);
            surfaceRaised = Color.rgb(31, 36, 43);
            outline = Color.rgb(55, 61, 70);
            textPrimary = Color.rgb(242, 244, 247);
            textSecondary = Color.rgb(174, 180, 190);
            accent = Color.rgb(255, 68, 80);
            accentSoft = Color.rgb(71, 29, 35);
            success = Color.rgb(70, 218, 112);
            warning = Color.rgb(244, 190, 83);
            danger = Color.rgb(255, 76, 87);
        } else {
            background = Color.rgb(245, 246, 248);
            surface = Color.WHITE;
            surfaceRaised = Color.rgb(249, 250, 252);
            outline = Color.rgb(220, 224, 230);
            textPrimary = Color.rgb(31, 35, 41);
            textSecondary = Color.rgb(98, 105, 116);
            accent = Color.rgb(220, 47, 61);
            accentSoft = Color.rgb(253, 232, 235);
            success = Color.rgb(32, 128, 69);
            warning = Color.rgb(154, 103, 0);
            danger = Color.rgb(190, 52, 52);
        }
    }

    private void applyWindowPalette() {
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Android 15+ enforces edge-to-edge for modern targets. Apply real system insets
        // instead of relying on a fixed top padding, so the header never sits under the status bar.
        final int sidePadding = dp(16);
        final int baseTopPadding = dp(14);
        final int baseBottomPadding = dp(32);
        root.setPadding(sidePadding, baseTopPadding, sidePadding, baseBottomPadding);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(sidePadding, baseTopPadding + topInset, sidePadding,
                    baseBottomPadding + bottomInset);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        addHeader(root);
        addConnectionCard(root);

        connectedSettingsContainer = new LinearLayout(this);
        connectedSettingsContainer.setOrientation(LinearLayout.VERTICAL);
        connectedSettingsContainer.setVisibility(View.GONE);
        root.addView(connectedSettingsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addDiagnosticsCard(connectedSettingsContainer);
        addModeCard(connectedSettingsContainer);
        addTypingCard(connectedSettingsContainer);
        addTouchCard(connectedSettingsContainer);
        addMotionCard(connectedSettingsContainer);
        updateModeSpecificVisibility();
        addAppFooter(root);
        setContentView(scroll);
        root.requestApplyInsets();
    }

    private void addHeader(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, dp(2), dp(18));

        ImageView icon = imageViewNoTint(R.drawable.ic_launcher_pokeball, 42);
        row.addView(icon, fixed(dp(50), dp(50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        copy.addView(text(getString(R.string.app_name), 25, true, textPrimary));
        TextView subtitle = text(getString(R.string.header_subtitle), 14, false, textSecondary);
        subtitle.setPadding(0, dp(3), 0, 0);
        copy.addView(subtitle);
        row.addView(copy, copyLp);

        ImageButton appearance = new ImageButton(this);
        appearance.setImageResource(R.drawable.ic_palette);
        appearance.setImageTintList(ColorStateList.valueOf(textPrimary));
        appearance.setBackground(roundRect(surface, outline, 12));
        appearance.setPadding(dp(10), dp(10), dp(10), dp(10));
        appearance.setContentDescription(getString(R.string.appearance_title));
        appearance.setOnClickListener(v -> showAppearanceDialog());
        LinearLayout.LayoutParams appearanceLp = fixed(dp(44), dp(44));
        appearanceLp.leftMargin = dp(8);
        row.addView(appearance, appearanceLp);

        root.addView(row);
    }

    private void addAppFooter(LinearLayout root) {
        TextView brand = text(getString(R.string.appearance_brand), 11, false, textSecondary);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(18), 0, dp(2));
        brand.setAlpha(0.78f);
        root.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addConnectionCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_link,
                getString(R.string.connection_title), getString(R.string.connection_subtitle));

        bluetoothRow = addActionRow(card, R.drawable.ic_bluetooth,
                getString(R.string.bluetooth_title), getString(R.string.bluetooth_desc),
                getString(R.string.action_settings), v -> openBluetoothControl());
        addDivider(card);

        locationRow = addActionRow(card, R.drawable.ic_location,
                getString(R.string.location_title), getString(R.string.location_desc),
                getString(R.string.action_settings),
                v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        addDivider(card);

        accessibilityRow = addActionRow(card, R.drawable.ic_accessibility,
                getString(R.string.accessibility_title), getString(R.string.accessibility_desc),
                getString(R.string.action_enable),
                v -> enableAccessibility());
        addDivider(card);

        shizukuRow = addActionRow(card, R.drawable.ic_link,
                getString(R.string.shizuku_title), getString(R.string.shizuku_desc),
                getString(R.string.action_settings), v -> {
                    ShizukuBridge bridge = ShizukuBridge.get();
                    if (bridge != null) bridge.requestPermissionAndBind();
                });

        addDivider(card);

        connectionOrb = new ConnectionOrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        orbLp.topMargin = dp(2);
        card.addView(connectionOrb, orbLp);

        pokeballStatus = text(getString(R.string.status_disconnected), 16, true, textSecondary);
        pokeballStatus.setGravity(Gravity.CENTER);
        card.addView(pokeballStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout battery = new LinearLayout(this);
        battery.setOrientation(LinearLayout.HORIZONTAL);
        battery.setGravity(Gravity.CENTER);
        battery.setPadding(0, dp(5), 0, dp(9));
        battery.setVisibility(View.GONE);
        batteryRow = battery;
        batteryIcon = new BatteryLevelView(this);
        batteryIcon.setColors(textSecondary, success, warning, danger);
        batteryIcon.setLevel(-1);
        battery.addView(batteryIcon, fixed(dp(31), dp(20)));
        batteryStatus = text(getString(R.string.battery_label) + "  " + getString(R.string.battery_unknown),
                13, false, textSecondary);
        batteryStatus.setPadding(dp(5), 0, 0, 0);
        battery.addView(batteryStatus);
        card.addView(battery);

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.VERTICAL);
        profileRow.setGravity(Gravity.CENTER_HORIZONTAL);
        profileRow.setPadding(0, dp(4), 0, dp(10));
        profileRow.setOnClickListener(v -> showActiveProfileDialog());
        profileStatus = text("", 12, false, textPrimary);
        profileStatus.setGravity(Gravity.CENTER);
        profileStatus.setMaxLines(3);
        profileStatus.setPadding(dp(6), dp(4), dp(6), dp(6));
        profileStatus.setOnClickListener(v -> showActiveProfileDialog());
        profileStatus.setTag(profileRow);
        LinearLayout.LayoutParams profileStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        profileStatusLp.gravity = Gravity.CENTER_HORIZONTAL;
        profileRow.addView(profileStatus, profileStatusLp);
        calibrationButton = secondaryButton(getString(R.string.profile_calibrate_button), 0, v -> {
            DeviceProfileStore.Profile active = DeviceProfileStore.get().activeProfile();
            if (active != null) startCalibration(active);
        });
        calibrationButton.setTextSize(12);
        LinearLayout.LayoutParams calibrationLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        calibrationLp.gravity = Gravity.CENTER_HORIZONTAL;
        calibrationLp.topMargin = dp(2);
        profileRow.addView(calibrationButton, calibrationLp);
        profileRow.setVisibility(View.GONE);
        card.addView(profileRow);

        connectionActionButton = primaryButton(
                getString(R.string.action_connect), 0, v -> connectPokeball());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.topMargin = dp(2);
        card.addView(connectionActionButton, actionLp);
        updateConnectionActionButton(PokeballService.phase());
    }

    private void addModeCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_mouse,
                getString(R.string.mode_title), getString(R.string.mode_subtitle));
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        addModeRadio(modes, getString(R.string.mode_mouse) + "\n" + getString(R.string.mode_mouse_desc), ControlConfig.Mode.MOUSE);
        addModeRadio(modes, getString(R.string.mode_dpad) + "\n" + getString(R.string.mode_dpad_desc), ControlConfig.Mode.DPAD);
        addModeRadio(modes, getString(R.string.mode_touch) + "\n" + getString(R.string.mode_touch_desc), ControlConfig.Mode.TOUCH);
        card.addView(modes);
    }

    private void addTypingCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_keyboard,
                getString(R.string.typing_title), getString(R.string.typing_subtitle));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addTypingRadio(group, getString(R.string.typing_radial) + "\n" + getString(R.string.typing_radial_desc),
                ControlConfig.TypingMode.RADIAL);
        addTypingRadio(group, getString(R.string.typing_keyboard) + "\n" + getString(R.string.typing_keyboard_desc),
                ControlConfig.TypingMode.KEYBOARD);
        card.addView(group);

        TextView shortcuts = bodyText(getString(R.string.typing_shortcuts));
        shortcuts.setPadding(0, dp(8), 0, 0);
        card.addView(shortcuts);
    }

    private void addTouchCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_touch,
                getString(R.string.tap_title), getString(R.string.tap_subtitle));
        touchCard = card;
        card.addView(bodyText(getString(R.string.tap_note)));

        for (ControlConfig.Binding binding : ControlConfig.Binding.values()) {
            Button b = mappingButton(binding);
            mappingButtons.put(binding, b);
            card.addView(b);
        }

        Button clear = secondaryButton(getString(R.string.tap_clear_all), R.drawable.ic_delete, v -> {
            for (ControlConfig.Binding b : ControlConfig.Binding.values()) config.clearTouchPoint(b);
            refreshMappingButtons();
            Toast.makeText(this, getString(R.string.tap_cleared), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        clear.setLayoutParams(lp);
        card.addView(clear);
    }

    private void addMotionCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_motion,
                getString(R.string.motion_title), getString(R.string.motion_subtitle));

        Switch motionEnabled = new Switch(this);
        motionEnabled.setText(getString(R.string.motion_enable));
        motionEnabled.setTextColor(textPrimary);
        motionEnabled.setTextSize(15);
        motionEnabled.setChecked(config.motionEnabled());
        motionEnabled.setPadding(0, dp(2), 0, dp(10));
        motionEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> config.setMotionEnabled(isChecked));
        card.addView(motionEnabled);

        TextView liveLabel = smallLabel(getString(R.string.motion_live_title));
        liveLabel.setPadding(0, dp(4), 0, dp(5));
        card.addView(liveLabel);

        motionDetected = text(getString(R.string.motion_detected_none), 15, true, textPrimary);
        motionDetected.setPadding(dp(12), dp(10), dp(12), dp(10));
        motionDetected.setBackground(roundRect(surfaceRaised, outline, 12));
        card.addView(motionDetected);

        motionSensors = diagnosticBox();
        LinearLayout.LayoutParams motionSensorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        motionSensorLp.topMargin = dp(8);
        motionSensors.setLayoutParams(motionSensorLp);
        card.addView(motionSensors);

        sensitivityText = text("", 13, false, textSecondary);
        sensitivityText.setPadding(0, dp(10), 0, 0);
        card.addView(sensitivityText);

        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(88);
        sensitivity.setProgress(Math.round((config.motionThreshold() - 0.32f) * 100f));
        sensitivity.setProgressTintList(ColorStateList.valueOf(accent));
        sensitivity.setThumbTintList(ColorStateList.valueOf(accent));
        updateSensitivityLabel(config.motionThreshold());
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float threshold = 0.32f + progress / 100f;
                config.setMotionThreshold(threshold);
                updateSensitivityLabel(threshold);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sensitivity);

        TextView advancedToggle = text(getString(R.string.advanced_settings), 14, true, textPrimary);
        advancedToggle.setPadding(dp(2), dp(8), dp(2), dp(8));
        advancedToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        advancedToggle.setCompoundDrawableTintList(ColorStateList.valueOf(textSecondary));
        card.addView(advancedToggle);

        LinearLayout axes = new LinearLayout(this);
        axes.setOrientation(LinearLayout.VERTICAL);
        axes.setPadding(dp(8), 0, 0, dp(8));
        axes.addView(check(getString(R.string.motion_swap_axes), config.motionSwapAxes(),
                (buttonView, checked) -> config.setMotionSwapAxes(checked)));
        axes.addView(check(getString(R.string.motion_invert_x), config.motionInvertX(),
                (buttonView, checked) -> config.setMotionInvertX(checked)));
        axes.addView(check(getString(R.string.motion_invert_y), config.motionInvertY(),
                (buttonView, checked) -> config.setMotionInvertY(checked)));
        axes.setVisibility(View.GONE);
        axes.setAlpha(0f);
        card.addView(axes);
        advancedToggle.setOnClickListener(v -> {
            boolean show = axes.getVisibility() != View.VISIBLE;
            if (show) {
                axes.setVisibility(View.VISIBLE);
                axes.animate().alpha(1f).setDuration(140L).start();
                advancedToggle.setText(getString(R.string.advanced_settings_hide));
            } else {
                axes.animate().alpha(0f).setDuration(120L).withEndAction(() -> axes.setVisibility(View.GONE)).start();
                advancedToggle.setText(getString(R.string.advanced_settings));
            }
        });

        TextView actionsLabel = smallLabel(getString(R.string.motion_actions_label));
        actionsLabel.setPadding(0, dp(6), 0, dp(4));
        card.addView(actionsLabel);

        for (ControlConfig.MotionDirection direction : ControlConfig.MotionDirection.values()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView label = text(UiLabels.direction(this, direction), 14, false, textPrimary);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
            row.addView(actionSpinner(direction), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f));
            card.addView(row);
        }

        TextView note = bodyText(getString(R.string.motion_note));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
    }

    private void showAppearanceDialog() {
        final ThemePrefs.Mode[] themes = ThemePrefs.Mode.values();
        final LanguagePrefs.Mode[] languages = LanguagePrefs.Mode.values();
        final ThemePrefs.Mode initialTheme = ThemePrefs.get(this);
        final LanguagePrefs.Mode initialLanguage = LanguagePrefs.get(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView paletteIcon = iconView(R.drawable.ic_palette, accent, 22);
        paletteIcon.setBackground(roundRect(surfaceRaised, outline, 12));
        paletteIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        header.addView(paletteIcon, fixed(dp(42), dp(42)));

        LinearLayout headerCopy = new LinearLayout(this);
        headerCopy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.appearance_title), 20, true, textPrimary);
        TextView subtitle = bodyText(getString(R.string.appearance_subtitle));
        subtitle.setPadding(0, dp(2), 0, 0);
        headerCopy.addView(title);
        headerCopy.addView(subtitle);
        LinearLayout.LayoutParams headerCopyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerCopyLp.leftMargin = dp(12);
        header.addView(headerCopy, headerCopyLp);
        panel.addView(header);

        TextView themeLabel = smallLabel(getString(R.string.theme_label));
        themeLabel.setPadding(0, dp(18), 0, dp(6));
        panel.addView(themeLabel);
        String[] themeLabels = new String[themes.length];
        for (int i = 0; i < themes.length; i++) themeLabels[i] = UiLabels.theme(this, themes[i]);
        RadioGroup themeGroup = appearanceChoiceGroup(themeLabels, initialTheme.ordinal());
        panel.addView(themeGroup);

        TextView languageLabel = smallLabel(getString(R.string.language_label));
        languageLabel.setPadding(0, dp(16), 0, dp(6));
        panel.addView(languageLabel);
        String[] languageLabels = new String[languages.length];
        for (int i = 0; i < languages.length; i++) languageLabels[i] = UiLabels.language(this, languages[i]);
        RadioGroup languageGroup = appearanceChoiceGroup(languageLabels, initialLanguage.ordinal());
        panel.addView(languageGroup);

        TextView brand = text(getString(R.string.appearance_brand), 12, false, textSecondary);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(18), 0, dp(2));
        panel.addView(brand);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .setNegativeButton(getString(R.string.appearance_cancel), null)
                .setPositiveButton(getString(R.string.appearance_save), null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(roundRect(surface, outline, 18));
            }
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancel != null) cancel.setTextColor(textSecondary);
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (save != null) {
                save.setTextColor(accent);
                save.setOnClickListener(v -> {
                    int themeIndex = checkedIndex(themeGroup);
                    int languageIndex = checkedIndex(languageGroup);
                    ThemePrefs.Mode selectedTheme = themes[Math.max(0, Math.min(themes.length - 1, themeIndex))];
                    LanguagePrefs.Mode selectedLanguage = languages[Math.max(0, Math.min(languages.length - 1, languageIndex))];
                    boolean changed = selectedTheme != initialTheme || selectedLanguage != initialLanguage;
                    ThemePrefs.set(MainActivity.this, selectedTheme);
                    LanguagePrefs.set(MainActivity.this, selectedLanguage);
                    dialog.dismiss();
                    if (changed) recreate();
                });
            }
        });
        dialog.show();
    }

    private RadioGroup appearanceChoiceGroup(String[] labels, int selectedIndex) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(dp(10), dp(6), dp(10), dp(6));
        group.setBackground(roundRect(surfaceRaised, outline, 13));
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(labels[i]);
            option.setTextColor(textPrimary);
            option.setTextSize(15);
            option.setButtonTintList(radioTint());
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(dp(44));
            option.setPadding(dp(2), 0, dp(4), 0);
            group.addView(option, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == selectedIndex) option.setChecked(true);
        }
        return group;
    }

    private int checkedIndex(RadioGroup group) {
        int checkedId = group.getCheckedRadioButtonId();
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == checkedId) return i;
        }
        return 0;
    }

    private LinearLayout spinnerRow(String labelText, Spinner spinner) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView label = text(labelText, 15, true, textPrimary);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f));
        return row;
    }

    private Spinner themeSpinner() {
        Spinner spinner = new Spinner(this);
        ThemePrefs.Mode[] themes = ThemePrefs.Mode.values();
        String[] labels = new String[themes.length];
        for (int i=0;i<themes.length;i++) labels[i] = UiLabels.theme(this, themes[i]);
        spinner.setAdapter(spinnerAdapter(labels, 14));
        spinner.setSelection(ThemePrefs.get(this).ordinal());
        spinner.setPopupBackgroundDrawable(roundRect(surfaceRaised, outline, 12));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ThemePrefs.Mode selected = themes[position];
                if (selected == ThemePrefs.get(MainActivity.this)) return;
                ThemePrefs.set(MainActivity.this, selected);
                recreate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return spinner;
    }

    private Spinner languageSpinner() {
        Spinner spinner = new Spinner(this);
        LanguagePrefs.Mode[] languages = LanguagePrefs.Mode.values();
        String[] labels = new String[languages.length];
        for (int i=0;i<languages.length;i++) labels[i] = UiLabels.language(this, languages[i]);
        spinner.setAdapter(spinnerAdapter(labels, 14));
        spinner.setSelection(LanguagePrefs.get(this).ordinal());
        spinner.setPopupBackgroundDrawable(roundRect(surfaceRaised, outline, 12));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LanguagePrefs.Mode selected = languages[position];
                if (selected == LanguagePrefs.get(MainActivity.this)) return;
                LanguagePrefs.set(MainActivity.this, selected);
                recreate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return spinner;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] labels, int textSize) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(textPrimary);
                    ((TextView) v).setTextSize(textSize);
                }
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(textPrimary);
                    v.setBackgroundColor(surfaceRaised);
                    v.setPadding(dp(12), dp(10), dp(12), dp(10));
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void addDiagnosticsCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_diagnostics,
                getString(R.string.diagnostics_title), getString(R.string.diagnostics_subtitle));

        LinearLayout previews = new LinearLayout(this);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        previews.setGravity(Gravity.CENTER);
        previews.setPadding(0, dp(4), 0, 0);

        LinearLayout joyWrap = new LinearLayout(this);
        joyWrap.setOrientation(LinearLayout.VERTICAL);
        joyWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView joyTitle = text(getString(R.string.diagnostics_joystick), 13, true, textPrimary);
        joyTitle.setGravity(Gravity.CENTER);
        joyWrap.addView(joyTitle);
        joystickDiagnosticView = new JoystickDiagnosticView(this);
        joystickDiagnosticView.setDark(dark);
        LinearLayout.LayoutParams joyLp = fixed(dp(88), dp(88));
        joyLp.gravity = Gravity.CENTER_HORIZONTAL;
        joyWrap.addView(joystickDiagnosticView, joyLp);
        diagnosticsJoystick = text("X=+0.00   Y=+0.00", 12, false, textSecondary);
        diagnosticsJoystick.setTypeface(Typeface.MONOSPACE);
        diagnosticsJoystick.setGravity(Gravity.CENTER);
        joyWrap.addView(diagnosticsJoystick);
        previews.addView(joyWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout ballWrap = new LinearLayout(this);
        ballWrap.setOrientation(LinearLayout.VERTICAL);
        ballWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ballTitle = text(getString(R.string.diagnostics_buttons), 13, true, textPrimary);
        ballTitle.setGravity(Gravity.CENTER);
        ballWrap.addView(ballTitle);
        pokeballDiagnosticView = new PokeballDiagnosticView(this);
        pokeballDiagnosticView.setDark(dark);
        LinearLayout.LayoutParams ballLp = fixed(dp(108), dp(108));
        ballLp.gravity = Gravity.CENTER_HORIZONTAL;
        ballWrap.addView(pokeballDiagnosticView, ballLp);
        previews.addView(ballWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(previews);

        Button zeroButton = secondaryButton(getString(R.string.joystick_set_zero), 0, v -> calibrateJoystickCenter());
        LinearLayout.LayoutParams zeroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zeroLp.gravity = Gravity.CENTER_HORIZONTAL;
        zeroLp.topMargin = dp(8);
        card.addView(zeroButton, zeroLp);

        joystickCenterStatus = text(getString(R.string.joystick_center_adjusted), 11, false, textSecondary);
        joystickCenterStatus.setGravity(Gravity.CENTER);
        joystickCenterStatus.setPadding(dp(8), dp(3), dp(8), dp(2));
        joystickCenterStatus.setOnClickListener(v -> {
            if (!InputRouter.fakeCenterEnabled()) return;
            InputRouter.clearFakeCenter();
            refreshJoystickCenterStatus();
            Toast.makeText(this, getString(R.string.joystick_zero_cleared), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams centerStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerStatusLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(joystickCenterStatus, centerStatusLp);

        refreshJoystickCenterStatus();
    }

    private void calibrateJoystickCenter() {
        if (!PokeballService.isConnected()) {
            Toast.makeText(this, getString(R.string.joystick_connect_first), Toast.LENGTH_SHORT).show();
            return;
        }
        InputRouter.setFakeCenterFromCurrent();
        refreshJoystickCenterStatus();
        Toast.makeText(this, getString(R.string.joystick_zero_saved), Toast.LENGTH_SHORT).show();
    }

    private void refreshJoystickCenterStatus() {
        if (joystickCenterStatus == null) return;
        boolean active = InputRouter.fakeCenterEnabled();
        joystickCenterStatus.setVisibility(active ? View.VISIBLE : View.GONE);
        joystickCenterStatus.setText(getString(R.string.joystick_fake_center_active));
        joystickCenterStatus.setTextColor(active ? accent : textSecondary);
    }

    private TextView diagnosticBox() {
        TextView view = text("", 13, false, textSecondary);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(roundRect(surfaceRaised, outline, 12));
        return view;
    }

    private LinearLayout card(LinearLayout root, int iconRes, String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(roundRect(surface, outline, 16));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        root.addView(card, cardLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = iconView(iconRes, textPrimary, 22);
        LinearLayout.LayoutParams iconLp = fixed(dp(30), dp(30));
        iconLp.rightMargin = dp(9);
        header.addView(icon, iconLp);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 18, true, textPrimary));
        TextView sub = text(subtitle, 13, false, textSecondary);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(1, dp(10)));
        return card;
    }

    private StatusRow addActionRow(LinearLayout parent, int iconRes, String title, String description,
                                   String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        ImageView icon = iconView(iconRes, textPrimary, 23);
        LinearLayout.LayoutParams iconLp = fixed(dp(32), dp(32));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, true, textPrimary));
        TextView desc = text(description, 12, false, textSecondary);
        desc.setPadding(0, dp(2), 0, 0);
        copy.addView(desc);
        TextView status = text(getString(R.string.status_checking), 12, true, textSecondary);
        status.setPadding(0, dp(3), 0, 0);
        copy.addView(status);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button button = secondaryButton(action, 0, listener);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.leftMargin = dp(8);
        row.addView(button, actionLp);
        parent.addView(row);
        return new StatusRow(icon, status, button);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(outline);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(42);
        parent.addView(divider, lp);
    }

    private void addModeRadio(RadioGroup group, String label, ControlConfig.Mode mode) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextColor(textPrimary);
        rb.setTextSize(14);
        rb.setLineSpacing(0f, 1.08f);
        rb.setTag(mode);
        rb.setId(View.generateViewId());
        rb.setChecked(config.mode() == mode);
        rb.setPadding(0, dp(5), 0, dp(5));
        rb.setButtonTintList(radioTint());
        rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) return;
            config.setMode((ControlConfig.Mode) buttonView.getTag());
            InputRouter.onModeChanged();
            updateModeSpecificVisibility();
        });
        group.addView(rb);
    }

    private void updateModeSpecificVisibility() {
        if (touchCard != null) {
            boolean showTouch = PokeballService.isConnected() && config.mode() == ControlConfig.Mode.TOUCH;
            touchCard.setVisibility(showTouch ? View.VISIBLE : View.GONE);
        }
    }

    private void addTypingRadio(RadioGroup group, String label, ControlConfig.TypingMode mode) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextColor(textPrimary);
        rb.setTextSize(14);
        rb.setLineSpacing(0f, 1.08f);
        rb.setTag(mode);
        rb.setId(View.generateViewId());
        rb.setChecked(config.typingMode() == mode);
        rb.setPadding(0, dp(5), 0, dp(5));
        rb.setButtonTintList(radioTint());
        rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) return;
            config.setTypingMode((ControlConfig.TypingMode) buttonView.getTag());
            if (InputRouter.typingActive()) {
                CursorAccessibilityService service = CursorAccessibilityService.getInstance();
                if (service != null) service.setTypingVisible(true, config.typingMode());
            }
        });
        group.addView(rb);
    }

    private Spinner actionSpinner(ControlConfig.MotionDirection direction) {
        Spinner spinner = new Spinner(this);
        ControlConfig.Action[] actions = ControlConfig.Action.values();
        String[] labels = new String[actions.length];
        for (int i=0;i<actions.length;i++) labels[i] = UiLabels.action(this, actions[i]);
        spinner.setAdapter(spinnerAdapter(labels, 13));
        spinner.setSelection(config.motionAction(direction).ordinal());
        spinner.setPopupBackgroundDrawable(roundRect(surfaceRaised, outline, 10));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                config.setMotionAction(direction, actions[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return spinner;
    }

    private Button mappingButton(ControlConfig.Binding binding) {
        Button button = secondaryButton(mappingLabel(binding), R.drawable.ic_touch, v -> beginPick(binding));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        button.setLayoutParams(lp);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return button;
    }

    private void beginPick(ControlConfig.Binding binding) {
        CursorAccessibilityService cursor = CursorAccessibilityService.getInstance();
        if (cursor == null) {
            Toast.makeText(this, getString(R.string.tap_enable_accessibility), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, getString(R.string.tap_touch_place_for, UiLabels.binding(this, binding)), Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
        handler.postDelayed(() -> {
            CursorAccessibilityService current = CursorAccessibilityService.getInstance();
            if (current != null) current.beginTouchPick(binding);
        }, 700L);
    }

    private String mappingLabel(ControlConfig.Binding binding) {
        String label = UiLabels.binding(this, binding);
        if (!config.hasTouchPoint(binding)) return label + "   " + getString(R.string.tap_not_set);
        int x = Math.round(config.touchX(binding) * 100f);
        int y = Math.round(config.touchY(binding) * 100f);
        return label + "   " + x + "% / " + y + "%";
    }

    private void refreshMappingButtons() {
        for (ControlConfig.Binding binding : ControlConfig.Binding.values()) {
            Button b = mappingButtons.get(binding);
            if (b != null) b.setText(mappingLabel(binding));
        }
    }

    private void updateSensitivityLabel(float value) {
        if (sensitivityText != null) sensitivityText.setText(getString(R.string.motion_threshold, value));
    }

    private void updateConnectionActionButton(PokeballService.Phase phase) {
        if (connectionActionButton == null) return;
        boolean connected = phase == PokeballService.Phase.CONNECTED;
        boolean busy = phase == PokeballService.Phase.SEARCHING
                || phase == PokeballService.Phase.CONNECTING;

        if (connected) {
            connectionActionButton.setText(getString(R.string.action_disconnect));
            connectionActionButton.setTextColor(textPrimary);
            connectionActionButton.setBackgroundTintList(ColorStateList.valueOf(surfaceRaised));
            connectionActionButton.setCompoundDrawablesRelative(null, null, null, null);
            connectionActionButton.setOnClickListener(v -> disconnectPokeball());
        } else if (busy) {
            connectionActionButton.setText(getString(R.string.action_cancel_connection));
            connectionActionButton.setTextColor(textPrimary);
            connectionActionButton.setBackgroundTintList(ColorStateList.valueOf(surfaceRaised));
            connectionActionButton.setCompoundDrawablesRelative(null, null, null, null);
            connectionActionButton.setOnClickListener(v -> disconnectPokeball());
        } else {
            connectionActionButton.setText(getString(R.string.action_connect));
            connectionActionButton.setTextColor(Color.WHITE);
            connectionActionButton.setBackgroundTintList(ColorStateList.valueOf(accent));
            connectionActionButton.setCompoundDrawablesRelative(null, null, null, null);
            connectionActionButton.setOnClickListener(v -> connectPokeball());
        }
    }

    private void connectPokeball() {
        if (!runtimePermissionsReady()) {
            pendingConnect = true;
            requestRuntimePermissions();
            return;
        }
        pendingConnect = false;
        Intent intent = new Intent(this, PokeballService.class).setAction(PokeballService.ACTION_CONNECT);
        startForegroundService(intent);
    }

    private void disconnectPokeball() {
        Intent intent = new Intent(this, PokeballService.class).setAction(PokeballService.ACTION_DISCONNECT);
        startService(intent);
    }

    private void openBluetoothControl() {
        if (!runtimePermissionsReady()) {
            pendingBluetoothControl = true;
            requestRuntimePermissions();
            return;
        }
        pendingBluetoothControl = false;
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter != null && !adapter.isEnabled()) {
            try {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                return;
            } catch (Throwable ignored) {
                // Fall through to the Bluetooth settings page.
            }
        }
        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        catch (Throwable ignored) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String component = new ComponentName(this, CursorAccessibilityService.class).flattenToString();
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null) {
                for (String item : enabled.split(":")) if (component.equalsIgnoreCase(item.trim())) return true;
            }
        } catch (Throwable ignored) {}
        return CursorAccessibilityService.getInstance() != null;
    }

    private void enableAccessibility() {
        if (isAccessibilityEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        ShizukuBridge bridge = ShizukuBridge.get();
        if (bridge == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        String component = new ComponentName(this, CursorAccessibilityService.class).flattenToString();
        bridge.whenReady(() -> bridge.setAccessibilityService(component, true, successResult -> runOnUiThread(() -> {
            if (successResult) {
                Toast.makeText(this, getString(R.string.accessibility_enabled_direct), Toast.LENGTH_SHORT).show();
                handler.postDelayed(this::updateButtonDiagnostics, 150L);
            } else {
                Toast.makeText(this, getString(R.string.accessibility_direct_failed), Toast.LENGTH_LONG).show();
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Throwable ignored) {}
            }
        })));
        bridge.requestPermissionAndBind();
    }

    private void updateProfileUi(PokeballService.Phase phase) {
        if (profileStatus == null) return;
        View row = profileStatus.getTag() instanceof View ? (View) profileStatus.getTag() : null;
        if (phase != PokeballService.Phase.CONNECTED) {
            if (row != null) row.setVisibility(View.GONE);
            return;
        }
        DeviceProfileStore.Profile profile = DeviceProfileStore.get().activeProfile();
        if (profile == null) {
            if (row != null) row.setVisibility(View.GONE);
            return;
        }
        if (row != null) row.setVisibility(View.VISIBLE);
        boolean fullyCalibrated = profile.joystickCalibrated && profile.motionCalibrated;
        String calibration = fullyCalibrated
                ? getString(R.string.profile_calibrated) : getString(R.string.profile_not_calibrated);
        profileStatus.setText(profile.name + " · " + profile.id + "
" + calibration);
        profileStatus.setGravity(Gravity.CENTER);
        profileStatus.setTextColor(fullyCalibrated ? textSecondary : textPrimary);
        if (calibrationButton != null) calibrationButton.setVisibility(fullyCalibrated ? View.GONE : View.VISIBLE);
        if (!profile.calibrationPrompted && !profile.address.equals(offeredCalibrationAddress)) {
            offeredCalibrationAddress = profile.address;
            handler.postDelayed(() -> showCalibrationOffer(profile), 250L);
        }
    }

    private void showCalibrationOffer(DeviceProfileStore.Profile profile) {
        if (isFinishing() || profile == null || !PokeballService.isConnected()) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = imageViewNoTint(R.drawable.ic_launcher_pokeball, 36);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        icon.setBackground(roundRect(surfaceRaised, outline, 12));
        header.addView(icon, fixed(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(getString(R.string.profile_new_device, profile.id), 20, true, textPrimary));
        TextView message = bodyText(getString(R.string.profile_calibration_offer));
        message.setPadding(0, dp(3), 0, 0);
        copy.addView(message);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        header.addView(copy, copyLp);
        panel.addView(header);

        CalibrationInstructionView visual = new CalibrationInstructionView(this);
        visual.setType(CalibrationInstructionView.Type.TABLE);
        LinearLayout.LayoutParams visualLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(126));
        visualLp.topMargin = dp(10);
        panel.addView(visual, visualLp);

        TextView steps = text(getString(R.string.profile_calibration_steps), 12, false, textSecondary);
        steps.setGravity(Gravity.CENTER);
        steps.setLineSpacing(0f, 1.12f);
        steps.setPadding(dp(10), dp(8), dp(10), dp(8));
        steps.setBackground(roundRect(surfaceRaised, outline, 12));
        panel.addView(steps);

        AlertDialog offer = new AlertDialog.Builder(this)
                .setView(panel)
                .setPositiveButton(R.string.profile_calibrate_now, (d, w) -> startCalibration(profile))
                .setNegativeButton(R.string.profile_skip, (d, w) -> DeviceProfileStore.get().markPrompted(profile.key, true))
                .create();
        offer.setOnShowListener(d -> {
            if (offer.getWindow() != null) offer.getWindow().setBackgroundDrawable(roundRect(surface, outline, 18));
            Button negative = offer.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) negative.setTextColor(textSecondary);
            Button positive = offer.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setTextColor(accent);
        });
        offer.show();
    }

    private void startCalibration(DeviceProfileStore.Profile profile) {
        calibrationWizard = new CalibrationWizard(this, completed -> { calibrationWizard = null; });
        calibrationWizard.start(profile);
    }

    private void showActiveProfileDialog() {
        DeviceProfileStore.Profile profile = DeviceProfileStore.get().activeProfile();
        if (profile == null || !PokeballService.isConnected()) {
            Toast.makeText(this, getString(R.string.profile_none_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        String state = (profile.joystickCalibrated && profile.motionCalibrated)
                ? getString(R.string.profile_calibrated) : getString(R.string.profile_not_calibrated);
        String[] items = { getString(R.string.profile_calibrate), getString(R.string.profile_rename), getString(R.string.profile_reset_calibration) };
        new AlertDialog.Builder(this)
                .setTitle(profile.name + " · " + profile.id)
                .setMessage(getString(R.string.profile_address, profile.address) + "\n" + state)
                .setItems(items, (d, which) -> {
                    if (which == 0) startCalibration(profile);
                    else if (which == 1) showRenameProfile(profile);
                    else { DeviceProfileStore.get().clearCalibration(profile.key); Toast.makeText(this, R.string.profile_reset_done, Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton(R.string.appearance_cancel, null)
                .show();
    }

    private void showRenameProfile(DeviceProfileStore.Profile profile) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true); input.setText(profile.name); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(R.string.profile_rename).setView(input)
                .setPositiveButton(R.string.appearance_save, (d,w) -> DeviceProfileStore.get().rename(profile.key, input.getText().toString()))
                .setNegativeButton(R.string.appearance_cancel, null).show();
    }

    private boolean runtimePermissionsReady() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean bluetoothEnabled() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        return adapter != null && adapter.isEnabled();
    }

    private boolean locationEnabled() {
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 28) return manager.isLocationEnabled();
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Throwable ignored) { return false; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST && runtimePermissionsReady()) {
            if (pendingConnect) connectPokeball();
            if (pendingBluetoothControl) {
                pendingBluetoothControl = false;
                openBluetoothControl();
            }
        }
    }

    private void requestRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERMISSIONS_REQUEST);
    }

    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            refreshMappingButtons();
            ShizukuBridge bridge = ShizukuBridge.get();
            boolean permissions = runtimePermissionsReady();
            boolean btEnabled = bluetoothEnabled();
            boolean btReady = permissions && btEnabled;
            boolean locReady = locationEnabled();
            boolean accessibilityReady = isAccessibilityEnabled();
            boolean shizukuReady = bridge != null && bridge.isReady();

            setStatusRow(bluetoothRow, btReady,
                    !permissions ? getString(R.string.status_permissions_missing)
                            : (btEnabled ? getString(R.string.status_on) : getString(R.string.status_off)));
            setStatusRow(locationRow, locReady, locReady ? getString(R.string.status_on) : getString(R.string.status_off));
            setStatusRow(accessibilityRow, accessibilityReady,
                    accessibilityReady ? getString(R.string.status_active) : getString(R.string.status_off));
            if (accessibilityRow != null && accessibilityRow.button != null) {
                accessibilityRow.button.setText(accessibilityReady ? getString(R.string.action_settings) : getString(R.string.action_enable));
            }
            setStatusRow(shizukuRow, shizukuReady,
                    shizukuReady ? getString(R.string.status_active) : getString(R.string.status_off));
            if (shizukuRow != null && shizukuRow.button != null) {
                shizukuRow.button.setText(getString(R.string.action_settings));
            }

            PokeballService.Phase phase = PokeballService.phase();
            if (phase == PokeballService.Phase.CONNECTING || phase == PokeballService.Phase.CONNECTED) {
                DeviceProfileStore.Profile activeProfile = DeviceProfileStore.get().activeProfile();
                String activeKey = activeProfile != null ? activeProfile.key : null;
                if (activeKey != null && !activeKey.equals(renderedProfileKey)) {
                    renderedProfileKey = activeKey;
                    recreate();
                    return;
                }
            }
            if (connectionOrb != null) connectionOrb.setPhase(phase);
            if (pokeballStatus != null) {
                String label;
                int color = textSecondary;
                switch (phase) {
                    case SEARCHING: label = getString(R.string.status_searching); color = textPrimary; break;
                    case CONNECTING: label = getString(R.string.status_connecting); color = textPrimary; break;
                    case CONNECTED: label = getString(R.string.status_connected); color = success; break;
                    case ERROR: label = PokeballService.state(); color = danger; break;
                    case DISCONNECTED:
                    default: label = getString(R.string.status_disconnected); break;
                }
                pokeballStatus.setText(label);
                pokeballStatus.setTextColor(color);
            }
            updateConnectionActionButton(phase);

            int battery = PokeballService.batteryLevel();
            if (batteryIcon != null) batteryIcon.setLevel(battery);
            if (batteryStatus != null) {
                batteryStatus.setText(getString(R.string.battery_label) + "  " +
                        (battery >= 0 ? getString(R.string.battery_percent, battery) : getString(R.string.battery_unknown)));
                batteryStatus.setTextColor(battery >= 0 ? textPrimary : textSecondary);
            }

            boolean connected = phase == PokeballService.Phase.CONNECTED;
            if (batteryRow != null) batteryRow.setVisibility(connected ? View.VISIBLE : View.GONE);
            if (connectedSettingsContainer != null) connectedSettingsContainer.setVisibility(connected ? View.VISIBLE : View.GONE);
            updateModeSpecificVisibility();
            updateProfileUi(phase);
            handler.postDelayed(this, 350L);
        }
    };

    private void updateButtonDiagnostics() {
        if (pokeballDiagnosticView != null) {
            pokeballDiagnosticView.setPressed(InputRouter.topPressed(), InputRouter.stickPressed());
        }
    }

    private final Runnable telemetryUpdater = new Runnable() {
        @Override public void run() {
            updateLiveTelemetry();
            handler.postDelayed(this, 32L);
        }
    };

    private void updateLiveTelemetry() {
        if (diagnosticsJoystick != null) {
            diagnosticsJoystick.setText(String.format(Locale.ROOT, "X=%+.2f   Y=%+.2f", InputRouter.joyX(), InputRouter.joyY()));
        }
        refreshJoystickCenterStatus();

        if (motionSensors != null) {
            motionSensors.setText(
                    getString(R.string.motion_orientation) + "  " +
                            fmtDeg(InputRouter.pitch()) + " / " + fmtDeg(InputRouter.yaw()) + " / " + fmtDeg(InputRouter.roll()) + "\n" +
                    getString(R.string.motion_gyro) + "  X=" + fmt(InputRouter.gyroX()) + "  Y=" + fmt(InputRouter.gyroY()) +
                            "  Z=" + fmt(InputRouter.gyroZ()) + "  W=" + fmt(InputRouter.gyroW()) + "\n" +
                    getString(R.string.motion_accel) + "  X=" + fmt(InputRouter.accelX()) + "  Y=" + fmt(InputRouter.accelY()) +
                            "  Z=" + fmt(InputRouter.accelZ()));
        }

        if (motionDetected != null) {
            MotionTelemetryDetector.Direction direction = InputRouter.liveMotionDirection();
            long age = android.os.SystemClock.uptimeMillis() - InputRouter.liveMotionTimestampMs();
            if (direction == null || (!InputRouter.topPressed() && age > 800L)) {
                motionDetected.setText(getString(R.string.motion_detected_none));
                motionDetected.setTextColor(textSecondary);
            } else {
                motionDetected.setText(getString(R.string.motion_detected_direction, motionDirectionLabel(direction)));
                motionDetected.setTextColor(accent);
            }
        }
    }

    private String motionDirectionLabel(MotionTelemetryDetector.Direction direction) {
        switch (direction) {
            case LEFT: return getString(R.string.motion_detected_left);
            case RIGHT: return getString(R.string.motion_detected_right);
            case UP: return getString(R.string.motion_detected_up);
            case DOWN: return getString(R.string.motion_detected_down);
            case FORWARD: return getString(R.string.motion_detected_forward);
            case BACKWARD: return getString(R.string.motion_detected_backward);
            default: return "—";
        }
    }

    private String fmt(float v) {
        return Float.isFinite(v) ? String.format(Locale.ROOT, "%+.2f", v) : "--";
    }
    private String fmtDeg(float v) {
        return Float.isFinite(v) ? String.format(Locale.ROOT, "%+.1f°", v) : "--";
    }

    private void setStatusRow(StatusRow row, boolean ok, String value) {
        if (row == null) return;
        row.status.setText(value);
        row.status.setTextColor(ok ? success : danger);
        // Requested visual rule: neutral/light icon when ready, red icon when missing/off.
        row.icon.setImageTintList(ColorStateList.valueOf(ok ? textPrimary : danger));
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView bodyText(String value) {
        TextView view = text(value, 13, false, textSecondary);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private TextView smallLabel(String value) {
        TextView view = text(value, 11, true, textSecondary);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private Button primaryButton(String label, int iconRes, View.OnClickListener listener) {
        Button button = baseButton(label, iconRes, listener);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(accent));
        tintButtonIcon(button, Color.WHITE);
        return button;
    }

    private Button secondaryButton(String label, int iconRes, View.OnClickListener listener) {
        Button button = baseButton(label, iconRes, listener);
        button.setTextColor(textPrimary);
        button.setBackgroundTintList(ColorStateList.valueOf(surfaceRaised));
        tintButtonIcon(button, textPrimary);
        return button;
    }

    private Button baseButton(String label, int iconRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(dp(40));
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setOnClickListener(listener);
        if (iconRes != 0) {
            Drawable icon = getDrawable(iconRes);
            if (icon != null) {
                icon.setBounds(0, 0, dp(18), dp(18));
                button.setCompoundDrawablesRelative(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(7));
            }
        }
        return button;
    }

    private void tintButtonIcon(Button button, int color) {
        Drawable[] drawables = button.getCompoundDrawablesRelative();
        for (Drawable drawable : drawables) if (drawable != null) drawable.setTint(color);
    }

    private CheckBox check(String label, boolean value, CompoundButton.OnCheckedChangeListener listener) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(textPrimary);
        box.setTextSize(14);
        box.setChecked(value);
        box.setButtonTintList(radioTint());
        box.setOnCheckedChangeListener(listener);
        return box;
    }

    private ImageView iconView(int resId, int tint, int sizeDp) {
        ImageView image = new ImageView(this);
        image.setImageResource(resId);
        image.setImageTintList(ColorStateList.valueOf(tint));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(3), dp(3), dp(3), dp(3));
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        return image;
    }

    private ImageView imageViewNoTint(int resId, int sizeDp) {
        ImageView image = new ImageView(this);
        image.setImageResource(resId);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(2), dp(2), dp(2), dp(2));
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        return image;
    }

    private ColorStateList radioTint() {
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
        };
        int[] colors = new int[] { accent, textSecondary };
        return new ColorStateList(states, colors);
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        InputRouter.setStateListener(null);
        super.onDestroy();
    }
}
