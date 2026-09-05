package pl.openai.pokeballmouse;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Per-device identity and calibration storage for Poké Ball Plus controllers. */
public final class DeviceProfileStore {
    public static final class Profile {
        public final String key;
        public final String id;
        public final String address;
        public final String name;
        public final boolean calibrationPrompted;
        public final boolean joystickCalibrated;
        public final boolean motionCalibrated;
        public final float centerX, centerY;
        public final float minX, maxX, minY, maxY;
        public final float idleNoise;

        Profile(String key, String id, String address, String name, boolean calibrationPrompted,
                boolean joystickCalibrated, boolean motionCalibrated,
                float centerX, float centerY, float minX, float maxX, float minY, float maxY,
                float idleNoise) {
            this.key = key; this.id = id; this.address = address; this.name = name;
            this.calibrationPrompted = calibrationPrompted;
            this.joystickCalibrated = joystickCalibrated;
            this.motionCalibrated = motionCalibrated;
            this.centerX = centerX; this.centerY = centerY;
            this.minX = minX; this.maxX = maxX; this.minY = minY; this.maxY = maxY;
            this.idleNoise = idleNoise;
        }
    }

    public static final class MotionTemplates {
        private final float[][] v = new float[4][3];
        MotionTemplates(float[][] values) {
            for (int i = 0; i < 4; i++) System.arraycopy(values[i], 0, v[i], 0, 3);
        }
        public float[] vector(MotionGestureDetector.Direction direction) {
            int index;
            switch (direction) {
                case LEFT: index = 0; break;
                case RIGHT: index = 1; break;
                case UP: index = 2; break;
                case DOWN: index = 3; break;
                default: throw new IllegalArgumentException();
            }
            return new float[]{v[index][0], v[index][1], v[index][2]};
        }
    }

    private static final String PREFS = "device_profiles_v1";
    private static final String KEY_SET = "profile_keys";
    private static final String KEY_ACTIVE = "active_profile";

    private static volatile DeviceProfileStore instance;
    private final SharedPreferences prefs;

