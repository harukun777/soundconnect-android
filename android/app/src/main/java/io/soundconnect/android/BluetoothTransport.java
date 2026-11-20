package io.soundconnect.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Bounded receive ring. A blocking reader detects EOF without blocking engine polling. */
class BluetoothTransport implements AutoCloseable {
    static final String LEGACY = "96CC203E-5068-46AD-B32D-E316F5E069BA";
    static final String MODERN = "956C7B26-D49A-4BA8-B03F-B17D393CB6E2";
    private final byte[] buffer = new byte[65536];
    private int head, count;
    private volatile boolean closed;
    private volatile BluetoothSocket socket;

    @SuppressLint("MissingPermission") // Activity requests CONNECT on API 31+ before selecting devices.
    void connect(BluetoothDevice device, int protocol) throws IOException {
        BluetoothSocket candidate = device.createRfcommSocketToServiceRecord(
            UUID.fromString(protocol == 1 ? LEGACY : MODERN));
        synchronized (this) {
            if (closed) { candidate.close(); throw new IOException("接続を中止しました"); }
            socket = candidate;
        }
        candidate.connect();
        if (closed) throw new IOException("接続を中止しました");
        InputStream input = candidate.getInputStream();
        Thread reader = new Thread(() -> {
            byte[] incoming = new byte[4096];
            try {
                int size;
                while ((size = input.read(incoming)) != -1) {
                    synchronized (this) {
                        if (closed || size > buffer.length - count) break;
                        for (int i = 0; i < size; i++) buffer[(head + count + i) % buffer.length] = incoming[i];
                        count += size;
                    }
                }
            } catch (IOException ignored) {
                // Poll reports a closed socket to the engine and UI.
            } finally { close(); }
        }, "soundconnect-bluetooth-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public synchronized int read(byte[] destination) {
        if (closed) return -1;
        int size = Math.min(count, destination.length);
        for (int i = 0; i < size; i++) destination[i] = buffer[(head + i) % buffer.length];
        head = (head + size) % buffer.length;
        count -= size;
        return size;
    }

    public boolean write(byte[] data) {
        try {
            BluetoothSocket current = socket;
            if (closed || current == null) return false;
            current.getOutputStream().write(data);
            return true;
        } catch (IOException e) { close(); return false; }
    }

    public synchronized int ready() { return closed ? -1 : count > 0 ? 1 : 0; }

    @Override public void close() {
        BluetoothSocket current;
        synchronized (this) { closed = true; current = socket; }
        if (current != null) try { current.close(); } catch (IOException ignored) { }
    }
}
