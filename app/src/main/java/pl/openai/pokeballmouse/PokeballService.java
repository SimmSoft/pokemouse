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
import android.content.pm.PackageManager;
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

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic batteryCharacteristic;
    private boolean scanning;
    private boolean connecting;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable scanTimeout = () -> {
        if (scanning) {
            stopScan();
            setState(getString(R.string.service_not_found), Phase.ERROR);
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
    public static boolean isConnected() { return publicPhase == Phase.CONNECTED; }

    @Override public void onCreate() {
        super.onCreate();
        LanguagePrefs.apply(this);
        createNotificationChannel();
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
        return START_STICKY;
    }

    private void startScan() {
        if (!hasBluetoothPermissions()) {
            setState(getString(R.string.service_missing_bt_permissions), Phase.ERROR);
            return;
        }
        if (scanning || connecting || gatt != null) return;

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            setState(getString(R.string.service_bt_off), Phase.ERROR);
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            setState(getString(R.string.service_no_scanner), Phase.ERROR);
            return;
        }
        publicBatteryLevel = -1;
        scanning = true;
        setState(getString(R.string.service_searching), Phase.SEARCHING);
        try {
            scanner.startScan(scanCallback);
        } catch (SecurityException ex) {
            scanning = false;
            setState(getString(R.string.service_missing_bt_permissions), Phase.ERROR);
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
                setState(getString(R.string.service_missing_bt_permissions), Phase.ERROR);
                return;
            }
            if (name == null) return;
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.contains("pokeball") && !normalized.contains("pokemon pbp")) return;

            stopScan();
            if (connecting || gatt != null) return;
            connecting = true;
            PhoneFeedback.lightDetectedVibration(PokeballService.this);
            setState(getString(R.string.service_connecting), Phase.CONNECTING);
            try {
                gatt = result.getDevice().connectGatt(
                        PokeballService.this, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE);
            } catch (SecurityException ex) {
                connecting = false;
                setState(getString(R.string.service_missing_bt_permissions), Phase.ERROR);
            }
        }

        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            setState(getString(R.string.status_connection_error) + " (BLE " + errorCode + ")", Phase.ERROR);
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
                try { bluetoothGatt.close(); } catch (Throwable ignored) {}
                if (gatt == bluetoothGatt) gatt = null;
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState(getString(R.string.status_connection_error) + " (GATT " + status + ")", Phase.ERROR);
                return;
            }
            BluetoothGattService inputService = bluetoothGatt.getService(INPUT_SERVICE_UUID);
            BluetoothGattCharacteristic input = inputService != null
                    ? inputService.getCharacteristic(INPUT_CHARACTERISTIC_UUID) : null;
            if (input == null) {
                setState(getString(R.string.service_input_missing), Phase.ERROR);
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
                handler.removeCallbacks(batteryPoll);
                handler.postDelayed(batteryPoll, 500L);
            } else {
                setState(getString(R.string.service_notifications_failed), Phase.ERROR);
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
                setState(getString(R.string.service_notifications_failed), Phase.ERROR);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33) {
                int result = bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (result != 0) setState(getString(R.string.service_notifications_failed), Phase.ERROR);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!bluetoothGatt.writeDescriptor(descriptor)) {
                    setState(getString(R.string.service_notifications_failed), Phase.ERROR);
                }
            }
        } catch (SecurityException ex) {
            setState(getString(R.string.service_missing_bt_permissions), Phase.ERROR);
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
        InputRouter.reset();
        if (gatt != null) {
            if (hasBluetoothPermissions()) {
                try { gatt.disconnect(); } catch (SecurityException ignored) {}
            }
            try { gatt.close(); } catch (Throwable ignored) {}
            gatt = null;
        }
        setState(getString(R.string.service_disconnected), Phase.DISCONNECTED);
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
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pokeball)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        disconnect();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
