package io.soundconnect.android;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    private Context base;
    private String original;
    @Before public void setup(){base=InstrumentationRegistry.getInstrumentation().getTargetContext();original=Appearance.selected(base);}
    @After public void restore(){Appearance.save(base,original);}
    private Context systemContext(int night){Configuration c=new Configuration(base.getResources().getConfiguration());c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|night;return base.createConfigurationContext(c);}
    private int night(Context c){return c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;}

    @Test public void explicitDarkOverridesLightSystemAndUsesDarkNativeDialogTheme(){
        Appearance.save(base,Appearance.DARK);
        Context dark=Appearance.wrap(systemContext(Configuration.UI_MODE_NIGHT_NO));
        assertEquals(Configuration.UI_MODE_NIGHT_YES,night(dark));
        assertTrue(android.graphics.Color.luminance(dark.getColor(R.color.card))<0.1);
        assertTrue(android.graphics.Color.luminance(dark.getColor(R.color.text))>0.8);
        ContextThemeWrapper themed=new ContextThemeWrapper(dark,R.style.AppTheme);
        TypedArray bars=themed.obtainStyledAttributes(new int[]{android.R.attr.windowLightStatusBar,android.R.attr.windowLightNavigationBar});
        try{assertFalse(bars.getBoolean(0,true));assertFalse(bars.getBoolean(1,true));}finally{bars.recycle();}
    }
    @Test public void automaticFollowsSystemAndExplicitLightOverridesDark(){
        Appearance.save(base,Appearance.SYSTEM);
        assertEquals(Configuration.UI_MODE_NIGHT_YES,night(Appearance.wrap(systemContext(Configuration.UI_MODE_NIGHT_YES))));
        assertEquals(Configuration.UI_MODE_NIGHT_NO,night(Appearance.wrap(systemContext(Configuration.UI_MODE_NIGHT_NO))));
        Appearance.save(base,Appearance.LIGHT);
        Context light=Appearance.wrap(systemContext(Configuration.UI_MODE_NIGHT_YES));
        assertEquals(Configuration.UI_MODE_NIGHT_NO,night(light));
        assertTrue(android.graphics.Color.luminance(light.getColor(R.color.card))>0.8);
    }
    @Test public void switchingPersistsAcrossRecreationAndKeepsMenu(){
        Appearance.save(base,Appearance.LIGHT);
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            screen.onActivity(activity->{activity.navigate("menu");activity.setAppearance(Appearance.DARK);});
            screen.recreate();
            screen.onActivity(activity->{assertEquals(Appearance.DARK,Appearance.selected(activity));assertEquals(Configuration.UI_MODE_NIGHT_YES,night(activity));});
            screen.onActivity(activity->activity.setAppearance(Appearance.LIGHT));
            screen.recreate();
            screen.onActivity(activity->{assertEquals(Appearance.LIGHT,Appearance.selected(activity));assertEquals(Configuration.UI_MODE_NIGHT_NO,night(activity));});
        }
    }
}
