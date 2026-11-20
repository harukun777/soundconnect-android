package io.soundconnect.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothAdapter adapter;
    private HeadphoneSession session;
    private LinearLayout content, controls;
    private Spinner devicePicker, protocolPicker;
    private Button connect, refresh;
    private TextView status, identity, battery;
    private boolean editing;
    private JSONObject latest;
    private String[] latestPresets = new String[0];
    private int protocol = 1;
    private boolean active;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        ScrollView scroll = new ScrollView(this);
        content = column();
        int gutter = dimension(R.dimen.gutter);
        content.setPadding(gutter, gutter, gutter, gutter);
        scroll.addView(content);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        label(content, "SoundConnect", R.dimen.title);
        label(content, "ヘッドホンを、自分の音に。", R.dimen.body);
        section(content, "ヘッドホンに接続");
        label(content, "AndroidのBluetooth設定で先にペアリングしてください。Sony Sound Connectは閉じてから接続してください。", R.dimen.body);
        button(content, "Bluetooth設定を開く", () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        label(content, "ペアリング済みの機器", R.dimen.body);
        devicePicker = new Spinner(this);
        devicePicker.setContentDescription("接続するヘッドホン");
        content.addView(devicePicker, rowParams());
        label(content, "通信方式", R.dimen.body);
        protocolPicker = new Spinner(this);
        protocolPicker.setAdapter(spinnerAdapter(new String[]{"V1 · WH-1000XM4以前", "V2 · XM5以降の対応機器"}));
        protocolPicker.setSelection(getPreferences(MODE_PRIVATE).getInt("protocol", 0));
        content.addView(protocolPicker, rowParams());
        refresh = button(content, "機器一覧を更新", this::loadDevices);
        connect = button(content, "ヘッドホンに接続", this::toggleConnection);
        int[][] buttonStates = new int[][] {new int[] {android.R.attr.state_enabled}, new int[] {}};
        connect.setBackgroundTintList(new android.content.res.ColorStateList(buttonStates,
            new int[] {getColor(R.color.accent), getColor(R.color.surface)}));
        connect.setTextColor(new android.content.res.ColorStateList(buttonStates,
            new int[] {getColor(R.color.surface), getColor(R.color.secondary)}));
        status = label(content, "未接続", R.dimen.body);
        status.setTextColor(getColor(R.color.accent));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        identity = label(content, "", R.dimen.section_title);
        battery = label(content, "", R.dimen.body);
        controls = column(); content.addView(controls);
        section(content, "このアプリについて");
        label(content, "Android 9以降対応 · 非公式アプリ\nSonyとは関係ありません。表示される機能は機器によって異なります。画面を離れると制御接続を終了します。音楽のBluetooth接続はそのままです。", R.dimen.body);
        button(content, "ライセンスを表示", () -> {
            try (java.io.InputStream input = getAssets().open("LICENSES.txt")) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] bytes = new byte[4096]; int count;
                while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
                new AlertDialog.Builder(this).setTitle("オープンソースライセンス")
                    .setMessage(output.toString("UTF-8")).setPositiveButton("閉じる", null).show();
            } catch (java.io.IOException e) { status.setText("ライセンスを読み込めませんでした。"); }
        });
    }

    @Override protected void onStart() { super.onStart(); active = true; loadDevices(); }
    @Override protected void onStop() {
        active = false;
        disconnect();
        super.onStop();
    }

    private boolean permitted() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void loadDevices() {
        if (session != null) return;
        devices.clear();
        connect.setEnabled(false);
        if (adapter == null) {
            devicePicker.setAdapter(spinnerAdapter(new String[]{"Bluetooth非対応の端末"}));
            status.setText("この端末はBluetoothに対応していません。"); return;
        }
        if (!permitted()) {
            devicePicker.setAdapter(spinnerAdapter(new String[]{"Bluetoothの許可が必要です"}));
            status.setText("「機器一覧を更新」を押し、付近のデバイスへの接続を許可してください。");
            return;
        }
        try {
            if (!adapter.isEnabled()) { status.setText("Bluetooth設定でBluetoothをオンにしてください。"); }
            else devices.addAll(adapter.getBondedDevices());
            devices.sort(Comparator.comparing(d -> d.getName() == null ? d.getAddress() : d.getName()));
            String[] labels = new String[Math.max(1, devices.size())];
            int selected = 0;
            String remembered = getPreferences(MODE_PRIVATE).getString("device", "");
            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice d = devices.get(i);
                labels[i] = (d.getName() == null ? "Bluetooth機器" : d.getName()) + "\n" + d.getAddress();
                if (d.getAddress().equals(remembered)) selected = i;
            }
            if (devices.isEmpty()) labels[0] = "ペアリング済みの機器がありません";
            devicePicker.setAdapter(spinnerAdapter(labels));
            devicePicker.setSelection(selected);
            connect.setEnabled(!devices.isEmpty());
        } catch (SecurityException e) { status.setText("Bluetoothの接続権限を許可してください。"); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && active) loadDevices();
    }

    private void toggleConnection() {
        if (session != null) { disconnect(); return; }
        int index = devicePicker.getSelectedItemPosition();
        if (!permitted() || index < 0 || index >= devices.size()) { loadDevices(); return; }
        BluetoothDevice device = devices.get(index);
        protocol = protocolPicker.getSelectedItemPosition() + 1;
        getPreferences(MODE_PRIVATE).edit().putString("device", device.getAddress()).putInt("protocol", protocol - 1).apply();
        devicePicker.setEnabled(false); protocolPicker.setEnabled(false); refresh.setEnabled(false);
        connect.setText("接続を中止 / 切断"); status.setText("接続しています…");
        session = new HeadphoneSession(device, protocol, new HeadphoneSession.Listener() {
            @Override public void onState(JSONObject state, String model, String firmware, String[] presets) {
                if (!active) return;
                latest = state; latestPresets = presets;
                identity.setText(model + (firmware.isEmpty() ? "" : " · " + firmware));
                status.setText("接続済み");
                renderBatteries(state.optJSONArray("battery"));
                if (!editing) renderControls();
            }
            @Override public void onStatus(String message, boolean disconnected) {
                if (!active) return;
                if (disconnected) disconnect();
                status.setText(message);
            }
            @Override public void onNotice(String message, boolean requiresChoice) {
                if (!active || session == null) return;
                if (!requiresChoice) {
                    if (!message.isEmpty()) Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    return;
                }
                HeadphoneSession target = session;
                new AlertDialog.Builder(MainActivity.this).setTitle("ヘッドホンからの確認")
                    .setMessage(message.isEmpty() ? "ヘッドホンが操作の確認を求めています。" : message)
                    .setPositiveButton("実行", (d, w) -> { if (session == target) target.command(15, 1, null); })
                    .setNegativeButton("キャンセル", (d, w) -> { if (session == target) target.command(15, 0, null); })
                    .setOnCancelListener(d -> { if (session == target) target.command(15, 0, null); }).show();
            }
        });
    }

    private void disconnect() {
        if (session != null) { session.close(); session = null; }
        latest = null; editing = false;
        if (controls != null) controls.removeAllViews();
        if (identity != null) identity.setText("");
        if (battery != null) battery.setText("");
        if (status != null) status.setText("未接続");
        if (connect != null) connect.setText("ヘッドホンに接続");
        if (devicePicker != null) devicePicker.setEnabled(true);
        if (protocolPicker != null) protocolPicker.setEnabled(true);
        if (refresh != null) refresh.setEnabled(true);
    }

    private boolean feature(int id) {
        JSONArray features = latest.optJSONArray("features");
        return features != null && features.optInt(id - 1) == 1;
    }
    private int value(String key) { return latest.optInt(key); }
    private void send(int kind, int value) { if (session != null) session.command(kind, value, null); }
    private void renderBatteries(JSONArray batteries) {
        StringBuilder text = new StringBuilder();
        String[] names = {"本体", "左", "右", "ケース"};
        if (batteries != null) for (int i = 0; i < batteries.length(); i++) {
            JSONArray b = batteries.optJSONArray(i);
            if (b == null || b.optInt(1) == 0) continue;
            int part = b.optInt(0), level = b.optInt(2);
            if (text.length() > 0) text.append("  /  ");
            text.append(part >= 0 && part < names.length ? names[part] : "電池").append(' ')
                .append(level <= 100 ? level + "%" : "不明");
            if (b.optInt(3) == 2) text.append(" 充電中");
        }
        battery.setText(text.length() == 0 ? "バッテリー情報なし" : text.toString());
    }

    private void renderControls() {
        controls.removeAllViews();
        if (latest == null || session == null) return;
        if (feature(8) || feature(9)) {
            section(controls, "ノイズコントロール");
            int mode = value("noise");
            if (protocol == 1 && mode != 0) mode = value("ambient") >= 1 && value("ambient") <= 20 ? 2 : 1;
            label(controls, mode == 1 ? "ノイズキャンセリング" : mode == 2 ? "外音取り込み" : "オフ", R.dimen.section_title);
            if (feature(8)) button(controls, "ノイズキャンセリングにする", () -> send(0, 1));
            if (feature(9)) button(controls, "外音取り込みにする", () -> send(0, 2));
            button(controls, "ノイズ制御をオフ", () -> send(0, 0));
            if (feature(9)) {
                slider("外音取り込みレベル", 1, 20, Math.max(1, Math.min(20, value("ambient"))), n -> send(1, n));
                toggle("声にフォーカス", value("focus"), n -> send(2, n));
            }
        }
        if (feature(13) && value("eqAvailable") != 0) {
            section(controls, "イコライザー");
            JSONArray presets = latest.optJSONArray("presets");
            if (presets != null && presets.length() > 0) {
                String[] names = new String[presets.length()];
                for (int i = 0; i < names.length; i++) names[i] = i < latestPresets.length && !latestPresets[i].isEmpty()
                    ? latestPresets[i] : "プリセット " + presets.optInt(i);
                button(controls, "プリセットを選ぶ", () -> new AlertDialog.Builder(this).setTitle("EQプリセット")
                    .setItems(names, (d, i) -> send(4, presets.optInt(i))).show());
                for (int i = 0; i < presets.length(); i++) if (presets.optInt(i) == value("preset")) label(controls, names[i], R.dimen.body);
            }
            JSONArray bandData = latest.optJSONArray("bands");
            if (bandData != null) {
                final int[] bands = new int[bandData.length()];
                for (int i = 0; i < bands.length; i++) bands[i] = bandData.optInt(i);
                String[] hz = {"400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz"};
                for (int i = 0; i < bands.length; i++) {
                    final int index = i;
                    int limit = bands.length == 10 ? 6 : 10;
                    slider(bands.length == 5 ? hz[i] : "バンド " + (i + 1), -limit, limit, bands[i], n -> {
                        bands[index] = n;
                        if (session != null) session.command(6, 0, bands.clone());
                    });
                }
            }
            slider("CLEAR BASS", -10, 10, value("bass"), n -> send(5, n));
        }
        if (feature(14) && value("dseeAvailable") != 0) toggle("DSEE", value("dsee"), n -> send(7, n));
        if (feature(11)) toggle("Speak-to-Chat", value("speech"), n -> send(3, n));
        if (feature(6) || feature(7)) {
            section(controls, "再生");
            if (feature(6)) {
                button(controls, "再生", () -> send(14, 1));
                button(controls, "一時停止", () -> send(14, 2));
                button(controls, "前の曲", () -> send(14, 4));
                button(controls, "次の曲", () -> send(14, 3));
            }
            if (feature(7)) slider("ヘッドホン音量", 0, 30, value("volume"), n -> send(11, n));
        }
        section(controls, "本体設定");
        if (feature(22)) toggle("外したら自動で一時停止", value("autoPause"), n -> send(8, n));
        if (feature(24)) toggle("音声ガイダンス", value("voice"), n -> send(10, n));
        if (feature(27)) button(controls, "Bluetooth接続品質を変更", () -> new AlertDialog.Builder(this)
            .setTitle("接続品質").setMessage("変更するとBluetooth接続が切れる場合があります。変更後は再接続してください。")
            .setPositiveButton("音質優先", (d, w) -> send(12, 1))
            .setNeutralButton("接続優先", (d, w) -> send(12, 2)).setNegativeButton("キャンセル", null).show());
        if (feature(26)) button(controls, "ヘッドホンの電源を切る", () -> new AlertDialog.Builder(this)
            .setTitle("ヘッドホンの電源を切りますか？").setPositiveButton("電源を切る", (d, w) -> send(13, 1))
            .setNegativeButton("キャンセル", null).show());
    }

    private void slider(String name, int min, int max, int initial, IntConsumer commit) {
        TextView title = label(controls, name + " · " + initial, R.dimen.body);
        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min); slider.setProgress(Math.max(0, Math.min(max - min, initial - min)));
        slider.setContentDescription(name);
        controls.addView(slider, rowParams());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
                title.setText(name + " · " + (progress + min));
                if (fromUser && !editing) commit.accept(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar view) { editing = true; }
            @Override public void onStopTrackingTouch(SeekBar view) { editing = false; commit.accept(view.getProgress() + min); }
        });
    }
    private void toggle(String name, int initial, IntConsumer action) {
        Switch toggle = new Switch(this);
        toggle.setText(name); toggle.setChecked(initial != 0);
        toggle.setMinHeight(dimension(R.dimen.touch_target));
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.body));
        controls.addView(toggle, rowParams());
        toggle.setOnCheckedChangeListener((v, checked) -> action.accept(checked ? 1 : 0));
    }
    private int dimension(int id) { return getResources().getDimensionPixelSize(id); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dimension(R.dimen.gap);
        return p;
    }
    private TextView label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(size));
        parent.addView(view, rowParams()); return view;
    }
    private void section(LinearLayout parent, String text) {
        TextView view = label(parent, text, R.dimen.section_title);
        view.setPadding(0, dimension(R.dimen.section_gap), 0, 0);
        view.setAccessibilityHeading(true);
    }
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false);
        view.setMinHeight(dimension(R.dimen.touch_target));
        parent.addView(view, rowParams());
        view.setOnClickListener(v -> {
            if (view == refresh && !permitted() && Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            } else action.run();
        });
        return view;
    }
    private ArrayAdapter<String> spinnerAdapter(String[] labels) {
        ArrayAdapter<String> result = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return result;
    }
}
