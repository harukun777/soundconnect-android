package io.soundconnect.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AutoConnectionTest {
    @Test public void advertisedServicesOverrideModelAndHistory() {
        assertArrayEquals(new int[]{2,1},ProtocolSelector.order(new String[]{BluetoothTransport.MODERN},"WH-1000XM4",1));
        assertArrayEquals(new int[]{1,2},ProtocolSelector.order(new String[]{BluetoothTransport.LEGACY},"WF-1000XM5",2));
    }
    @Test public void missingServicesUseSuccessfulHistoryThenModel() {
        assertArrayEquals(new int[]{1,2},ProtocolSelector.order(null,"unknown",1));
        assertArrayEquals(new int[]{1,2},ProtocolSelector.order(null,"WH-1000XM4",0));
        assertArrayEquals(new int[]{2,1},ProtocolSelector.order(null,"WF-1000XM5",0));
        assertFalse(ProtocolSelector.isSony("Desktop PC"));
        assertTrue(ProtocolSelector.isSony("LinkBuds S"));
    }
    private static final class Fake implements AutoConnection.Connection {
        boolean closed;
        @Override public void command(int kind,int value,int[] values) { }
        @Override public void close() { closed=true; }
    }
    private static final class Listener implements HeadphoneSession.Listener {
        int states,disconnections;
        @Override public void onState(JSONObject state,String model,String firmware,String[] presets) { states++; }
        @Override public void onStatus(String message,boolean disconnected) { if(disconnected)disconnections++; }
        @Override public void onNotice(String message,boolean choice) { }
    }
    @Test public void setupFailureFallsBackOnceAndRemembersOnlySuccess() throws Exception {
        List<Integer> opened=new ArrayList<>(), remembered=new ArrayList<>();
        List<HeadphoneSession.Listener> callbacks=new ArrayList<>(); List<Fake> sessions=new ArrayList<>(); Listener events=new Listener();
        AutoConnection connection=new AutoConnection(new int[]{1,2},(protocol,listener)->{
            opened.add(protocol);callbacks.add(listener);Fake f=new Fake();sessions.add(f);return f;
        },events,remembered::add);
        callbacks.get(0).onStatus("timeout",true);
        assertEquals(Arrays.asList(1,2),opened);assertTrue(sessions.get(0).closed);assertTrue(remembered.isEmpty());
        callbacks.get(0).onState(new JSONObject(),"stale","",new String[0]);assertEquals(0,events.states);
        JSONObject state=new JSONObject();callbacks.get(1).onState(state,"headphones","",new String[0]);
        assertEquals(Arrays.asList(2),remembered);assertEquals(2,state.getInt("protocol"));
        callbacks.get(1).onStatus("another app took over",true);
        assertEquals(2,opened.size());assertEquals(1,events.disconnections);connection.close();assertTrue(sessions.get(1).closed);
    }
    @Test public void cancellationPreventsFallbackAndLateCallbacks() {
        List<HeadphoneSession.Listener> callbacks=new ArrayList<>();Listener events=new Listener();
        AutoConnection connection=new AutoConnection(new int[]{2,1},(protocol,listener)->{callbacks.add(listener);return new Fake();},events,p->fail("must not remember canceled attempt"));
        connection.close();callbacks.get(0).onStatus("timeout",true);callbacks.get(0).onState(new JSONObject(),"late","",new String[0]);
        assertEquals(1,callbacks.size());assertEquals(0,events.states);
    }
}
