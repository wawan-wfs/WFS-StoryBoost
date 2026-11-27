package com.wfs.storyboost;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ProcessDeathTimelineTest {
    private static final String PREF="qa-timeline-process";
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Context app(){return inst.getTargetContext();}
    private void phase(String p){
        Assume.assumeTrue(p.equals(InstrumentationRegistry.getArguments().getString("wfs.timeline.phase")));
    }
    private void reportPid(String key){
        Bundle status=new Bundle();
        status.putInt(key,Process.myPid());
        inst.sendStatus(2,status);
    }
    private Intent screen(){
        return new Intent(app(),StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
    }
    private static View find(View view,String text,boolean desc){
        if(desc&&view.getContentDescription()!=null&&view.getContentDescription().toString().equals(text))return view;
        if(!desc&&view instanceof TextView&&((TextView)view).getText().toString().contains(text))return view;
        if(view instanceof ViewGroup){
            ViewGroup g=(ViewGroup)view;
            for(int i=0;i<g.getChildCount();i++){
                View match=find(g.getChildAt(i),text,desc);if(match!=null)return match;
            }
        }
        return null;
    }
    private static void tap(View root,String text,boolean desc){
        View found=find(root,text,desc);
        assertNotNull("Missing "+text,found);assertTrue(found.isEnabled());assertTrue(found.performClick());
    }
    private Uri fixture(String name)throws Exception{
        ContentValues v=new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME,"wfs-timeline-draft-"+System.nanoTime()+name);
        v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/WFS-StoryBoost-QA");
        Uri uri=app().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);
        assertNotNull(uri);
        try(InputStream in=inst.getContext().getAssets().open(name);
            OutputStream out=app().getContentResolver().openOutputStream(uri)){
            assertNotNull(out);byte[] buf=new byte[8192];
            for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);
        }
        return uri;
    }
    private String sha(Uri uri)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=app().getContentResolver().openInputStream(uri)){
            assertNotNull(in);byte[] bytes=new byte[8192];
            for(int n;(n=in.read(bytes))!=-1;)hash.update(bytes,0,n);
        }
        return Arrays.toString(hash.digest());
    }

    @Test(timeout=145000) public void stageVisibleDraftAndHistory()throws Exception{
        phase("stage");
        Uri first=fixture("qa-timeline-a.mp4"),second=fixture("qa-timeline-b.mp4");
        ExportOptions old=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                AspectRatioProfile.FIT,false,125,1500).withCaption("Migrasi",0xffffd786);
        assertTrue(StudioDraftStore.save(app(),first,old,-1,false));
        TimelineProject initial=TimelineDraftStore.fromSingleSource(first,old,MediaProbe.duration(app(),first));
        assertEquals(125,initial.clips.get(0).in);
        assertEquals(1375,initial.caption.out);
        TimelineProject edited=initial.append(TimelineProject.Clip.create(second.toString(),
                MediaProbe.duration(app(),second),500,1600)).caption(new TimelineProject.Caption(
                "Caption rentang",400,1750,0xffffd786)).adjust(1,new ClipAdjustments(6,30,-20,15,true));
        edited=edited.soundtrack(new TimelineProject.Audio(second.toString(),MediaProbe.duration(app(),second),500,1000,250,40)).fade(150)
                .voiceTrack(new TimelineProject.Audio(second.toString(),MediaProbe.duration(app(),second),500,1000,750,35))
                .keyframes(1,40,120).zoom(1,60,140)
                .picture(new PictureLayer(second.toString(),MediaProbe.duration(app(),second),500,1000,250,40,80,0,0,100,100,PictureLayer.SMOOTH,500))
                .picture2(new PictureLayer(first.toString(),MediaProbe.duration(app(),first),125,625,350,30,60,100,0,0,100,PictureLayer.EASE_IN,500));
        String firstId=edited.clips.get(0).id,secondId=edited.clips.get(1).id;
        assertTrue(TimelineDraftStore.save(app(),edited));
        ActivityScenario<StudioMergeActivity> ui=ActivityScenario.launch(screen());
        ui.onActivity(a->{
            View root=a.getWindow().getDecorView();
            tap(root,"Turunkan klip 1",true);
            assertEquals(secondId,TimelineDraftStore.load(app()).clips.get(0).id);
            tap(root,"Undo",false);
            assertEquals(firstId,TimelineDraftStore.load(app()).clips.get(0).id);
            tap(root,"Redo",false);
            assertEquals(secondId,TimelineDraftStore.load(app()).clips.get(0).id);
        });
        // Seed a disposable interrupted-render file and an unrelated control.
        String orphan=VideoMergeEngine.PREVIEW_PREFIX+java.util.UUID.randomUUID()+".mp4";
        String control="wfs-qa-control-"+java.util.UUID.randomUUID()+".mp4";
        java.nio.file.Files.write(new java.io.File(app().getCacheDir(),orphan).toPath(),new byte[]{1});
        java.nio.file.Files.write(new java.io.File(app().getCacheDir(),control).toPath(),new byte[]{2});
        assertTrue(app().getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putInt("pid",Process.myPid()).putString("a",first.toString()).putString("b",second.toString())
                .putString("aId",firstId).putString("bId",secondId)
                .putString("orphan",orphan).putString("control",control)
                .putString("shaA",sha(first)).putString("shaB",sha(second)).commit());
        reportPid("wfsTimelineStagePid");
        // Keep the Activity and instrumentation alive, so the host must interrupt
        // this process before a normal ActivityScenario finish can save anything.
        Thread.sleep(120000L);
        ui.close();
        fail("External force-stop never interrupted the foreground timeline");
    }

    @Test public void verifyDifferentProcessAndReopenedDraft()throws Exception{
        phase("verify");
        android.content.SharedPreferences p=app().getSharedPreferences(PREF,Context.MODE_PRIVATE);
        Uri first=Uri.parse(p.getString("a","")),second=Uri.parse(p.getString("b",""));
        try{
            int previousPid=p.getInt("pid",-1);
            assertTrue("Live stage did not persist its PID",previousPid>0);
            assertNotEquals("Expected a new PID",previousPid,Process.myPid());
            String orphan=p.getString("orphan",""),control=p.getString("control","");
            assertTrue(orphan.startsWith(VideoMergeEngine.PREVIEW_PREFIX));
            assertTrue(control.startsWith("wfs-qa-control-"));
            assertFalse("Cold startup must discard interrupted preview",new java.io.File(app().getCacheDir(),orphan).exists());
            assertArrayEquals(new byte[]{2},java.nio.file.Files.readAllBytes(new java.io.File(app().getCacheDir(),control).toPath()));
            TimelineProject saved=TimelineDraftStore.load(app());
            assertNotNull(saved);
            assertEquals(p.getString("bId",""),saved.clips.get(0).id);
            assertEquals(p.getString("aId",""),saved.clips.get(1).id);
            assertTrue(saved.clips.get(0).adjustments.same(new ClipAdjustments(6,30,-20,15,true)));
            assertTrue(saved.clips.get(1).adjustments.same(ClipAdjustments.NEUTRAL));
            assertEquals(500,saved.clips.get(0).in);assertEquals(1600,saved.clips.get(0).out);
            assertEquals(125,saved.clips.get(1).in);assertEquals(1500,saved.clips.get(1).out);
            assertEquals(400,saved.caption.in);assertEquals(1750,saved.caption.out);
            assertNotNull(saved.audio);assertEquals(second.toString(),saved.audio.uri);
            assertEquals(500,saved.audio.in);assertEquals(1000,saved.audio.out);
            assertEquals(250,saved.audio.start);assertEquals(40,saved.audio.volume);assertEquals(150,saved.fadeMs);
            assertNotNull(saved.voice);assertEquals(second.toString(),saved.voice.uri);
            assertEquals(500,saved.voice.in);assertEquals(1000,saved.voice.out);
            assertEquals(750,saved.voice.start);assertEquals(35,saved.voice.volume);
            assertEquals(40,saved.clips.get(0).gainStart);assertEquals(120,saved.clips.get(0).gainEnd);
            assertEquals(60,saved.clips.get(0).zoomStart);assertEquals(140,saved.clips.get(0).zoomEnd);
            assertNotNull(saved.picture);assertEquals(second.toString(),saved.picture.uri);assertEquals(250,saved.picture.start);assertEquals(80,saved.picture.opacity);assertEquals(100,saved.picture.xEnd);
            assertEquals(PictureLayer.SMOOTH,saved.picture.curve);assertNotNull(saved.picture2);assertEquals(first.toString(),saved.picture2.uri);assertEquals(350,saved.picture2.start);assertEquals(PictureLayer.EASE_IN,saved.picture2.curve);assertEquals(500,saved.picture2.motionDuration);
            assertEquals(p.getString("shaA",""),sha(first));assertEquals(p.getString("shaB",""),sha(second));
            assertEquals("Migrasi",StudioDraftStore.load(app(),first).options.caption);
            try(ActivityScenario<StudioMergeActivity> ui=ActivityScenario.launch(screen())){
                ui.onActivity(a->{
                    View root=a.getWindow().getDecorView();
                    assertNotNull(find(root,"2 klip dalam storyboard",false));
                    assertNotNull(find(root,"Caption 400–1750 ms",false));
                    assertNotNull(find(root,"500–1600 ms",false));
                    assertFalse(find(root,"Undo",false).isEnabled());
                });
            }
        }finally{
            TimelineDraftStore.clear(app());
            if("content".equals(first.getScheme()))
                app().getSharedPreferences("studio-drafts",Context.MODE_PRIVATE).edit()
                        .remove(StudioDraftStore.keyFor(first)).commit();
            for(Uri u:new Uri[]{first,second})if("content".equals(u.getScheme()))app().getContentResolver().delete(u,null,null);
            String control=p.getString("control","");
            if(control.startsWith("wfs-qa-control-")&&!control.contains("/"))
                new java.io.File(app().getCacheDir(),control).delete();
            p.edit().clear().commit();
        }
        Bundle cacheEvidence=new Bundle();
        cacheEvidence.putString("wfsTimelineCacheRecovery","verified");
        inst.sendStatus(2,cacheEvidence);
        reportPid("wfsTimelineRestoredPid");
    }
}
