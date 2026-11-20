package io.soundconnect.android;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothAdapter adapter;
    private AutoConnection connection;
    private Ui ui;
    private LinearLayout root, body, navigation;
    private ScrollView scroll;
    private String page = "home", model = "", firmware = "", message = "ヘッドホンが接続されていません";
    private String[] presets = new String[0];
    private JSONObject state;
    private boolean active, editing, connecting;
    private String artworkKey = "";
    private Bitmap artwork;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Appearance.wrap(base));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = new Ui(this);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        model = getPreferences(MODE_PRIVATE).getString("name", "");
        if (saved != null) page = saved.getString("page", "home");
        root = ui.column(); root.setBackgroundColor(ui.color(R.color.surface));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = ui.column(); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = ui.horizontal(); navigation.setBackgroundColor(ui.color(R.color.navigation));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, ui.dim(R.dimen.navigation_height)));
        setContentView(root); render(false);
    }
    @Override protected void onSaveInstanceState(Bundle saved) { saved.putString("page", page); super.onSaveInstanceState(saved); }
    @Override protected void onStart() { super.onStart(); active = true; loadDevices(true); }
    @Override protected void onStop() { active = false; disconnect(); super.onStop(); }
    @Override public void onBackPressed() { if (!page.equals("home")) navigate("home"); else super.onBackPressed(); }
    private boolean permitted() { return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; }

    @android.annotation.SuppressLint("MissingPermission")
    private void loadDevices(boolean automatic) {
        if (connection != null) return;
        devices.clear();
        if (adapter == null) { message = "この端末はBluetoothに対応していません"; render(true); return; }
        if (!permitted()) { message = "付近のデバイスへの接続を許可してください"; render(true); return; }
        try {
            if (!adapter.isEnabled()) { message = "Bluetoothをオンにしてください"; render(true); return; }
            devices.addAll(adapter.getBondedDevices());
            devices.sort(Comparator.comparing(d -> (ProtocolSelector.isSony(d.getName()) ? "0" : "1") + deviceName(d)));
            if (automatic) {
                String mac = getPreferences(MODE_PRIVATE).getString("device", "");
                for (BluetoothDevice d : devices) if (d.getAddress().equals(mac)) { connect(d,false); return; }
                BluetoothDevice candidate = null; int count = 0;
                for (BluetoothDevice d : devices) if (ProtocolSelector.isSony(d.getName())) { candidate = d; count++; }
                if (count == 1) { connect(candidate,false); return; }
            }
        } catch (SecurityException e) { message = "Bluetoothの接続権限を許可してください"; }
        render(true);
    }
    @android.annotation.SuppressLint("MissingPermission")
    private String deviceName(BluetoothDevice d) { String n = d.getName(); return n == null || n.isEmpty() ? d.getAddress() : n; }
    private void chooseDevice() {
        if (!permitted() && Build.VERSION.SDK_INT >= 31) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1); return; }
        loadDevices(false);
        String[] names = new String[devices.size()];
        for (int i=0; i<names.length; i++) names[i] = deviceName(devices.get(i));
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("接続するデバイスを選択")
            .setNegativeButton("閉じる", null).setNeutralButton("新しい機器を登録", (d,w) -> bluetoothSettings());
        if (names.length == 0) dialog.setMessage("AndroidのBluetooth設定でヘッドホンをペアリングしてください。");
        else dialog.setItems(names, (d,i) -> connect(devices.get(i)));
        dialog.show();
    }
    private void bluetoothSettings() { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (active && request == 1) { loadDevices(true); if (connection == null && permitted()) chooseDevice(); }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        connect(device,true);
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device,boolean openHome) {
        disconnect(); model = deviceName(device); firmware = ""; connecting = true; if(openHome)page = "home";
        message = "接続しています…"; render(false);
        String mac = device.getAddress();
        ParcelUuid[] cached = device.getUuids();
        String[] services = cached == null ? new String[0] : Arrays.stream(cached).map(Object::toString).toArray(String[]::new);
        int remembered = getPreferences(MODE_PRIVATE).getInt("protocol:" + mac, 0);
        int[] order = ProtocolSelector.order(services, model, remembered);
        connection = new AutoConnection(order, (protocol, listener) -> new HeadphoneSession(device, protocol, listener),
            new HeadphoneSession.Listener() {
                @Override public void onState(JSONObject snapshot, String name, String version, String[] names) {
                    if (!active) return;
                    connecting = false; message = "接続済み";
                    getPreferences(MODE_PRIVATE).edit().putString("device", mac).putString("name", name.isEmpty()?model:name).apply();
                    showDeviceState(snapshot, name, version, names);
                }
                @Override public void onStatus(String text, boolean disconnected) {
                    if (!active) return;
                    if (disconnected) disconnect(); message = text; render(true);
                }
                @Override public void onNotice(String text, boolean choice) {
                    if (!active || connection == null) return;
                    if (!choice) { if (!text.isEmpty()) Toast.makeText(MainActivity.this,text,Toast.LENGTH_LONG).show(); return; }
                    AutoConnection target = connection;
                    new AlertDialog.Builder(MainActivity.this).setTitle("ヘッドホンからの確認")
                        .setMessage(text.isEmpty()?"この操作を実行しますか？":text)
                        .setPositiveButton("実行",(d,w)->{ if(connection==target)target.command(15,1,null); })
                        .setNegativeButton("キャンセル",(d,w)->{ if(connection==target)target.command(15,0,null); })
                        .setOnCancelListener(d->{ if(connection==target)target.command(15,0,null); }).show();
                }
            }, protocol -> getPreferences(MODE_PRIVATE).edit().putInt("protocol:"+mac,protocol).apply());
    }
    private void disconnect() {
        if (connection != null) { connection.close(); connection = null; }
        state = null; connecting = false; editing = false; message = "ヘッドホンが接続されていません";
    }
    // Also used by instrumentation to render actual recorded device states, without a production demo mode.
    void showDeviceState(JSONObject snapshot, String name, String version, String[] names) {
        state=snapshot; if(!name.isEmpty())model=name; firmware=version; presets=names.clone();
        if(!editing)render(true);
    }
    void navigate(String destination) { page=destination; render(false); }
    private boolean feature(int id) { return state != null && state.optJSONArray("features") != null && state.optJSONArray("features").optInt(id-1)==1; }
    private int value(String key) { return state == null?0:state.optInt(key); }
    private String text(String key, String fallback) { String s=state==null?"":state.optString(key); return s.isEmpty()?fallback:s; }
    private void send(int kind, int value) { if(connection!=null)connection.command(kind,value,null); }
    private void render(boolean preserveScroll) {
        if(body==null)return;
        int y=preserveScroll?scroll.getScrollY():0;
        body.removeAllViews(); ui.padding(body,R.dimen.gutter);
        if(page.equals("home")) home();
        else if(page.equals("settings")) settings();
        else if(page.equals("noise")) noise();
        else if(page.equals("equalizer")) equalizer();
        else if(page.equals("scene")) information("シーン", "行動や場所に合わせた自動切り替え", "このAndroid 9版では、場所・行動の学習やシーンの自動再生には対応していません。");
        else if(page.equals("discover")) information("ディスカバー", "音の楽しみ方を見つける", "リスニング履歴やSonyアカウントのサービスはこのアプリには含まれていません。");
        else menu();
        bottomNavigation(); scroll.post(()->scroll.scrollTo(0,y));
    }
    private void heading(String title) {
        LinearLayout bar=ui.horizontal(); bar.addView(ui.icon("back","戻る",()->navigate("home")));
        TextView label=ui.centered(title,R.dimen.section_title); label.setTypeface(null,android.graphics.Typeface.BOLD);
        bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(ui.icon("power","ヘッドホンの電源",this::power)); body.addView(bar,ui.params()); ui.space(body,R.dimen.gutter);
    }
    private void home() {
        LinearLayout tools=ui.horizontal(); tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        tools.addView(ui.icon("power","ヘッドホンの電源",this::power));
        tools.addView(ui.icon("more","デバイスのメニュー",this::deviceMenu)); body.addView(tools);
        View image=productImage(); body.addView(image,new LinearLayout.LayoutParams(-1,ui.dim(R.dimen.hero)));
        TextView title=ui.centered(model.isEmpty()?"ヘッドホンを接続":model,R.dimen.model_title); title.setTypeface(null,android.graphics.Typeface.BOLD); body.addView(title,ui.params());
        ui.space(body,R.dimen.gap);
        TextView battery=ui.centered(batteries(),R.dimen.caption); body.addView(battery,ui.params());
        String codec=codec();
        if(!codec.isEmpty()) { TextView v=ui.centered(codec+(value("dsee")!=0?"  ·  DSEE":""),R.dimen.small); v.setTypeface(null,android.graphics.Typeface.BOLD); body.addView(v,ui.params()); }
        ui.space(body,R.dimen.gutter);
        if(state==null) {
            LinearLayout card=ui.card(body);
            TextView status=ui.centered(message,R.dimen.body); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); card.addView(status,ui.params());
            ui.space(card,R.dimen.gutter);
            if(connecting) {
                ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); card.addView(progress,ui.params());
                ui.space(card,R.dimen.gutter); card.addView(ui.button("接続を中止",false,()->{disconnect();render(true);}));
            } else card.addView(ui.button("デバイスを登録・接続",true,this::chooseDevice));
            ui.space(card,R.dimen.gap); ui.body(card,"Bluetoothでペアリングしたヘッドホンに自動接続します。"); return;
        }
        LinearLayout all=ui.card(body); all.setPadding(ui.dim(R.dimen.gutter),0,ui.dim(R.dimen.gutter),0); ui.row(all,"すべてのデバイス設定",null,()->navigate("settings"));
        ui.space(body,R.dimen.gap); TextView shortcuts=ui.text("ショートカット",R.dimen.body); shortcuts.setTypeface(null,android.graphics.Typeface.BOLD); body.addView(shortcuts); ui.space(body,R.dimen.gap);
        if(feature(8)||feature(9)) {
            LinearLayout card=ui.card(body); ui.row(card,"外音コントロール",null,()->navigate("noise"));
            ui.space(card,R.dimen.gap); ui.choices(card,noiseMode(),n->send(0,n),feature(8),feature(9));
        }
        if(feature(13)&&value("eqAvailable")!=0) {
            LinearLayout card=ui.card(body); ui.row(card,"イコライザー",null,()->navigate("equalizer"));
            card.addView(new Ui.EqualizerChart(this,bands()),new LinearLayout.LayoutParams(-1,ui.dim(R.dimen.chart_preview)));
            card.addView(ui.button("•••••  "+presetName(),false,this::selectPreset),ui.params());
        }
        if(feature(11)) { LinearLayout card=ui.card(body); ui.toggle(card,"Speak-to-Chat",null,value("speech")!=0,n->send(3,n)); }
        if(feature(6)||feature(7)) playback(ui.card(body));
        ui.space(body,R.dimen.gap); body.addView(ui.button("デバイス設定",false,()->navigate("settings")),ui.params());
    }
    private View productImage() {
        String key=artKey(model);
        if(!key.equals(artworkKey)) {
            artworkKey=key; artwork=null;
            if(!key.isEmpty())try(InputStream input=getAssets().open("devices/"+key+".png")) { artwork=BitmapFactory.decodeStream(input); }catch(IOException ignored) { }
        }
        if(artwork==null)return new Ui.Glyph(this,"headphones",false);
        ImageView image=new ImageView(this); image.setImageBitmap(artwork); image.setScaleType(ImageView.ScaleType.FIT_CENTER); ui.padding(image,R.dimen.gutter); image.setContentDescription(model); return image;
    }
    private String artKey(String name) {
        String n=name.toUpperCase(Locale.ROOT);
        if(n.startsWith("LINKBUDS CLIP"))return "WF-LC900";
        if(n.startsWith("LINKBUDS OPEN"))return "WF-L910";
        if(n.startsWith("LINKBUDS S"))return "WF-LS900N";
        try { for(String file:Objects.requireNonNull(getAssets().list("devices"))) {
            String id=file.replace(".png",""); if(n.startsWith(id))return id;
        }}catch(IOException ignored) { }
        return "";
    }
    private String batteries() {
        if(state==null)return connecting?"接続中":"未接続";
        JSONArray bs=state.optJSONArray("battery"); StringBuilder result=new StringBuilder(); String[] names={"", "L ", "R ", "ケース "};
        if(bs!=null)for(int i=0;i<bs.length();i++) { JSONArray b=bs.optJSONArray(i); if(b==null||b.optInt(1)==0)continue;
            int part=b.optInt(0),level=b.optInt(2); if(result.length()>0)result.append("    ");
            result.append(part>=0&&part<names.length?names[part]:"").append(level<=100?level+"%":"—");
            if(b.optInt(3)==2)result.append(" 充電中");
        }
        return result.length()>0?result.toString():"バッテリー情報なし";
    }
    private String codec() { String[] labels={"","SBC","AAC","LDAC","aptX","aptX HD","LC3"}; int n=value("codec"); return n>=0&&n<labels.length?labels[n]:""; }
    private int noiseMode() { int mode=value("noise"); if(value("protocol")==1&&mode!=0)return value("ambient")>=1&&value("ambient")<=20?2:1; return mode; }
    private int[] bands() { JSONArray array=state==null?null:state.optJSONArray("bands"); int[] result=new int[array==null?0:array.length()]; for(int i=0;i<result.length;i++)result[i]=array.optInt(i); return result; }
    private String presetLabel(int index) {
        if(index<presets.length&&!presets[index].isEmpty())return presets[index];
        JSONArray ids=state==null?null:state.optJSONArray("presets");int id=ids==null?0:ids.optInt(index);
        String[] names={"Off","Rock","Pop","Jazz","Dance","EDM","R&B / Hip-Hop","Acoustic","Bright","Excited","Mellow","Relaxed","Vocal","Treble Boost","Bass Boost","Speech","Heavy","Clear","Hard","Soft","Gaming","FPS 1","FPS 2","FPS 3","Manual","Custom 1","Custom 2","Custom 3","Custom 4","Custom 5"};
        return id>=0&&id<names.length?names[id]:"プリセット "+id;
    }
    private int presetIndex(){JSONArray ids=state==null?null:state.optJSONArray("presets");if(ids!=null)for(int i=0;i<ids.length();i++)if(ids.optInt(i)==value("preset"))return i;return 0;}
    private String presetName() { JSONArray ids=state==null?null:state.optJSONArray("presets");return ids!=null&&ids.length()>0?presetLabel(presetIndex()):value("preset")==0?"Off":"Custom"; }
    private void selectPreset() {
        JSONArray ids=state==null?null:state.optJSONArray("presets"); if(ids==null)return;
        String[] labels=new String[ids.length()]; for(int i=0;i<labels.length;i++)labels[i]=presetLabel(i);
        new AlertDialog.Builder(this).setTitle("イコライザー").setSingleChoiceItems(labels,presetIndex(),(d,i)->{send(4,ids.optInt(i));d.dismiss();}).setNegativeButton("キャンセル",null).show();
    }
    private void noise() {
        heading("外音コントロール"); if(state==null){unavailable();return;}
        LinearLayout card=ui.card(body); ui.choices(card,noiseMode(),n->send(0,n),feature(8),feature(9));
        if(feature(9)) {
            slider(card,"外音取り込みレベル",1,20,Math.max(1,Math.min(20,value("ambient"))),n->send(1,n));
            ui.toggle(card,"ボイスフォーカス", "アナウンスや人の声を取り込みます",value("focus")!=0,n->send(2,n));
        }
        if(feature(11)) { LinearLayout speech=ui.card(body); ui.toggle(speech,"Speak-to-Chat","話し始めると音楽を一時停止します",value("speech")!=0,n->send(3,n)); }
    }
    private void equalizer() {
        heading("イコライザー"); if(state==null){unavailable();return;}
        LinearLayout card=ui.card(body); LinearLayout top=ui.horizontal(); top.addView(ui.text("イコライザー",R.dimen.body),new LinearLayout.LayoutParams(0,-2,1)); top.addView(ui.text("CLEAR BASS  "+value("bass"),R.dimen.caption));card.addView(top);
        card.addView(new Ui.EqualizerChart(this,bands()),new LinearLayout.LayoutParams(-1,ui.dim(R.dimen.chart)));
        ui.space(card,R.dimen.section_gap);
        JSONArray ids=state.optJSONArray("presets");int count=ids==null?0:ids.length(),index=presetIndex();
        LinearLayout carousel=ui.horizontal();
        if(count>1){TextView previous=ui.centered(presetLabel((index+count-1)%count),R.dimen.caption);previous.setMinimumHeight(ui.dim(R.dimen.touch_target));previous.setTextColor(ui.color(R.color.secondary));previous.setOnClickListener(v->movePreset(-1));carousel.addView(previous,new LinearLayout.LayoutParams(0,-2,1));}
        TextView current=ui.centered(presetName(),R.dimen.model_title);current.setMinimumHeight(ui.dim(R.dimen.touch_target));current.setOnClickListener(v->selectPreset());carousel.addView(current,new LinearLayout.LayoutParams(0,-2,2));
        if(count>1){TextView next=ui.centered(presetLabel((index+1)%count),R.dimen.caption);next.setMinimumHeight(ui.dim(R.dimen.touch_target));next.setTextColor(ui.color(R.color.secondary));next.setOnClickListener(v->movePreset(1));carousel.addView(next,new LinearLayout.LayoutParams(0,-2,1));}
        card.addView(carousel,ui.params());ui.space(card,R.dimen.gutter);
        StringBuilder dots=new StringBuilder();for(int i=0;i<count;i++)dots.append(i==index?"● ":"○ ");TextView indicators=ui.centered(dots.toString().trim(),R.dimen.small);indicators.setTextColor(ui.color(R.color.secondary));card.addView(indicators,ui.params());
        ui.space(card,R.dimen.section_gap);LinearLayout edit=ui.horizontal();edit.setGravity(Gravity.END);edit.addView(ui.icon("settings","イコライザーを調整",this::editEqualizer));card.addView(edit,ui.params());
        if(feature(14)&&value("dseeAvailable")!=0){LinearLayout dsee=ui.card(body);ui.toggle(dsee,"DSEE",null,value("dsee")!=0,n->send(7,n));}
    }
    private void movePreset(int delta){JSONArray ids=state==null?null:state.optJSONArray("presets");if(ids!=null&&ids.length()>0)send(4,ids.optInt((presetIndex()+ids.length()+delta)%ids.length()));}
    private void editEqualizer(){
        ScrollView editor=new ScrollView(this);LinearLayout card=ui.column();ui.padding(card,R.dimen.gutter);editor.addView(card);
        final int[] values=bands(); String[] hz={"400 Hz","1 kHz","2.5 kHz","6.3 kHz","16 kHz"};
        for(int i=0;i<values.length;i++) { final int index=i; int max=values.length==10?6:10;
            slider(card,values.length==5?hz[i]:"バンド "+(i+1),-max,max,values[i],n->{ values[index]=n; if(connection!=null)connection.command(6,0,values.clone()); });
        }
        slider(card,"CLEAR BASS",-10,10,value("bass"),n->send(5,n));
        new AlertDialog.Builder(this).setTitle("イコライザーを調整").setView(editor).setPositiveButton("完了",null).show();
    }
    private void settings() {
        heading("すべてのデバイス設定"); if(state==null){unavailable();return;}
        LinearLayout sound=ui.card(body); sectionTitle(sound,"サウンド");
        if(feature(8)||feature(9))ui.row(sound,"外音コントロール",noiseMode()==1?"ノイズキャンセリング":noiseMode()==2?"外音取り込み":"オフ",()->navigate("noise"));
        if(feature(13)&&value("eqAvailable")!=0)ui.row(sound,"イコライザー",presetName(),()->navigate("equalizer"));
        if(feature(14)&&value("dseeAvailable")!=0)ui.toggle(sound,"DSEE",null,value("dsee")!=0,n->send(7,n));
        if(feature(27))ui.row(sound,"Bluetooth接続品質",value("priority")==1?"音質優先":"接続優先",this::quality);
        LinearLayout system=ui.card(body);sectionTitle(system,"システム");
        if(feature(22))ui.toggle(system,"ヘッドホンを外したら音楽を一時停止",null,value("autoPause")!=0,n->send(8,n));
        if(feature(24))ui.toggle(system,"通知音と音声ガイダンス",null,value("voice")!=0,n->send(10,n));
        ui.row(system,model+" バージョン",firmware.isEmpty()?"不明":firmware,null);
        if(feature(26))ui.row(system,"ヘッドホンの電源を切る",null,this::power);
    }
    private void playback(LinearLayout card) {
        TextView track=ui.text(text("track","再生中の曲"),R.dimen.body); track.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(track);
        ui.body(card,text("artist","不明なアーティスト")+"  /  "+text("album","不明なアルバム"));
        if(feature(6)){LinearLayout row=ui.horizontal();row.setGravity(Gravity.CENTER); row.addView(ui.icon("previous","前の曲",()->send(14,4))); row.addView(ui.icon(value("playback")==2?"pause":"play",value("playback")==2?"一時停止":"再生",()->send(14,value("playback")==2?2:1)));row.addView(ui.icon("next","次の曲",()->send(14,3)));card.addView(row,ui.params());}
        if(feature(7))slider(card,"音量",0,30,value("volume"),n->send(11,n));
    }
    private void slider(LinearLayout parent,String title,int min,int max,int initial,IntConsumer commit) {
        ui.space(parent,R.dimen.gap); TextView label=ui.text(title+"  "+initial,R.dimen.caption);parent.addView(label,ui.params());
        SeekBar slider=new SeekBar(this);slider.setMax(max-min);slider.setProgress(Math.max(0,Math.min(max-min,initial-min))); slider.setMinimumHeight(ui.dim(R.dimen.touch_target));slider.setContentDescription(title); parent.addView(slider,ui.params());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar view,int n,boolean user){ label.setText(title+"  "+(n+min));if(user&&!editing)commit.accept(n+min); }
            @Override public void onStartTrackingTouch(SeekBar view){editing=true;}
            @Override public void onStopTrackingTouch(SeekBar view){editing=false;commit.accept(view.getProgress()+min);}
        });
    }
    private void sectionTitle(LinearLayout parent,String title){TextView t=ui.text(title,R.dimen.body);t.setTextColor(ui.color(R.color.accent));t.setTypeface(null,android.graphics.Typeface.BOLD);t.setAccessibilityHeading(true);parent.addView(t);ui.space(parent,R.dimen.gap);}
    private void quality(){new AlertDialog.Builder(this).setTitle("Bluetooth接続品質").setMessage("変更すると接続が切れることがあります。変更後は再接続してください。")
        .setPositiveButton("音質優先",(d,w)->send(12,1)).setNeutralButton("接続優先",(d,w)->send(12,2)).setNegativeButton("キャンセル",null).show();}
    private void power(){if(!feature(26)){deviceMenu();return;}new AlertDialog.Builder(this).setTitle("ヘッドホンの電源を切りますか？").setPositiveButton("電源を切る",(d,w)->send(13,1)).setNegativeButton("キャンセル",null).show();}
    private void deviceMenu(){new AlertDialog.Builder(this).setTitle(model.isEmpty()?"デバイス":model).setItems(new String[]{"接続するデバイスを変更","接続を切断","アプリについて"},(d,i)->{if(i==0)chooseDevice();else if(i==1){disconnect();render(true);}else navigate("menu");}).show();}
    private void unavailable(){LinearLayout card=ui.card(body);ui.body(card,"ヘッドホンを接続すると設定を変更できます。");ui.space(card,R.dimen.gutter);card.addView(ui.button("デバイスに接続",true,this::chooseDevice));}
    private void information(String title,String subtitle,String description){heading(title);LinearLayout card=ui.card(body);sectionTitle(card,subtitle);ui.body(card,description);}
    private void menu(){heading("メニュー");LinearLayout card=ui.card(body);ui.row(card,"デバイスの追加・切り替え",null,this::chooseDevice);ui.row(card,"Bluetooth設定",null,this::bluetoothSettings);ui.row(card,"表示テーマ",Appearance.label(this),this::chooseAppearance);ui.row(card,"ライセンス",null,this::licenses);ui.row(card,"SoundConnect Android","バージョン 0.3.1",null);ui.space(card,R.dimen.gutter);ui.body(card,"Sonyとは関係のない非公式アプリです。Android 9以降に対応しています。画面を離れると制御接続を終了します。");}
    private void chooseAppearance(){
        String[] modes={Appearance.SYSTEM,Appearance.LIGHT,Appearance.DARK};
        String selected=Appearance.selected(this);int index=Arrays.asList(modes).indexOf(selected);
        new AlertDialog.Builder(this).setTitle("表示テーマ").setSingleChoiceItems(
            new String[]{"端末の設定に合わせる","ライト","ダーク"},index,(dialog,which)->{
                dialog.dismiss();setAppearance(modes[which]);
            }).setNegativeButton("キャンセル",null).show();
    }
    void setAppearance(String mode){if(!Appearance.selected(this).equals(mode)){Appearance.save(this,mode);recreate();}}
    private void licenses(){try(InputStream input=getAssets().open("LICENSES.txt")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=input.read(block))!=-1)bytes.write(block,0,n);new AlertDialog.Builder(this).setTitle("ライセンス").setMessage(bytes.toString("UTF-8")).setPositiveButton("閉じる",null).show();}catch(IOException e){Toast.makeText(this,"ライセンスを読み込めませんでした",Toast.LENGTH_LONG).show();}}
    private void bottomNavigation(){
        navigation.removeAllViews(); String[] destinations={"home","scene","discover","menu"};String[] labels={"マイデバイス","シーン","ディスカバー","メニュー"};String[] icons={"headphones","walk","discover","menu"};
        boolean devicePage=page.equals("home")||page.equals("settings")||page.equals("noise")||page.equals("equalizer");
        for(int i=0;i<destinations.length;i++){final String destination=destinations[i];boolean selected=i==0?devicePage:page.equals(destination);
            LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setContentDescription(labels[i]);item.setSelected(selected);
            FrameLayout holder=new FrameLayout(this);if(selected)holder.setBackground(ui.shape(R.color.accent,false));
            Ui.Glyph glyph=new Ui.Glyph(this,icons[i],selected).tone(R.color.text);holder.addView(glyph,new FrameLayout.LayoutParams(ui.dim(R.dimen.icon),ui.dim(R.dimen.icon),Gravity.CENTER));
            item.addView(holder,new LinearLayout.LayoutParams(ui.dim(R.dimen.touch_target),ui.dim(R.dimen.section_gap)+ui.dim(R.dimen.gap)));
            TextView caption=ui.centered(labels[i],R.dimen.small);LinearLayout.LayoutParams cp=ui.params();cp.topMargin=ui.dim(R.dimen.gap);item.addView(caption,cp);
            item.setFocusable(true);item.setOnClickListener(v->navigate(destination));navigation.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
}
