package com.wfs.storyboost;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

@RunWith(AndroidJUnit4.class)
public final class TimelineAdjustmentsQaTest {
    @Test public void legacySchemaReadOnlyAndV2RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        String v1="{\"version\":1,\"quality\":1,\"clips\":[{\"id\":\"legacy\",\"uri\":\"content://qa/a\",\"duration\":2000,\"inMs\":100,\"outMs\":1900}]}";
        try{
            assertTrue(prefs.edit().putString("draft",v1).commit());
            TimelineProject old=TimelineDraftStore.load(ctx);assertNotNull(old);
            assertTrue(old.clips.get(0).adjustments.same(ClipAdjustments.NEUTRAL));
            assertEquals(v1,prefs.getString("draft",null));
            TimelineProject next=old.adjust(0,new ClipAdjustments(7,-34,100,5,true));
            assertTrue(TimelineDraftStore.save(ctx,next));
            String v2=prefs.getString("draft",null);assertEquals(2,new JSONObject(v2).getInt("version"));
            TimelineProject reopened=TimelineDraftStore.load(ctx);
            assertEquals("legacy",reopened.clips.get(0).id);
            assertEquals(100,reopened.clips.get(0).in);
            assertTrue(reopened.clips.get(0).adjustments.same(next.clips.get(0).adjustments));
            for(String bad:new String[]{v2.replace("\"version\":2","\"version\":3"),
                    v2.replace("\"brightness\":-34","\"brightness\":-34.5"),
                    v2.replace("\"mute\":true","\"mute\":\"true\"")}){
                assertTrue(prefs.edit().putString("draft",bad).commit());
                assertNull(TimelineDraftStore.load(ctx));assertEquals(bad,prefs.getString("draft",null));
            }
        }finally{TimelineDraftStore.clear(ctx);}
    }
}
