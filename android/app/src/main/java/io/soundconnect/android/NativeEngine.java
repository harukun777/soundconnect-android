package io.soundconnect.android;

final class NativeEngine {
    static { System.loadLibrary("soundconnect"); }
    private NativeEngine() {}
    static native long create(BluetoothTransport transport, int protocol);
    static native void destroy(long handle);
    static native int tick(long handle);
    static native String snapshot(long handle);
    static native String text(long handle, int kind, int index);
    static native int command(long handle, int kind, int value, int[] values);
}
