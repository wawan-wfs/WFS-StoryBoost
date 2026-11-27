package com.wfs.storyboost;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RestorationLabQaTest {
    @Test public void originalModelInferenceAndHashGate()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(boolean sr:new boolean[]{false,true}){
            RestorationLab model;
            try(InputStream in=ctx.getAssets().open("restoration-lab/"+(sr?"sr2x":"deblur")+".weights.txt")){model=RestorationLab.load(in,sr);}
            float[] input=new float[64];for(int i=0;i<input.length;i++)input[i]=i%8<4?.2f:.8f;
            float[] original=input.clone();
            long start=android.os.SystemClock.elapsedRealtimeNanos();float[] output=model.infer(input,8,8);
            double ms=(android.os.SystemClock.elapsedRealtimeNanos()-start)/1e6;
            assertEquals(sr?.0696245f:.18993203f,output[27],.000002f);
            assertEquals(sr?.94676805f:.7977222f,output[28],.000002f);
            assertArrayEquals(original,input,0f);
            for(float v:output)assertTrue(Float.isFinite(v)&&v>=0&&v<=1);
            try{RestorationLab.load(new ByteArrayInputStream(new byte[]{1}),sr);fail("Corrupt weights accepted");}
            catch(IOException expected){}
            android.util.Log.i("WfsTimelinePreviewQA","restorationModel="+(sr?"sr2x":"deblur")+" inferenceMs="+ms+" parity=verified");
        }
        assertEquals(0,RestorationLab.residualDelta(.2f,.8f,0));
        assertEquals(38,RestorationLab.residualDelta(.2f,.8f,25));
        assertEquals(153,RestorationLab.residualDelta(.2f,.8f,100));
        assertEquals(-38,RestorationLab.residualDelta(.8f,.2f,25));
        try{RestorationLab.residualDelta(.2f,.8f,101);fail("Out-of-range strength accepted");}catch(IllegalArgumentException expected){}
        float[] scaled=RestorationLab.bilinear2x(new float[]{0,1,0,1},2,2);
        assertEquals(16,scaled.length);assertEquals(.25f,scaled[1],.000001f);assertEquals(.75f,scaled[2],.000001f);
    }
    @Test public void refinedModelsInferenceAndOriginalCompatibility()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(boolean sr:new boolean[]{false,true}){
            String name=sr?"sr2x":"deblur";
            RestorationLab model;
            try(InputStream in=ctx.getAssets().open("restoration-lab/"+name+"-refined.weights.txt")){model=RestorationLab.load(in,sr,true);}
            float[] input=new float[64];for(int i=0;i<input.length;i++)input[i]=i%8<4?.2f:.8f;
            long start=android.os.SystemClock.elapsedRealtimeNanos();float[] out=model.infer(input,8,8);
            assertEquals(sr?0f:.26754335f,out[27],.000002f);assertEquals(sr?.9894026f:.530282f,out[28],.000002f);
            for(float v:out)assertTrue(Float.isFinite(v)&&v>=0&&v<=1);
            try(InputStream in=ctx.getAssets().open("restoration-lab/"+name+".weights.txt")){
                try{RestorationLab.load(in,sr,true);fail("Original hash must not match refined");}catch(IOException expected){}
            }
            android.util.Log.i("WfsTimelinePreviewQA","refinedModel="+name+" inferenceMs="+((android.os.SystemClock.elapsedRealtimeNanos()-start)/1e6)+" parity=verified");
        }
    }
    @Test(timeout=180000) public void tiledModelsPreserveSeamsAndBoundedPreview()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String run=InstrumentationRegistry.getArguments().getString("wfs.restoration.run","connected");
        assertTrue(run.matches("[A-Za-z0-9_-]{1,80}"));
        for(boolean sr:new boolean[]{false,true})for(boolean refined:new boolean[]{false,true}){
            String name=sr?"sr2x":"deblur";
            RestorationLab model;
            try(InputStream in=ctx.getAssets().open("restoration-lab/"+name+(refined?"-refined":"")+".weights.txt")){model=RestorationLab.load(in,sr,refined);}
            int w=257,h=263;float[] input=new float[w*h];java.util.Random rng=new java.util.Random(2301);
            for(int i=0;i<input.length;i++)input[i]=rng.nextFloat();
            assertArrayEquals(model.infer(input,w,h),model.inferTiled(input,w,h),0f);
            if(refined){
                float[] large=new float[1024*720];for(int i=0;i<large.length;i++)large[i]=rng.nextFloat();
                long start=android.os.SystemClock.elapsedRealtimeNanos();float[] result=model.inferTiled(large,1024,720);
                assertEquals(large.length,result.length);
                for(float value:result)assertTrue(Float.isFinite(value)&&value>=0&&value<=1);
                android.util.Log.i("WfsTimelinePreviewQA","tiledModel="+name+" width=1024 height=720 elapsedMs="+((android.os.SystemClock.elapsedRealtimeNanos()-start)/1e6)+" seamParity=verified run="+run+" pid="+android.os.Process.myPid());
            }
        }
    }
    private static byte[] shell(android.app.UiAutomation automation,String command)throws IOException{
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command));
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>24*1024*1024)throw new IOException("Screenshot too large");out.write(buf,0,n);}
            return out.toByteArray();
        }
    }
    @Test(timeout=60000) public void restorationLabSyntheticUiRunsOffline()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<RestorationLabActivity> activity=ActivityScenario.launch(RestorationLabActivity.class)){
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Uji demo sintetis SR 2×"))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
            assertNotNull(device.wait(Until.findObject(By.textStartsWith("Preview neural siap")),20000));
            // Gradle removes the target package after connected tests. A shell-owned
            // screenshot survives that lifecycle; capture it while this real UI is open.
            android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
            byte[] command=shell(automation,"screencap -p /data/local/tmp/wfs-restoration-lab.png");
            assertEquals("screencap error",0,command.length);
            byte[] png=shell(automation,"cat /data/local/tmp/wfs-restoration-lab.png");
            assertTrue(png.length>1024);
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},java.util.Arrays.copyOf(png,8));
            android.graphics.Bitmap decoded=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length);
            assertNotNull(decoded);int width=decoded.getWidth(),height=decoded.getHeight();decoded.recycle();
            assertTrue(width>=320&&height>=640);
            StringBuilder sha=new StringBuilder();
            for(byte v:java.security.MessageDigest.getInstance("SHA-256").digest(png))sha.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
            android.util.Log.i("WfsTimelinePreviewQA","restorationScreenshotSha256="+sha+" width="+width+" height="+height);
        }
    }
}
