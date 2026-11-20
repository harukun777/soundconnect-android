package io.soundconnect.android;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the native engine on a single worker; close() cancels even a blocked connect(). */
final class HeadphoneSession implements AutoCloseable {
    interface Listener {
        void onState(JSONObject state, String model, String firmware, String[] presets);
        void onStatus(String message, boolean disconnected);
        void onNotice(String message, boolean requiresChoice);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final BluetoothTransport transport = new BluetoothTransport();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Listener listener;
    private long handle;
    private long deadline;
    private String previous = "";
    private final Runnable connectionTimeout = () -> fail("接続がタイムアウトしました。ヘッドホンを接続し直してください。");

    HeadphoneSession(BluetoothDevice device, int protocol, Listener listener) {
        this.listener = listener;
        main.postDelayed(connectionTimeout, 20000);
        worker.execute(() -> {
            try {
                transport.connect(device, protocol);
                if (closed.get()) return;
                handle = NativeEngine.create(transport, protocol);
                if (handle == 0) throw new IOException("通信エンジンを開始できませんでした");
                main.removeCallbacks(connectionTimeout);
                deadline = SystemClock.elapsedRealtime() + 45000;
                postStatus("ヘッドホンの設定を取得しています…", false);
                worker.scheduleWithFixedDelay(this::tick, 0, 20, TimeUnit.MILLISECONDS);
            } catch (Exception | LinkageError e) {
                fail("接続できませんでした。Sonyアプリを閉じ、機器と通信方式を確認してください。\n" + e.getMessage());
            }
        });
    }

    private void tick() {
        if (closed.get() || handle == 0) return;
        try {
            int result = NativeEngine.tick(handle);
            if (result == -1 || result == -2) {
                String notice = NativeEngine.text(handle, result == -1 ? 14 : 15, 0);
                main.post(() -> { if (!closed.get()) listener.onNotice(notice, result == -1); });
            } else if (result != 0) {
                String detail = NativeEngine.text(handle, 13, 0);
                fail("通信が終了しました。再接続してください。" + (detail.isEmpty() ? "" : "\n" + detail));
                return;
            }
            String json = NativeEngine.snapshot(handle);
            JSONObject state = new JSONObject(json);
            if (state.optInt("ready") == 0) {
                if (SystemClock.elapsedRealtime() > deadline) fail("設定の取得がタイムアウトしました。機器と通信方式を確認してください。");
                return;
            }
            deadline = SystemClock.elapsedRealtime() + 45000;
            if (json.equals(previous)) return;
            previous = json;
            String model = NativeEngine.text(handle, 1, 0);
            String firmware = NativeEngine.text(handle, 3, 0);
            int count = state.getJSONArray("presets").length();
            String[] presets = new String[count];
            for (int i = 0; i < count; i++) presets[i] = NativeEngine.text(handle, 17, i);
            main.post(() -> { if (!closed.get()) listener.onState(state, model, firmware, presets); });
        } catch (Exception e) { fail("通信エラーが発生しました。再接続してください。\n" + e.getMessage()); }
    }

    void command(int kind, int value, int[] values) {
        if (closed.get()) return;
        worker.execute(() -> {
            if (closed.get() || handle == 0) return;
            int result = NativeEngine.command(handle, kind, value, values);
            if (result != 0) {
                postStatus(result == 1 ? "設定を反映中です。少し待ってから操作してください。"
                    : "設定を変更できませんでした（コード " + result + "）。", false);
                // Rebuild controls from confirmed state rather than leaving a false visual value.
                previous = "";
            }
        });
    }

    private void postStatus(String message, boolean disconnected) {
        main.post(() -> { if (!closed.get()) listener.onStatus(message, disconnected); });
    }

    private void fail(String message) {
        if (closed.get()) return;
        main.post(() -> {
            if (!closed.get()) { listener.onStatus(message, true); close(); }
        });
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        main.removeCallbacks(connectionTimeout);
        transport.close();
        worker.execute(() -> { if (handle != 0) { NativeEngine.destroy(handle); handle = 0; } });
        worker.shutdown();
    }
}
