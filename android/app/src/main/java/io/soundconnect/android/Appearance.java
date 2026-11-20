package io.soundconnect.android;

import android.content.Context;
import android.content.res.Configuration;

/** Local override also works on Android 9, which has no general system dark-mode switch. */
final class Appearance {
    static final String SYSTEM="system", LIGHT="light", DARK="dark";
    private Appearance() {}
    static String selected(Context context) {
        return context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("theme",SYSTEM);
    }
    static void save(Context context,String mode) {
        if(!SYSTEM.equals(mode)&&!LIGHT.equals(mode)&&!DARK.equals(mode))throw new IllegalArgumentException("Unknown theme");
        context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("theme",mode).apply();
    }
    static Context wrap(Context base) {
        String mode=selected(base);
        if(SYSTEM.equals(mode))return base;
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|
            (DARK.equals(mode)?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(config);
    }
    static String label(Context context) {
        String mode=selected(context);return DARK.equals(mode)?"ダーク":LIGHT.equals(mode)?"ライト":"端末の設定に合わせる";
    }
}
