package io.soundconnect.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** Exercises the actual Android/JNI engine with upstream recorded hardware packets. */
@RunWith(AndroidJUnit4.class)
public class EngineTest {
    @Test public void testActivityLaunchesOnSupportedAndroid() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Intent intent = new android.content.Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity = instrumentation.startActivitySync(intent);
        try {
            instrumentation.runOnMainSync(() -> {
                assertNotNull(activity.findViewById(android.R.id.content));
                assertFalse(activity.isFinishing());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
    private static class ReplayTransport extends BluetoothTransport {
        final byte[] rx;
        int offset;
        int sent;
        int reads;
        ReplayTransport(byte[] rx) { this.rx = rx; }
        @Override public int read(byte[] dst) {
            // Real devices respond only after a request, with idle gaps and
            // partial frames. Preloading all RX used to hide a transport deadlock.
            if (sent == 0 || reads++ % 2 == 0) return 0;
            int size = Math.min(64, Math.min(dst.length, rx.length - offset));
            System.arraycopy(rx, offset, dst, 0, size);
            offset += size;
            return size;
        }
        @Override public boolean write(byte[] data) { sent += data.length; return true; }
        @Override public int ready() { return sent == 0 || offset == rx.length ? 0 : 1; }
    }

    @Test public void testInitialRequestIsSentBeforeAnyResponse() throws Exception {
        ReplayTransport transport = new ReplayTransport(new byte[0]);
        long handle = NativeEngine.create(transport, 1);
        assertTrue(handle != 0);
        try {
            for (int i = 0; i < 8; i++) assertEquals(0, NativeEngine.tick(handle));
            assertTrue("An idle connected socket must send the first request", transport.sent > 0);
            assertEquals(0, new JSONObject(NativeEngine.snapshot(handle)).optInt("ready"));
        } finally { NativeEngine.destroy(handle); }
    }

    @Test public void testClosedTransportReportsDisconnect() {
        BluetoothTransport transport = new BluetoothTransport();
        assertEquals(0, transport.ready());
        transport.close(); transport.close();
        assertEquals(-1, transport.ready());
        assertEquals(-1, transport.read(new byte[128]));
        assertFalse(transport.write(new byte[]{1}));
    }

    @Test public void testXm3ReplayInitializesThroughJni() throws Exception {
        replay("xm3", 1, "WH-1000XM3", true);
    }
    @Test public void testClipV2ReplayInitializesThroughJni() throws Exception {
        replay("clip", 2, "LinkBuds Clip", true);
    }
    @Test public void testIncompleteXm5CaptureDoesNotReportReady() throws Exception {
        // This upstream capture ends before LOG_SET_STATUS's ACK; see engine_replay.rs.
        replay("xm5", 2, "WF-1000XM5", false);
    }
    private void replay(String directory, int protocol, String expectedName, boolean ready) throws Exception {
        android.content.res.AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        String[] files = assets.list(directory);
        assertNotNull(files);
        Arrays.sort(files);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (String file : files) {
            if (!file.contains("-rx.") || !file.endsWith(".bin")) continue;
            try (InputStream input = assets.open(directory + "/" + file)) {
                byte[] data = new byte[4096]; int size;
                while ((size = input.read(data)) != -1) stream.write(data, 0, size);
            }
        }
        assertTrue("Recorded RX fixtures must be present", stream.size() > 0);
        ReplayTransport transport = new ReplayTransport(stream.toByteArray());
        long handle = NativeEngine.create(transport, protocol);
        assertTrue("Engine must be created", handle != 0);
        try {
            JSONObject snapshot = null;
            for (int i = 0; i < 2000; i++) {
                int result = NativeEngine.tick(handle);
                assertEquals("Engine poll result", 0, result);
                snapshot = new JSONObject(NativeEngine.snapshot(handle));
                if (snapshot.optInt("ready") == 1) break;
            }
            assertNotNull(snapshot);
            assertEquals("Only complete captures may report ready", ready ? 1 : 0, snapshot.optInt("ready"));
            assertTrue(NativeEngine.text(handle, 1, 0).contains(expectedName));
            assertTrue("Must send initialization commands", transport.sent > 0);
            assertTrue("Must obtain advertised features", snapshot.getJSONArray("features").length() >= 27);
            if (ready) {
                assertTrue("EQ must expose actual bands", snapshot.getJSONArray("bands").length() > 0);
                int before = transport.sent;
                assertEquals("Noise change must stage successfully", 0, NativeEngine.command(handle, 0, 1, null));
                for (int i = 0; i < 16; i++) {
                    int result = NativeEngine.tick(handle);
                    assertTrue("Notifications must not become disconnect errors", result >= -2 && result <= 0);
                }
                assertTrue("Noise commit must transmit a command", transport.sent > before);
            } else {
                assertEquals("Controls must reject commands before initialization", 1, NativeEngine.command(handle, 0, 1, null));
            }
        } finally { NativeEngine.destroy(handle); }
    }
}
