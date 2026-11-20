package io.soundconnect.android;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceUiTest {
    /** Renders real libmdr state from the recording, rather than inventing a production demo. */
    @Test public void recordedDeviceScreensPreserveNavigationAndRemoveProtocolPicker() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        android.content.res.AssetManager assets=instrumentation.getContext().getAssets();
        String[] files=assets.list("xm3");assertNotNull(files);Arrays.sort(files);
        for(String file:files)try(InputStream input=assets.open("xm3/"+file)){byte[] data=new byte[4096];int n;while((n=input.read(data))!=-1)stream.write(data,0,n);}
        final byte[] rx=stream.toByteArray();
        BluetoothTransport transport=new BluetoothTransport(){int offset;
            @Override public int ready(){return 1;}
            @Override public boolean write(byte[] bytes){return true;}
            @Override public int read(byte[] dst){int n=Math.min(dst.length,rx.length-offset);System.arraycopy(rx,offset,dst,0,n);offset+=n;return n;}
        };
        long handle=NativeEngine.create(transport,1);assertTrue(handle!=0);
        JSONObject snapshot=null;String model,version;String[] presets;
        try{
            for(int i=0;i<2000;i++){assertEquals(0,NativeEngine.tick(handle));snapshot=new JSONObject(NativeEngine.snapshot(handle));if(snapshot.optInt("ready")==1)break;}
            assertNotNull(snapshot);assertEquals(1,snapshot.optInt("ready"));snapshot.put("protocol",1);
            model=NativeEngine.text(handle,1,0);version=NativeEngine.text(handle,3,0);
            presets=new String[snapshot.getJSONArray("presets").length()];for(int i=0;i<presets.length;i++)presets[i]=NativeEngine.text(handle,17,i);
        }finally{NativeEngine.destroy(handle);}
        String original=Appearance.selected(instrumentation.getTargetContext());
        try{for(String mode:new String[]{Appearance.LIGHT,Appearance.DARK}){
        Appearance.save(instrumentation.getTargetContext(),mode);
        Intent intent=new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(intent);
        JSONObject recorded=snapshot;
        try{
            instrumentation.runOnMainSync(()->{
                activity.showDeviceState(recorded,model,version,presets);
                assertNotNull(find(activity.findViewById(android.R.id.content),"マイデバイス"));
                assertNotNull(find(activity.findViewById(android.R.id.content),"外音コントロール"));
                assertNull(find(activity.findViewById(android.R.id.content),"通信方式"));
            });
            capture(instrumentation,mode+"-home");
            instrumentation.runOnMainSync(()->activity.navigate("equalizer"));capture(instrumentation,mode+"-equalizer");
            instrumentation.runOnMainSync(()->{
                assertNotNull(find(activity.findViewById(android.R.id.content),"CLEAR BASS  "+recorded.optInt("bass")));
                activity.navigate("settings");
                assertNotNull(find(activity.findViewById(android.R.id.content),"すべてのデバイス設定"));
            });
            capture(instrumentation,mode+"-settings");
            instrumentation.runOnMainSync(()->activity.navigate("noise"));capture(instrumentation,mode+"-noise");
            instrumentation.runOnMainSync(()->{
                assertNotNull(find(activity.findViewById(android.R.id.content),"ボイスフォーカス"));
                activity.navigate("menu");assertNotNull(find(activity.findViewById(android.R.id.content),"ライセンス"));
            });
        }finally{instrumentation.runOnMainSync(activity::finish);}
        }}finally{Appearance.save(instrumentation.getTargetContext(),original);}
    }
    private static View find(View view,String label){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private static void capture(Instrumentation instrumentation,String name)throws Exception{
        instrumentation.waitForIdleSync();Thread.sleep(150);
        Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(screenshot);
        File dir=instrumentation.getTargetContext().getExternalFilesDir("screenshots");assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        try(OutputStream output=new FileOutputStream(new File(dir,name+".png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,output));}
        screenshot.recycle();
    }
}
