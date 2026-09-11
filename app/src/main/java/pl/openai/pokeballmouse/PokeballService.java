package pl.openai.pokeballmouse;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

public class PokeballService extends Service {
    public enum Phase { DISCONNECTED, SEARCHING, CONNECTING, CONNECTED, ERROR }

    public static final String ACTION_CONNECT = "pl.openai.pokeballmouse.CONNECT";
    public static final String ACTION_DISCONNECT = "pl.openai.pokeballmouse.DISCONNECT";

    private static final String TAG = "PokeballService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "pokeball_connection";

    private static final UUID INPUT_SERVICE_UUID = UUID.fromString("6675e16c-f36d-4567-bb55-6b51e27a23e5");
    private static final UUID INPUT_CHARACTERISTIC_UUID = UUID.fromString("6675e16c-f36d-4567-bb55-6b51e27a23e6");
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static volatile String publicState = "Disconnected";
    private static volatile Phase publicPhase = Phase.DISCONNECTED;
    private static volatile int publicBatteryLevel = -1;
    private static volatile String publicDeviceAddress;
    private static volatile String publicDeviceName = "Poké Ball Plus";

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic batteryCharacteristic;
    private boolean scanning;
    private boolean connecting;
    private boolean environmentReceiverRegistered;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver environmentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state != BluetoothAdapter.STATE_ON) disconnectForEnvironment(getString(R.string.service_bt_off));
            } else if (LocationManager.MODE_CHANGED_ACTION.equals(action)
                    || LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {
                if (!locationEnabled()) disconnectForEnvironment(getString(R.string.service_location_off));
            }
        }
    };

    private final Runnable scanTimeout = () -> {
        if (scanning) {
            stopScan();
            failAndStop(getString(R.string.service_not_found));
        }
    };

    private final Runnable batteryPoll = new Runnable() {
        @Override public void run() {
            readBattery();
            handler.postDelayed(this, 60_000L);
        }
    };

    public static String state() { return publicState; }
    public static Phase phase() { return publicPhase; }
    public static int batteryLevel() { return publicBatteryLevel; }
    public static String deviceAddress() { return publicDeviceAddress; }
    public static String deviceName() { return publicDeviceName; }
    public static boolean isConnected() { return publicPhase == Phase.CONNECTED; }

    @Override public void onCreate() {
        super.onCreate();
        LanguagePrefs.apply(this);
        createNotificationChannel();
        IntentFilter environmentFilter = new IntentFilter();
        environmentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        environmentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
        environmentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(environmentReceiver, environmentFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(environmentReceiver, environmentFilter);
            environmentReceiverRegistered = true;
        } catch (Throwable ignored) {
            environmentReceiverRegistered = false;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_ready)));
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISCONNECT.equals(action)) {
            disconnect();
            stopSelf();
        } else {
            startScan();
        }
        return START_NOT_STICKY;
    }

    private void startScan() {
        if (!hasBluetoothPermissions()) {
            failAndStop(getString(R.string.service_missing_bt_permissions));
            return;
        }
        if (scanning || connecting || gatt != null) return;

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            failAndStop(getString(R.string.service_bt_off));
            return;
        }
        if (!locationEnabled()) {
            failAndStop(getString(R.string.service_location_off));
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            failAndStop(getString(R.string.service_no_scanner));
            return;
        }
        publicBatteryLevel = -1;
        scanning = true;
        setState(getString(R.string.service_searching), Phase.SEARCHING);
        try {
            scanner.startScan(scanCallback);
        } catch (SecurityException ex) {
            scanning = false;
            failAndStop(getString(R.string.service_missing_bt_permissions));
            return;
        }
        handler.removeCallbacks(scanTimeout);
        handler.postDelayed(scanTimeout, 30_000L);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (!hasBluetoothPermissions()) return;
            String name;
            try { name = result.getDevice().getName(); }
            catch (SecurityException ex) {
                failAndStop(getString(R.string.service_missing_bt_permissions));
                return;
            }
            if (name == null) return;
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.contains("pokeball") && !normalized.contains("pokemon pbp")) return;

            stopScan();
            if (connecting || gatt != null) return;
            try {
                publicDeviceAddress = result.getDevice().getAddress();
                publicDeviceName = name;
                DeviceProfileStore.get().ensureProfile(publicDeviceAddress, publicDeviceName);
                InputRouter.setActiveDevice(publicDeviceAddress, publicDeviceName);
            } catch (SecurityException ignored) {}
            connecting = true;
            PhoneFeedback.detectedVibration(PokeballService.this);
            setState(getString(R.string.service_connecting), Phase.CONNECTING);
            try {
                gatt = result.getDevice().connectGatt(
                        PokeballService.this, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE);
            } catch (SecurityException ex) {
                connecting = false;
                failAndStop(getString(R.string.service_missing_bt_permissions));
            }
        }

        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            failAndStop(getString(R.string.status_connection_error) + " (BLE " + errorCode + ")");
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            connecting = false;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setState(getString(R.string.service_discovering), Phase.CONNECTING);
                if (hasBluetoothPermissions()) {
                    try { bluetoothGatt.discoverServices(); }
                    catch (SecurityException ignored) {}
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.removeCallbacks(batteryPoll);
                publicBatteryLevel = -1;
                batteryCharacteristic = null;
                setState(getString(R.string.service_disconnected), Phase.DISCONNECTED);
                InputRouter.reset();
                setAccessibilityConnectionActive(false);
                try { bluetoothGatt.close(); } catch (Throwable ignored) {}
                if (gatt == bluetoothGatt) gatt = null;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndStop(getString(R.string.status_connection_error) + " (GATT " + status + ")");
                return;
            }
            BluetoothGattService inputService = bluetoothGatt.getService(INPUT_SERVICE_UUID);
            BluetoothGattCharacteristic input = inputService != null
                    ? inputService.getCharacteristic(INPUT_CHARACTERISTIC_UUID) : null;
            if (input == null) {
                failAndStop(getString(R.string.service_input_missing));
                return;
            }

            BluetoothGattService batteryService = bluetoothGatt.getService(BATTERY_SERVICE_UUID);
            batteryCharacteristic = batteryService != null
                    ? batteryService.getCharacteristic(BATTERY_LEVEL_UUID) : null;
            enableNotifications(bluetoothGatt, input);
        }

        @Override public void onDescriptorWrite(BluetoothGatt bluetoothGatt,
                                                BluetoothGattDescriptor descriptor,
                                                int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setState(getString(R.string.service_connected), Phase.CONNECTED);
                setAccessibilityConnectionActive(true);
                handler.removeCallbacks(batteryPoll);
                handler.postDelayed(batteryPoll, 500L);
            } else {
                failAndStop(getString(R.string.service_notifications_failed));
            }
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                                       BluetoothGattCharacteristic characteristic) {
            if (INPUT_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                decodeInput(characteristic.getValue());
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                                      BluetoothGattCharacteristic characteristic,
                                                      byte[] value) {
            if (INPUT_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) decodeInput(value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicRead(BluetoothGatt bluetoothGatt,
                                                    BluetoothGattCharacteristic characteristic,
                                                    int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                handleBattery(characteristic.getValue());
            }
        }

        @Override public void onCharacteristicRead(BluetoothGatt bluetoothGatt,
                                                   BluetoothGattCharacteristic characteristic,
                                                   byte[] value,
                                                   int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                handleBattery(value);
            }
        }
    };

    @SuppressWarnings("deprecation")
    private void enableNotifications(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
        if (!hasBluetoothPermissions()) return;
        try {
            boolean local = bluetoothGatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (!local || descriptor == null) {
                failAndStop(getString(R.string.service_notifications_failed));
                return;
            }
            if (Build.VERSION.SDK_INT >= 33) {
                int result = bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (result != 0) failAndStop(getString(R.string.service_notifications_failed));
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!bluetoothGatt.writeDescriptor(descriptor)) {
                    failAndStop(getString(R.string.service_notifications_failed));
                }
            }
        } catch (SecurityException ex) {
            failAndStop(getString(R.string.service_missing_bt_permissions));
        }
    }

    @SuppressWarnings("deprecation")
    private void readBattery() {
        BluetoothGatt localGatt = gatt;
        BluetoothGattCharacteristic battery = batteryCharacteristic;
        if (localGatt == null || battery == null || !hasBluetoothPermissions()) return;
        try { localGatt.readCharacteristic(battery); }
        catch (SecurityException ignored) {}
    }

    private void handleBattery(byte[] value) {
        if (value == null || value.length == 0) return;
        int level = value[0] & 0xff;
        publicBatteryLevel = Math.max(0, Math.min(100, level));
        if (publicPhase == Phase.CONNECTED) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(publicState));
        }
    }

    private void decodeInput(byte[] data) {
        try {
            PokeballDecoder.Decoded decoded = PokeballDecoder.decode(data);
            InputRouter.onPacket(decoded.x, decoded.y, decoded.topPressed, decoded.stickPressed,
                    decoded.accelX, decoded.accelY, decoded.accelZ,
                    decoded.gyroX, decoded.gyroY, decoded.gyroZ, decoded.gyroW,
                    decoded.pitch, decoded.yaw, decoded.roll);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Invalid Poké Ball Plus input packet", ex);
        }
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

    private void disconnectForEnvironment(String reason) {
        boolean hadLiveState = publicPhase == Phase.CONNECTED || publicPhase == Phase.CONNECTING || publicPhase == Phase.SEARCHING;
        disconnect();
        if (hadLiveState) setState(reason, Phase.DISCONNECTED);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < 31) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if (!scanning || scanner == null || !hasBluetoothPermissions()) {
            scanning = false;
            return;
        }
        try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) {}
        scanning = false;
    }

    private void disconnect() {
        stopScan();
        handler.removeCallbacks(batteryPoll);
        connecting = false;
        publicBatteryLevel = -1;
        batteryCharacteristic = null;
        publicDeviceAddress = null;
        publicDeviceName = "Poké Ball Plus";
        InputRouter.reset();
        setAccessibilityConnectionActive(false);
        if (gatt != null) {
            if (hasBluetoothPermissions()) {
                try { gatt.disconnect(); } catch (SecurityException ignored) {}
            }
            try { gatt.close(); } catch (Throwable ignored) {}
            gatt = null;
        }
        setState(getString(R.string.service_disconnected), Phase.DISCONNECTED);
    }

    private void failAndStop(String message) {
        stopScan();
        handler.removeCallbacks(batteryPoll);
        connecting = false;
        publicBatteryLevel = -1;
        batteryCharacteristic = null;
        InputRouter.reset();
        setAccessibilityConnectionActive(false);
        if (gatt != null) {
            if (hasBluetoothPermissions()) {
                try { gatt.disconnect(); } catch (Throwable ignored) {}
            }
            try { gatt.close(); } catch (Throwable ignored) {}
            gatt = null;
        }
        setState(message, Phase.ERROR);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void setAccessibilityConnectionActive(boolean active) {
        CursorAccessibilityService service = CursorAccessibilityService.getInstance();
        if (service != null) service.setPokeballConnected(active);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(scanTimeout);
        handler.removeCallbacks(batteryPoll);
        if (environmentReceiverRegistered) {
            try { unregisterReceiver(environmentReceiver); } catch (Throwable ignored) {}
            environmentReceiverRegistered = false;
        }
        setAccessibilityConnectionActive(false);
        stopScan();
        if (gatt != null) {
            try { gatt.close(); } catch (Throwable ignored) {}
            gatt = null;
        }
        super.onDestroy();
    }

    private void setState(String text, Phase phase) {
        publicState = text;
        publicPhase = phase;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPending = PendingIntent.getActivity(
                this, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pokeball)
                .setContentIntent(contentPending)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE);

        if (publicPhase == Phase.CONNECTED) {
            DeviceProfileStore.Profile profile = DeviceProfileStore.get().activeProfile();
            String displayName = profile != null ? profile.name : publicDeviceName;
            String battery = publicBatteryLevel >= 0
                    ? getString(R.string.notification_battery, publicBatteryLevel)
                    : getString(R.string.battery_unknown);
            builder.setContentTitle(getString(R.string.notification_connected_title, displayName))
                    .setContentText(battery);

            Intent settingsIntent = new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_OPEN_PROFILE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent settingsPending = PendingIntent.getActivity(this, 11, settingsIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent disconnectIntent = new Intent(this, PokeballService.class).setAction(ACTION_DISCONNECT);
            PendingIntent disconnectPending = PendingIntent.getService(this, 12, disconnectIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(new Notification.Action.Builder(R.drawable.ic_link, getString(R.string.action_settings), settingsPending).build())
                    .addAction(new Notification.Action.Builder(R.drawable.ic_bluetooth, getString(R.string.action_disconnect), disconnectPending).build());
        } else {
            builder.setContentTitle(getString(R.string.app_name)).setContentText(text);
        }
        return builder.build();
    }

    @Override public void onDestroy() {
        if (environmentReceiverRegistered) {
            try { unregisterReceiver(environmentReceiver); } catch (Throwable ignored) {}
            environmentReceiverRegistered = false;
        }
        handler.removeCallbacksAndMessages(null);
        disconnect();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
