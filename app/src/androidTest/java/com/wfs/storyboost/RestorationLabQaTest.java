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
        float[] scaled=RestorationLab.bilinear2x(new float[]{0,1,0,1},2,2);
        assertEquals(16,scaled.length);assertEquals(.25f,scaled[1],.000001f);assertEquals(.75f,scaled[2],.000001f);
    }
    @Test(timeout=60000) public void restorationLabSyntheticUiRunsOffline()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<RestorationLabActivity> activity=ActivityScenario.launch(RestorationLabActivity.class)){
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Uji demo sintetis SR 2×"))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
            assertNotNull(device.wait(Until.findObject(By.textStartsWith("Preview neural siap")),20000));
            Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
            assertTrue(device.takeScreenshot(new java.io.File(ctx.getFilesDir(),"qa-restoration-lab.png")));
        }
    }
}
