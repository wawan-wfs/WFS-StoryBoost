package com.wfs.storyboost;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    @Test public void stageVisibleDraftAndHistory()throws Exception{
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
                "Caption rentang",400,1750,0xffffd786));
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
        assertTrue(app().getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putInt("pid",Process.myPid()).putString("a",first.toString()).putString("b",second.toString())
                .putString("aId",firstId).putString("bId",secondId)
                .putString("shaA",sha(first)).putString("shaB",sha(second)).commit());
        System.out.println("TIMELINE_STAGE pid="+Process.myPid()+" moved=true undo=true redo=true");
        // The owner script force-stops this process before independently verifying.
    }

    @Test public void verifyDifferentProcessAndReopenedDraft()throws Exception{
        phase("verify");
        android.content.SharedPreferences p=app().getSharedPreferences(PREF,Context.MODE_PRIVATE);
        Uri first=Uri.parse(p.getString("a","")),second=Uri.parse(p.getString("b",""));
        try{
            assertNotEquals("Expected a new PID",p.getInt("pid",-1),Process.myPid());
            TimelineProject saved=TimelineDraftStore.load(app());
            assertNotNull(saved);
            assertEquals(p.getString("bId",""),saved.clips.get(0).id);
            assertEquals(p.getString("aId",""),saved.clips.get(1).id);
            assertEquals(500,saved.clips.get(0).in);assertEquals(1600,saved.clips.get(0).out);
            assertEquals(125,saved.clips.get(1).in);assertEquals(1500,saved.clips.get(1).out);
            assertEquals(400,saved.caption.in);assertEquals(1750,saved.caption.out);
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
            System.out.println("TIMELINE_RESTORED pid="+Process.myPid()+" unchanged=true");
        }finally{
            TimelineDraftStore.clear(app());
            if("content".equals(first.getScheme()))
                app().getSharedPreferences("studio-drafts",Context.MODE_PRIVATE).edit()
                        .remove(StudioDraftStore.keyFor(first)).commit();
            for(Uri u:new Uri[]{first,second})if("content".equals(u.getScheme()))app().getContentResolver().delete(u,null,null);
            p.edit().clear().commit();
        }
    }
}
