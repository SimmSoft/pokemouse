package pl.openai.pokeballmouse;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
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
    private static final int PERMISSIONS_REQUEST = 4100;

    private static final class StatusRow {
        final ImageView icon;
        final TextView status;
        StatusRow(ImageView icon, TextView status) { this.icon = icon; this.status = status; }
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
    private ConnectionOrbView connectionOrb;
    private TextView diagnosticsButtons;
    private TextView diagnosticsJoystick;
    private TextView diagnosticsSensors;
    private JoystickDiagnosticView joystickDiagnosticView;
    private TextView sensitivityText;
    private boolean pendingConnect;
    private ControlConfig config;

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

        initPalette();
        applyWindowPalette();
        buildUi();
        requestRuntimePermissions();
        handler.post(statusUpdater);
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
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        addHeader(root);
        addConnectionCard(root);
        addModeCard(root);
        addTouchCard(root);
        addMotionCard(root);
        addAppearanceCard(root);
        addDiagnosticsCard(root);
        setContentView(scroll);
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
        root.addView(row);
    }

    private void addConnectionCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_link,
                getString(R.string.connection_title), getString(R.string.connection_subtitle));

        bluetoothRow = addActionRow(card, R.drawable.ic_bluetooth,
                getString(R.string.bluetooth_title), getString(R.string.bluetooth_desc),
                getString(R.string.action_permissions), v -> requestRuntimePermissions());
        addDivider(card);

        locationRow = addActionRow(card, R.drawable.ic_location,
                getString(R.string.location_title), getString(R.string.location_desc),
                getString(R.string.action_settings),
                v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        addDivider(card);

        accessibilityRow = addActionRow(card, R.drawable.ic_accessibility,
                getString(R.string.accessibility_title), getString(R.string.accessibility_desc),
                getString(R.string.action_settings),
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addDivider(card);

        shizukuRow = addActionRow(card, R.drawable.ic_link,
                getString(R.string.shizuku_title), getString(R.string.shizuku_desc),
                getString(R.string.action_connect), v -> {
                    ShizukuBridge bridge = ShizukuBridge.get();
                    if (bridge != null) bridge.requestPermissionAndBind();
                });

        Button openShizuku = secondaryButton(getString(R.string.action_open_shizuku), R.drawable.ic_link, v -> openShizuku());
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openLp.gravity = Gravity.END;
        openLp.topMargin = dp(4);
        openShizuku.setLayoutParams(openLp);
        card.addView(openShizuku);
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

        LinearLayout batteryRow = new LinearLayout(this);
        batteryRow.setOrientation(LinearLayout.HORIZONTAL);
        batteryRow.setGravity(Gravity.CENTER);
        batteryRow.setPadding(0, dp(5), 0, dp(9));
        ImageView batteryIcon = iconView(R.drawable.ic_battery, textPrimary, 19);
        batteryRow.addView(batteryIcon, fixed(dp(24), dp(24)));
        batteryStatus = text(getString(R.string.battery_label) + "  " + getString(R.string.battery_unknown),
                13, false, textSecondary);
        batteryRow.addView(batteryStatus);
        card.addView(batteryRow);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        Button disconnect = secondaryButton(getString(R.string.action_disconnect), 0, v -> disconnectPokeball());
        Button connect = primaryButton(getString(R.string.action_connect), R.drawable.ic_bluetooth, v -> connectPokeball());
        buttons.addView(disconnect, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        connectLp.leftMargin = dp(8);
        buttons.addView(connect, connectLp);
        card.addView(buttons);
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

    private void addTouchCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_touch,
                getString(R.string.tap_title), getString(R.string.tap_subtitle));
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

        sensitivityText = text("", 13, false, textSecondary);
        card.addView(sensitivityText);

        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(98);
        sensitivity.setProgress(Math.round((config.motionThreshold() - 0.22f) * 100f));
        sensitivity.setProgressTintList(ColorStateList.valueOf(accent));
        sensitivity.setThumbTintList(ColorStateList.valueOf(accent));
        updateSensitivityLabel(config.motionThreshold());
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float threshold = 0.22f + progress / 100f;
                config.setMotionThreshold(threshold);
                updateSensitivityLabel(threshold);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sensitivity);

        LinearLayout axes = new LinearLayout(this);
        axes.setOrientation(LinearLayout.VERTICAL);
        axes.setPadding(0, dp(2), 0, dp(8));
        axes.addView(check(getString(R.string.motion_swap_axes), config.motionSwapAxes(),
                (buttonView, checked) -> config.setMotionSwapAxes(checked)));
        axes.addView(check(getString(R.string.motion_invert_x), config.motionInvertX(),
                (buttonView, checked) -> config.setMotionInvertX(checked)));
        axes.addView(check(getString(R.string.motion_invert_y), config.motionInvertY(),
                (buttonView, checked) -> config.setMotionInvertY(checked)));
        card.addView(axes);

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

    private void addAppearanceCard(LinearLayout root) {
        LinearLayout card = card(root, R.drawable.ic_palette,
                getString(R.string.appearance_title), getString(R.string.appearance_subtitle));

        card.addView(spinnerRow(getString(R.string.theme_label), themeSpinner()));
        addDivider(card);
        card.addView(spinnerRow(getString(R.string.language_label), languageSpinner()));
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

        diagnosticsButtons = diagnosticBox();
        card.addView(diagnosticsButtons);

        LinearLayout joyWrap = new LinearLayout(this);
        joyWrap.setOrientation(LinearLayout.VERTICAL);
        joyWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        joyWrap.setPadding(0, dp(10), 0, 0);
        TextView joyTitle = text(getString(R.string.diagnostics_joystick), 14, true, textPrimary);
        joyTitle.setGravity(Gravity.CENTER);
        joyWrap.addView(joyTitle);
        joystickDiagnosticView = new JoystickDiagnosticView(this);
        joystickDiagnosticView.setDark(dark);
        LinearLayout.LayoutParams joyLp = fixed(dp(118), dp(118));
        joyLp.gravity = Gravity.CENTER_HORIZONTAL;
        joyWrap.addView(joystickDiagnosticView, joyLp);
        diagnosticsJoystick = text("X=+0.00   Y=+0.00", 13, false, textSecondary);
        diagnosticsJoystick.setTypeface(Typeface.MONOSPACE);
        diagnosticsJoystick.setGravity(Gravity.CENTER);
        joyWrap.addView(diagnosticsJoystick);
        card.addView(joyWrap);

        diagnosticsSensors = diagnosticBox();
        LinearLayout.LayoutParams sensorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sensorLp.topMargin = dp(10);
        card.addView(diagnosticsSensors, sensorLp);

        TextView help = bodyText(getString(R.string.diagnostics_help));
        help.setPadding(0, dp(10), 0, 0);
        card.addView(help);
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
        return new StatusRow(icon, status);
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

    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) { startActivity(launch); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))); }
        catch (Throwable ignored) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))); }
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
        if (requestCode == PERMISSIONS_REQUEST && pendingConnect && runtimePermissionsReady()) connectPokeball();
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
            boolean accessibilityReady = CursorAccessibilityService.getInstance() != null;
            boolean shizukuReady = bridge != null && bridge.isReady();

            setStatusRow(bluetoothRow, btReady,
                    !permissions ? getString(R.string.status_permissions_missing)
                            : (btEnabled ? getString(R.string.status_on) : getString(R.string.status_off)));
            setStatusRow(locationRow, locReady, locReady ? getString(R.string.status_on) : getString(R.string.status_off));
            setStatusRow(accessibilityRow, accessibilityReady,
                    accessibilityReady ? getString(R.string.status_active) : getString(R.string.status_off));
            setStatusRow(shizukuRow, shizukuReady,
                    shizukuReady ? getString(R.string.status_active) : getString(R.string.status_off));

            PokeballService.Phase phase = PokeballService.phase();
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

            int battery = PokeballService.batteryLevel();
            if (batteryStatus != null) {
                batteryStatus.setText(getString(R.string.battery_label) + "  " +
                        (battery >= 0 ? getString(R.string.battery_percent, battery) : getString(R.string.battery_unknown)));
                batteryStatus.setTextColor(battery >= 0 ? textPrimary : textSecondary);
            }

            updateDiagnostics(bridge);
            handler.postDelayed(this, 300L);
        }
    };

    private void updateDiagnostics(ShizukuBridge bridge) {
        String pressed = getString(R.string.diagnostics_pressed);
        String released = getString(R.string.diagnostics_released);
        if (diagnosticsButtons != null) {
            diagnosticsButtons.setText(
                    getString(R.string.diagnostics_top) + "      " + (InputRouter.topPressed() ? pressed : released) + "\n" +
                    getString(R.string.diagnostics_stick) + "  " + (InputRouter.stickPressed() ? pressed : released));
        }
        if (joystickDiagnosticView != null) joystickDiagnosticView.setPosition(InputRouter.joyX(), InputRouter.joyY());
        if (diagnosticsJoystick != null) {
            diagnosticsJoystick.setText(String.format(Locale.ROOT, "X=%+.2f   Y=%+.2f", InputRouter.joyX(), InputRouter.joyY()));
        }
        if (diagnosticsSensors != null) {
            diagnosticsSensors.setText(
                    getString(R.string.diagnostics_gyro) + "  X=" + fmt(InputRouter.gyroX()) + "  Y=" + fmt(InputRouter.gyroY()) +
                            "  Z=" + fmt(InputRouter.gyroZ()) + "  W=" + fmt(InputRouter.gyroW()) + "\n" +
                    getString(R.string.diagnostics_pitch) + "  " + fmtDeg(InputRouter.pitch()) + " / " +
                            fmtDeg(InputRouter.yaw()) + " / " + fmtDeg(InputRouter.roll()) + "\n" +
                    getString(R.string.diagnostics_accel) + "  X=" + fmt(InputRouter.accelX()) + "  Y=" + fmt(InputRouter.accelY()) +
                            "  Z=" + fmt(InputRouter.accelZ()) + "\n" +
                    getString(R.string.diagnostics_last_gesture) + "  " + InputRouter.lastMotion() + "\n" +
                    getString(R.string.diagnostics_ble) + "  " + PokeballService.state() + "\n" +
                    getString(R.string.diagnostics_shizuku) + "  " +
                            (bridge != null && bridge.isReady() ? getString(R.string.status_active) : getString(R.string.status_off)));
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
        super.onDestroy();
    }
}
