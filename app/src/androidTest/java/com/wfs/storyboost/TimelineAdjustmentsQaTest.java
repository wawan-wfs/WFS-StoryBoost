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
    @Test public void cutAndCurveV8RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject base=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
                .append(TimelineProject.Clip.create("content://qa/b",2000,0,2000))
                .append(TimelineProject.Clip.create("content://qa/c",2000,0,2000));
            TimelineProject edit=base.cut(0,500).cut(1,150).picture(new PictureLayer("content://qa/p",2000,0,1000,500,40,80,0,0,100,100,PictureLayer.EASE_IN,1000));
            assertTrue(TimelineDraftStore.save(ctx,edit));String raw=prefs.getString("draft",null);
            assertEquals(8,new JSONObject(raw).getInt("version"));TimelineProject restored=TimelineDraftStore.load(ctx);
            assertNotNull(restored);assertEquals(5350,restored.duration());assertEquals(500,restored.cutMs(0));assertEquals(150,restored.cutMs(1));
            assertEquals(PictureLayer.EASE_IN,restored.picture.curve);assertEquals(-.3f,restored.picture.anchorX(1000000),.000001f);
            String bad=raw.replace("\"curve\":1","\"curve\":99");
            assertTrue(prefs.edit().putString("draft",bad).commit());assertNull(TimelineDraftStore.load(ctx));assertEquals(bad,prefs.getString("draft",null));
            assertTrue(TimelineDraftStore.save(ctx,base.picture(edit.picture)));assertNotNull(TimelineDraftStore.load(ctx));assertTrue(TimelineDraftStore.load(ctx).cuts.isEmpty());
            assertTrue(TimelineDraftStore.save(ctx,base.cut(0,500)));assertNotNull(TimelineDraftStore.load(ctx));assertNull(TimelineDraftStore.load(ctx).picture);
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test public void pictureV7RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
                .picture(new PictureLayer("content://qa/p",2000,0,1000,500,40,80,0,0,100,100));
            assertTrue(TimelineDraftStore.save(ctx,project));String raw=prefs.getString("draft",null);
            assertEquals(7,new JSONObject(raw).getInt("version"));TimelineProject loaded=TimelineDraftStore.load(ctx);
            assertNotNull(loaded);assertNotNull(loaded.picture);assertEquals(500,loaded.picture.start);
            assertEquals(40,loaded.picture.scale);assertEquals(80,loaded.picture.opacity);assertEquals(100,loaded.picture.xEnd);
            String bad=raw.replace("\"scale\":40","\"scale\":99");
            assertTrue(prefs.edit().putString("draft",bad).commit());assertNull(TimelineDraftStore.load(ctx));assertEquals(bad,prefs.getString("draft",null));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test public void overlapV6RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
                .append(TimelineProject.Clip.create("content://qa/b",2000,0,2000)).crossfade(500);
            assertTrue(TimelineDraftStore.save(ctx,project));String raw=prefs.getString("draft",null);
            assertEquals(6,new JSONObject(raw).getInt("version"));TimelineProject loaded=TimelineDraftStore.load(ctx);
            assertNotNull(loaded);assertEquals(3500,loaded.duration());assertEquals(1500,loaded.startOf(1));assertEquals(500,loaded.crossfadeMs);
            String bad=raw.replace("\"crossfadeMs\":500","\"crossfadeMs\":1001");
            assertTrue(prefs.edit().putString("draft",bad).commit());assertNull(TimelineDraftStore.load(ctx));assertEquals(bad,prefs.getString("draft",null));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test public void zoomV5RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/z",2000,0,2000)).zoom(0,50,150);
            assertTrue(TimelineDraftStore.save(ctx,project));String raw=prefs.getString("draft",null);
            assertEquals(5,new JSONObject(raw).getInt("version"));TimelineProject loaded=TimelineDraftStore.load(ctx);
            assertNotNull(loaded);assertEquals(50,loaded.clips.get(0).zoomStart);assertEquals(150,loaded.clips.get(0).zoomEnd);
            assertTrue(prefs.edit().putString("draft",raw.replace("\"zoomEnd\":150","\"zoomEnd\":201")).commit());assertNull(TimelineDraftStore.load(ctx));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test public void dualTrackAndKeyframesV4RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
                .soundtrack(new TimelineProject.Audio("content://qa/s",3000,0,1000,0,40))
                .voiceTrack(new TimelineProject.Audio("content://qa/v",3000,500,1500,1000,30)).keyframes(0,25,125);
            assertTrue(TimelineDraftStore.save(ctx,project));String raw=prefs.getString("draft",null);
            assertEquals(4,new JSONObject(raw).getInt("version"));TimelineProject got=TimelineDraftStore.load(ctx);
            assertNotNull(got);assertEquals(25,got.clips.get(0).gainStart);assertEquals(125,got.clips.get(0).gainEnd);
            assertEquals("content://qa/v",got.voice.uri);assertEquals(30,got.voice.volume);assertEquals(40,got.audio.volume);
            String bad=raw.replace("\"gainStart\":25","\"gainStart\":151");
            assertTrue(prefs.edit().putString("draft",bad).commit());assertNull(TimelineDraftStore.load(ctx));
            assertEquals(bad,prefs.getString("draft",null));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test public void soundtrackAndFadeV3RoundTrip()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("one-track-timeline",Context.MODE_PRIVATE);
        try{
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
                    .soundtrack(new TimelineProject.Audio("content://qa/sound",3000,250,1250,500,45)).fade(300);
            assertTrue(TimelineDraftStore.save(ctx,project));
            String raw=prefs.getString("draft",null);assertEquals(3,new JSONObject(raw).getInt("version"));
            TimelineProject loaded=TimelineDraftStore.load(ctx);assertNotNull(loaded);assertNotNull(loaded.audio);
            assertEquals(500,loaded.audio.start);assertEquals(250,loaded.audio.in);assertEquals(1250,loaded.audio.out);
            assertEquals(45,loaded.audio.volume);assertEquals(300,loaded.fadeMs);
            for(String bad:new String[]{raw.replace("\"volume\":45","\"volume\":101"),
                    raw.replace("\"startMs\":500","\"startMs\":500.5"),raw.replace("\"fadeMs\":300","\"fadeMs\":1001")}){
                assertTrue(prefs.edit().putString("draft",bad).commit());assertNull(TimelineDraftStore.load(ctx));
            }
        }finally{TimelineDraftStore.clear(ctx);}
    }
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
