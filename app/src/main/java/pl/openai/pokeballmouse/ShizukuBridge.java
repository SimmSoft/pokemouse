package pl.openai.pokeballmouse;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public interface ResultCallback { void onResult(boolean success); }
    private static final int REQUEST_CODE = 7001;
    private static volatile ShizukuBridge instance;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<float[]> latestMove = new AtomicReference<>();
    private final AtomicBoolean moveDrainRunning = new AtomicBoolean(false);
    private final AtomicBoolean binding = new AtomicBoolean(false);
    private volatile IPrivilegedInput remote;
    private final CopyOnWriteArrayList<Runnable> readyCallbacks = new CopyOnWriteArrayList<>();
    private volatile String status = "Shizuku nieaktywne";

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::onBinderReceived;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::onBinderDead;
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindUserService();
                } else {
                    status = "Brak zgody Shizuku";
                }
            };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = IPrivilegedInput.Stub.asInterface(service);
            binding.set(false);
            status = "Shizuku: sterowanie systemowym wejściem aktywne";
            InputRouter.onShizukuReady();
            for (Runnable callback : readyCallbacks) {
                try { callback.run(); } catch (Throwable ignored) {}
            }
            readyCallbacks.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            binding.set(false);
            status = "Shizuku: usługa rozłączona";
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs;

    private ShizukuBridge(Context context) {
        this.context = context.getApplicationContext();
        ComponentName component = new ComponentName(this.context, PrivilegedInputService.class);
        userServiceArgs = new Shizuku.UserServiceArgs(component)
                .processNameSuffix("input")
                .tag("pokeball-mouse-input")
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (ShizukuBridge.class) {
                if (instance == null) instance = new ShizukuBridge(context);
            }
        }
    }

    public static ShizukuBridge get() { return instance; }

    public String status() { return status; }
    public boolean isReady() { return remote != null; }

    public void requestPermissionAndBind() {
        try {
            if (!Shizuku.pingBinder()) {
                status = "Uruchom Shizuku, potem spróbuj ponownie";
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindUserService();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                status = "Zgoda Shizuku została wcześniej odrzucona";
            } else {
                status = "Czekam na zgodę Shizuku…";
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            status = "Błąd Shizuku: " + t.getClass().getSimpleName();
        }
    }

    private void onBinderReceived() {
        status = "Shizuku działa — sprawdzam zgodę";
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindUserService();
            }
        } catch (Throwable t) {
            status = "Shizuku: nie można sprawdzić uprawnienia";
        }
    }

    private void onBinderDead() {
        remote = null;
        binding.set(false);
        status = "Shizuku zatrzymane";
    }

    private void bindUserService() {
        if (remote != null || !binding.compareAndSet(false, true)) return;
        status = "Shizuku: uruchamiam backend myszy…";
        try {
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable t) {
            binding.set(false);
            status = "Shizuku bind: " + t.getClass().getSimpleName();
        }
    }

    public void whenReady(Runnable callback) {
        if (callback == null) return;
        if (isReady()) { callback.run(); return; }
        readyCallbacks.add(callback);
    }

    public void setAccessibilityService(String componentName, boolean enabled, ResultCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            IPrivilegedInput r = remote;
            if (r != null) {
                try { success = r.setAccessibilityService(componentName, enabled); }
                catch (RemoteException e) { remote = null; }
            }
            if (callback != null) {
                try { callback.onResult(success); } catch (Throwable ignored) {}
            }
        });
    }

    public void move(float x, float y) {
        if (remote == null) return;
        latestMove.set(new float[]{x, y});
        if (moveDrainRunning.compareAndSet(false, true)) {
            executor.execute(this::drainMoves);
        }
    }

    private void drainMoves() {
        try {
            while (true) {
                float[] p = latestMove.getAndSet(null);
                if (p == null) break;
                IPrivilegedInput r = remote;
                if (r == null) break;
                try {
                    r.injectMouseMove(p[0], p[1]);
                } catch (RemoteException e) {
                    remote = null;
                    status = "Shizuku: utracono backend wejścia";
                    break;
                }
            }
        } finally {
            moveDrainRunning.set(false);
            if (latestMove.get() != null && remote != null && moveDrainRunning.compareAndSet(false, true)) {
                executor.execute(this::drainMoves);
            }
        }
    }

    public void button(float x, float y, int button, boolean down) {
        executor.execute(() -> {
            IPrivilegedInput r = remote;
            if (r == null) return;
            try {
                r.injectMouseButton(x, y, button, down);
            } catch (RemoteException e) {
                remote = null;
                status = "Shizuku: utracono backend wejścia";
            }
        });
    }

    public void touch(int pointerId, float x, float y, boolean down) {
        executor.execute(() -> {
            IPrivilegedInput r = remote;
            if (r == null) return;
            try { r.injectTouchPointer(pointerId, x, y, down); }
            catch (RemoteException e) {
                remote = null;
                status = "Shizuku: utracono backend wejścia";
            }
        });
    }

    public void tap(float x, float y) {
        executor.execute(() -> {
            IPrivilegedInput r = remote;
            if (r == null) return;
            try { r.injectTap(x, y); }
            catch (RemoteException e) {
                remote = null;
                status = "Shizuku: utracono backend wejścia";
            }
        });
    }

    public void key(int keyCode) {
        executor.execute(() -> {
            IPrivilegedInput r = remote;
            if (r == null) return;
            try { r.injectKey(keyCode); } catch (RemoteException ignored) {}
        });
    }
}
