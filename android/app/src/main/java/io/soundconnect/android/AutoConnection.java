package io.soundconnect.android;

import org.json.JSONObject;
import java.util.function.IntConsumer;

/** Exactly one fallback during setup; never fights another controller after a live disconnect. */
final class AutoConnection implements AutoCloseable {
    interface Connection extends AutoCloseable {
        void command(int kind, int value, int[] values);
        @Override void close();
    }
    interface Factory { Connection open(int protocol, HeadphoneSession.Listener listener); }
    private final int[] order;
    private final Factory factory;
    private final HeadphoneSession.Listener listener;
    private final IntConsumer remember;
    private Connection current;
    private int attempt;
    private boolean closed, connected;

    AutoConnection(int[] order, Factory factory, HeadphoneSession.Listener listener, IntConsumer remember) {
        this.order = order.clone(); this.factory = factory; this.listener = listener; this.remember = remember;
        open();
    }
    private void open() {
        final int index = attempt;
        current = factory.open(order[index], new HeadphoneSession.Listener() {
            private boolean valid() { return !closed && attempt == index; }
            @Override public void onState(JSONObject state, String model, String firmware, String[] presets) {
                if (!valid()) return;
                if (!connected) { connected = true; remember.accept(order[index]); }
                try { state.put("protocol", order[index]); } catch (org.json.JSONException ignored) { }
                listener.onState(state, model, firmware, presets);
            }
            @Override public void onStatus(String message, boolean disconnected) {
                if (!valid()) return;
                if (disconnected && !connected && attempt + 1 < order.length) {
                    if (current != null) current.close();
                    attempt++;
                    listener.onStatus("接続方法を自動調整しています…", false);
                    open();
                } else listener.onStatus(message, disconnected);
            }
            @Override public void onNotice(String message, boolean requiresChoice) {
                if (valid()) listener.onNotice(message, requiresChoice);
            }
        });
    }
    void command(int kind, int value, int[] values) { if (!closed && current != null) current.command(kind, value, values); }
    @Override public void close() { closed = true; if (current != null) current.close(); }
}