    private DeviceProfileStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (DeviceProfileStore.class) {
                if (instance == null) instance = new DeviceProfileStore(context);
            }
        }
    }

    public static DeviceProfileStore get() {
        if (instance == null) throw new IllegalStateException("DeviceProfileStore.init() not called");
        return instance;
    }

    private String keyForAddress(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        return address.toUpperCase(Locale.ROOT).replace(':', '_');
    }

    private String p(String key, String field) { return "p_" + key + "_" + field; }

    public synchronized Profile ensureProfile(String address, String bluetoothName) {
        String key = keyForAddress(address);
        if (key == null) return null;
        Set<String> keys = prefs.getStringSet(KEY_SET, Collections.emptySet());
        if (!keys.contains(key)) {
            java.util.HashSet<String> updated = new java.util.HashSet<>(keys);
            updated.add(key);
            int next = prefs.getInt("next_profile_number", 1);
            String id = String.format(Locale.ROOT, "PB-%02d", next);
            String defaultName = "Poké Ball Plus " + next;
            prefs.edit()
                    .putStringSet(KEY_SET, updated)
                    .putInt("next_profile_number", next + 1)
                    .putString(p(key, "id"), id)
                    .putString(p(key, "address"), address)
                    .putString(p(key, "name"), defaultName)
                    .putString(p(key, "bt_name"), bluetoothName == null ? "Poké Ball Plus" : bluetoothName)
                    .apply();
        }
        prefs.edit().putString(KEY_ACTIVE, key).apply();
        return load(key);
    }

    public synchronized Profile activeProfile() {
        String key = prefs.getString(KEY_ACTIVE, null);
        return key == null ? null : load(key);
    }

    public synchronized void setActiveAddress(String address, String bluetoothName) {
        ensureProfile(address, bluetoothName);
    }

    public synchronized void clearActive() {
        prefs.edit().remove(KEY_ACTIVE).apply();
    }

    public synchronized List<Profile> profiles() {
        List<Profile> result = new ArrayList<>();
        for (String key : prefs.getStringSet(KEY_SET, Collections.emptySet())) {
            Profile profile = load(key);
            if (profile != null) result.add(profile);
        }
        result.sort((a, b) -> a.id.compareTo(b.id));
        return result;
    }

    private Profile load(String key) {
        if (key == null || !prefs.contains(p(key, "id"))) return null;
        return new Profile(
                key,
                prefs.getString(p(key, "id"), "PB-??"),
                prefs.getString(p(key, "address"), ""),
                prefs.getString(p(key, "name"), "Poké Ball Plus"),
                prefs.getBoolean(p(key, "prompted"), false),
                prefs.getBoolean(p(key, "joy_calibrated"), false),
                prefs.getBoolean(p(key, "motion_calibrated"), false),
                prefs.getFloat(p(key, "center_x"), 0f),
                prefs.getFloat(p(key, "center_y"), 0f),
                prefs.getFloat(p(key, "min_x"), -1f),
                prefs.getFloat(p(key, "max_x"), 1f),
                prefs.getFloat(p(key, "min_y"), -1f),
                prefs.getFloat(p(key, "max_y"), 1f),
                prefs.getFloat(p(key, "idle_noise"), 0.32f));
    }

    public synchronized void rename(String key, String name) {
        if (key == null) return;
        String safe = name == null ? "" : name.trim();
        if (safe.isEmpty()) return;
        prefs.edit().putString(p(key, "name"), safe.substring(0, Math.min(48, safe.length()))).apply();
    }

    public synchronized void markPrompted(String key, boolean prompted) {
        if (key != null) prefs.edit().putBoolean(p(key, "prompted"), prompted).apply();
    }

    public synchronized void saveJoystickCalibration(String key, float centerX, float centerY,
                                                        float minX, float maxX, float minY, float maxY,
                                                        float idleNoise) {
        if (key == null) return;
        if (minX > maxX) { float t = minX; minX = maxX; maxX = t; }
        if (minY > maxY) { float t = minY; minY = maxY; maxY = t; }
        prefs.edit()
                .putBoolean(p(key, "joy_calibrated"), true)
                .putFloat(p(key, "center_x"), clamp(centerX, -0.85f, 0.85f))
                .putFloat(p(key, "center_y"), clamp(centerY, -0.85f, 0.85f))
                .putFloat(p(key, "min_x"), clamp(minX, -1f, 1f))
                .putFloat(p(key, "max_x"), clamp(maxX, -1f, 1f))
                .putFloat(p(key, "min_y"), clamp(minY, -1f, 1f))
                .putFloat(p(key, "max_y"), clamp(maxY, -1f, 1f))
                .putFloat(p(key, "idle_noise"), clamp(idleNoise, 0.02f, 0.80f))
                .apply();
    }

    public synchronized void saveMotionTemplates(String key, float[][] vectors) {
        if (key == null || vectors == null || vectors.length != 4) return;
        SharedPreferences.Editor e = prefs.edit().putBoolean(p(key, "motion_calibrated"), true);
        String[] names = {"left", "right", "up", "down"};
        for (int i = 0; i < 4; i++) {
            float[] n = normalized(vectors[i]);
            e.putFloat(p(key, "motion_" + names[i] + "_x"), n[0]);
            e.putFloat(p(key, "motion_" + names[i] + "_y"), n[1]);
            e.putFloat(p(key, "motion_" + names[i] + "_z"), n[2]);
        }
        e.apply();
    }

    public synchronized MotionTemplates motionTemplates() {
        Profile active = activeProfile();
        if (active == null || !active.motionCalibrated) return null;
        String key = active.key;
        String[] names = {"left", "right", "up", "down"};
        float[][] values = new float[4][3];
        for (int i = 0; i < 4; i++) {
            values[i][0] = prefs.getFloat(p(key, "motion_" + names[i] + "_x"), 0f);
            values[i][1] = prefs.getFloat(p(key, "motion_" + names[i] + "_y"), 0f);
            values[i][2] = prefs.getFloat(p(key, "motion_" + names[i] + "_z"), 0f);
            if (length(values[i]) < 0.5f) return null;
        }
        return new MotionTemplates(values);
    }

    public synchronized void clearCalibration(String key) {
        if (key == null) return;
        SharedPreferences.Editor e = prefs.edit();
        String[] fields = {"joy_calibrated", "motion_calibrated", "center_x", "center_y", "min_x", "max_x",
                "min_y", "max_y", "idle_noise"};
        for (String field : fields) e.remove(p(key, field));
        for (String dir : new String[]{"left", "right", "up", "down"}) {
            for (String axis : new String[]{"x", "y", "z"}) e.remove(p(key, "motion_" + dir + "_" + axis));
        }
        e.apply();
    }

    public synchronized void forget(String key) {
        if (key == null) return;
        java.util.HashSet<String> updated = new java.util.HashSet<>(prefs.getStringSet(KEY_SET, Collections.emptySet()));
        updated.remove(key);
        SharedPreferences.Editor e = prefs.edit().putStringSet(KEY_SET, updated);
        for (String field : new String[]{"id", "address", "name", "bt_name", "prompted", "joy_calibrated",
                "motion_calibrated", "center_x", "center_y", "min_x", "max_x", "min_y", "max_y", "idle_noise"}) {
            e.remove(p(key, field));
        }
        for (String dir : new String[]{"left", "right", "up", "down"}) {
            for (String axis : new String[]{"x", "y", "z"}) e.remove(p(key, "motion_" + dir + "_" + axis));
        }
        if (key.equals(prefs.getString(KEY_ACTIVE, null))) e.remove(KEY_ACTIVE);
        e.apply();
    }

    private static float[] normalized(float[] vector) {
        float len = length(vector);
        if (len < 0.0001f) return new float[]{0f, 0f, 0f};
        return new float[]{vector[0] / len, vector[1] / len, vector[2] / len};
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
