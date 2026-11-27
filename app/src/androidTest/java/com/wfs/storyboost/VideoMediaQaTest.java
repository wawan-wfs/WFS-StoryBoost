package com.wfs.storyboost;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES30;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Rights-safe synthetic MP4 fixtures run through real Android MediaExtractor.
 * A 1080p Media3 transform is a HARD gate on the Android 15 emulator only.
 */
@RunWith(AndroidJUnit4.class)
public class VideoMediaQaTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final List<Uri> sources=new ArrayList<>();
    private Context ctx;
    private Uri av,silent,rotated;
    private VideoEngine engine;
    private VideoMergeEngine mergeEngine;
    private File result;
    private final List<File> outputFiles=new ArrayList<>();

    @Before public void setup() throws Exception {
        ctx=inst.getTargetContext();
        av=importFixture("qa-h264-aac.mp4");
        silent=importFixture("qa-h264-silent.mp4");
        rotated=importFixture("qa-rotated.mp4");
    }
    private Uri importFixture(String name) throws Exception {
        ContentValues meta=new ContentValues();
        meta.put(MediaStore.Video.Media.DISPLAY_NAME,"wfs-qa-"+System.nanoTime()+name);
        meta.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        meta.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/WFS-StoryBoost-QA");
        Uri uri=ctx.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,meta);
        assertNotNull(uri);
        sources.add(uri);
        try(InputStream in=inst.getContext().getAssets().open(name);
            OutputStream out=ctx.getContentResolver().openOutputStream(uri)){
            assertNotNull(out);
            byte[] bytes=new byte[8192];
            for(int n;(n=in.read(bytes))!=-1;)out.write(bytes,0,n);
        }
        return uri;
    }
    private static boolean containsCodec(File file,String mime) throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(file.getAbsolutePath());
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat fmt=extractor.getTrackFormat(i);
                if(mime.equals(fmt.getString(MediaFormat.KEY_MIME)))return true;
            }
            return false;
        }finally{extractor.release();}
    }
    @After public void cleanup(){
        if(engine!=null)inst.runOnMainSync(engine::cancel);
        if(mergeEngine!=null)inst.runOnMainSync(mergeEngine::cancel);
        if(result!=null)result.delete();
        for(File f:outputFiles)if(f!=null)f.delete();
        for(Uri uri:sources)try{ctx.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
    }

    @Test public void extractorReadsAacSilentAndRotatedFixtures() throws Exception {
        MediaProbe.Info video=MediaProbe.inspect(ctx,av);
        assertTrue(video.video);assertTrue(video.audio);
        assertTrue(video.videoDurationMs>=1800);
        assertTrue(video.audioDurationMs>=1800);
        MediaProbe.Info noAudio=MediaProbe.inspect(ctx,silent);
        assertTrue(noAudio.video);assertFalse(noAudio.audio);
        AspectRatioProfile.Size logical=MediaDimensions.read(ctx,rotated);
        assertEquals(568,logical.width);
        assertEquals(320,logical.height);
    }

    @Test(timeout=210000)
    @SdkSuppress(minSdkVersion=35)
    public void android15EncoderProducesActual1080pTrimmedMp4WithAac() throws Exception {
        ExportOptions config=new ExportOptions(ColorLooks.ORIGINAL,
            AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,200,1700)
            .withQuality(OutputProfile.FULL_HD);
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> produced=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        engine=new VideoEngine();
        inst.runOnMainSync(()->{
            try{
                engine.start(ctx,av,config,new VideoEngine.Listener(){
                    @Override public void onSuccess(File f,AspectRatioProfile.Size dims){
                        produced.set(f);done.countDown();
                    }
                    @Override public void onError(String msg){error.set(msg);done.countDown();}
                });
            }catch(Exception ex){error.set(ex.toString());done.countDown();}
        });
        assertTrue("Encoder timeout",done.await(170,TimeUnit.SECONDS));
        assertNull("VideoEngine error or 1080p fallback: "+error.get(),error.get());
        result=produced.get();
        assertNotNull(result);
        MediaProbe.Info actual=MediaProbe.inspect(result);
        assertTrue(actual.video);assertTrue(actual.audio);
        assertTrue(containsCodec(result,"video/avc"));
        assertTrue(containsCodec(result,"audio/mp4a-latm"));
        assertTrue("trim duration "+actual.durationMs,Math.abs(actual.durationMs-1500)<450);
        assertTrue("A/V drift",Math.abs(actual.audioDurationMs-actual.videoDurationMs)<450);
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{
            retriever.setDataSource(result.getAbsolutePath());
            int w=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rot=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if(rot!=null&&Integer.parseInt(rot)%180!=0){int swap=w;w=h;h=swap;}
            assertEquals("Real encoded width",1080,w);
            assertEquals("Real encoded height",1920,h);
        }finally{retriever.release();}
    }

    @Test(timeout=150000) @SdkSuppress(minSdkVersion=29,maxSdkVersion=29)
    public void android10ReportsRealHdAndDistinguishesFullHdFromFallback() throws Exception {
        AspectRatioProfile.Size hd=new AspectRatioProfile.Size(720,1280);
        AspectRatioProfile.Size fhd=new AspectRatioProfile.Size(1080,1920);
        for(int quality:new int[]{OutputProfile.HD,OutputProfile.FULL_HD}){
            ExportOptions config=new ExportOptions(ColorLooks.ORIGINAL,
                    AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,200,1700)
                    .withQuality(quality);
            CountDownLatch finished=new CountDownLatch(1);
            AtomicReference<String> error=new AtomicReference<>();
            AtomicReference<File> actual=new AtomicReference<>();
            AtomicReference<AspectRatioProfile.Size> resultSize=new AtomicReference<>();
            engine=new VideoEngine();
            inst.runOnMainSync(()->engine.start(ctx,av,config,new VideoEngine.Listener(){
                @Override public void onSuccess(File file,AspectRatioProfile.Size dimensions){
                    actual.set(file);resultSize.set(dimensions);finished.countDown();
                }
                @Override public void onError(String message){error.set(message);finished.countDown();}
            }));
            assertTrue("Android 10 encoder did not finish",finished.await(55,TimeUnit.SECONDS));
            File encoded=actual.get();
            if(encoded==null){
                String reason=error.get();
                assertNotNull("No classified outcome from encoder",reason);
                assertTrue("Unexpected failure for valid MP4: "+reason,
                        reason.startsWith("ERROR_CODE_ENCODER_")||reason.startsWith("ERROR_CODE_ENCODING_")
                        ||reason.startsWith("ERROR_CODE_DECODER_")||reason.startsWith("ERROR_CODE_DECODING_"));
                android.util.Log.i("WfsEncoderQa","API29 quality="+quality+" CODEC_FAILURE "+reason);
            }else{
                outputFiles.add(encoded);
                assertNull(error.get());
                assertTrue(containsCodec(encoded,"video/avc"));
                assertTrue(containsCodec(encoded,"audio/mp4a-latm"));
                MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(encoded);
                assertTrue("Unexpected sample drift: "+pts.lastPtsDriftMs(),pts.lastPtsDriftMs()>=0&&pts.lastPtsDriftMs()<=250);
                AspectRatioProfile.Size dimensions=resultSize.get();
                assertTrue("Unexpected actual canvas "+dimensions,hd.toString().equals(dimensions.toString())
                        ||(quality==OutputProfile.FULL_HD&&fhd.toString().equals(dimensions.toString())));
                android.util.Log.i("WfsEncoderQa","API29 quality="+quality+" actual="+dimensions+
                        " A/V last PTS drift="+pts.lastPtsDriftMs()+"ms result="+
                        (quality==OutputProfile.HD?"HD":fhd.toString().equals(dimensions.toString())?"FHD":"HD_FALLBACK"));
            }
            inst.runOnMainSync(engine::cleanup);
        }
    }

    @Test public void rejectsMalformedMp4InsteadOfInventingVideoTrack() throws Exception {
        File bad=new File(ctx.getCacheDir(),"wfs-qa-bad-"+System.nanoTime()+".mp4");
        try{
            try(FileOutputStream out=new FileOutputStream(bad)){
                out.write("not a real mp4".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try{
                MediaProbe.inspect(bad);
                fail("MediaExtractor must reject corrupted MP4");
            }catch(java.io.IOException|IllegalArgumentException expected){}
        }finally{bad.delete();}
    }

    @Test public void videoEngineRejectsMalformedSourceBeforeAnyEncode() throws Exception {
        Uri malformed=importFixture("qa-invalid.mp4");
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.ORIGINAL,AspectRatioProfile.FIT,true,0,0);
        AtomicReference<RuntimeException> rejected=new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger callbacks=new java.util.concurrent.atomic.AtomicInteger();
        engine=new VideoEngine();
        String[] before=ctx.getCacheDir().list((dir,name)->name.startsWith("storyboost_"));
        inst.runOnMainSync(()->{
            try{
                engine.start(ctx,malformed,options,new VideoEngine.Listener(){
                    @Override public void onSuccess(File file,AspectRatioProfile.Size dims){
                        outputFiles.add(file);
                        callbacks.incrementAndGet();
                    }
                    @Override public void onError(String error){callbacks.incrementAndGet();}
                });
            }catch(IllegalArgumentException expected){rejected.set(expected);}
        });
        assertNotNull("Malformed source passed engine preflight",rejected.get());
        assertEquals(0,callbacks.get());
        String[] after=ctx.getCacheDir().list((dir,name)->name.startsWith("storyboost_"));
        assertNotNull(before);
        assertNotNull(after);
        java.util.Arrays.sort(before);
        java.util.Arrays.sort(after);
        assertArrayEquals("Malformed source leaked encoder cache",before,after);
    }

    private File realExport(Uri source, ExportOptions options) throws Exception {
        CountDownLatch completed=new CountDownLatch(1);
        AtomicReference<File> file=new AtomicReference<>();
        AtomicReference<String> fail=new AtomicReference<>();
        engine=new VideoEngine();
        inst.runOnMainSync(()->{
            try{
                engine.start(ctx,source,options,new VideoEngine.Listener(){
                    @Override public void onSuccess(File output,AspectRatioProfile.Size dimensions){
                        file.set(output);completed.countDown();
                    }
                    @Override public void onError(String reason){fail.set(reason);completed.countDown();}
                });
            }catch(Exception ex){fail.set(ex.toString());completed.countDown();}
        });
        assertTrue("Actual encoder did not finish",completed.await(170,TimeUnit.SECONDS));
        assertNull("VideoEngine failed: "+fail.get(),fail.get());
        File output=file.get();
        assertNotNull("No encoded video returned",output);
        assertTrue("Encoded MP4 unexpectedly short",output.length()>1024);
        outputFiles.add(output);
        return output;
    }

    @Test(timeout=450000)
    public void captionSafeZonesMatchEditorOverlayOnDecodedVideo() throws Exception {
        Uri portrait=importFixture("qa-caption.mp4"), landscape=importFixture("qa-caption-rotated.mp4");
        final int gold=0xffffd786;
        for(int profile:new int[]{SocialExportProfile.CUSTOM,SocialExportProfile.INSTAGRAM_REELS}){
            for(Uri source:new Uri[]{portrait,landscape}){
                boolean rotated=landscape.equals(source);
                int ratio=profile==SocialExportProfile.CUSTOM&&rotated
                        ?AspectRatioProfile.LANDSCAPE_16_9:AspectRatioProfile.PORTRAIT_9_16;
                String title="CAPTION PLACEMENT FROM PREVIEW TO VIDEO";
                ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,ratio,AspectRatioProfile.FIT,
                        false,0,0).withSocialProfile(profile).withCaption(title,gold);
                byte[] before=hashSource(source);
                File encoded=realExport(source,options);
                try{
                    assertTrue(containsCodec(encoded,"video/avc"));
                    assertTrue(containsCodec(encoded,"audio/mp4a-latm"));
                    MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(encoded);
                    assertTrue("Video needs actual A/V samples",pts.videoSamples>10&&pts.audioSamples>15);
                    assertTrue("Caption export changed A/V timing",pts.lastPtsDriftMs()>=0&&pts.lastPtsDriftMs()<=250);
                    android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
                    try{
                        reader.setDataSource(encoded.getAbsolutePath());
                        android.graphics.Rect reference=null;
                        int w=profile==SocialExportProfile.CUSTOM&&rotated?1280:profile==SocialExportProfile.CUSTOM?720:1080;
                        int h=profile==SocialExportProfile.CUSTOM&&rotated?720:profile==SocialExportProfile.CUSTOM?1280:1920;
                        for(long time:new long[]{200000L,850000L}){
                            android.graphics.Bitmap frame=reader.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST);
                            assertNotNull("No decoded frame at "+time,frame);
                            try{
                                assertEquals("Decoded width",w,frame.getWidth());
                                assertEquals("Decoded height",h,frame.getHeight());
                                android.graphics.Rect actual=captionPixels(frame);
                                if(reference==null)reference=drawPreviewCaption(w,h,title,gold,profile);
                                assertEquals("Caption left",reference.left/(float)w,actual.left/(float)w,.04f);
                                assertEquals("Caption right",reference.right/(float)w,actual.right/(float)w,.04f);
                                assertEquals("Caption top",reference.top/(float)h,actual.top/(float)h,.025f);
                                assertEquals("Caption bottom",reference.bottom/(float)h,actual.bottom/(float)h,.025f);
                                SocialExportProfile.Profile zone=SocialExportProfile.get(profile);
                                assertTrue("Caption exceeds profile safe zone "+actual,
                                        actual.top>=zone.safeTop*h&&actual.bottom<=(1f-zone.safeBottom)*h);
                                assertTrue("Caption crosses horizontal safe margins "+actual,
                                        actual.left>=w*.035f&&actual.right<=w*.965f);
                                android.util.Log.i("WfsBeta11CaptionQA","profile="+profile+" rotated="+rotated+
                                        " canvas="+w+"x"+h+" frameUs="+time+" preview="+reference+
                                        " encoded="+actual+" sampleDriftMs="+pts.lastPtsDriftMs());
                            }finally{frame.recycle();}
                        }
                    }finally{reader.release();}
                    assertArrayEquals("Source video changed",before,hashSource(source));
                }finally{inst.runOnMainSync(engine::cleanup);}
            }
        }
    }

    @Test(timeout=450000)
    public void wrappedCaptionsOnOtherProfilesAndCustomSilentClips() throws Exception {
        Uri colorless=importFixture("qa-caption.mp4");
        Uri noAudio=importFixture("qa-caption-silent.mp4");
        String words="WIDE CAPTION WRAP KEEPS EVERY LINE INSIDE SOCIAL VIDEO SAFE AREA";
        String token="MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM";
        int gold=0xffffd786;
        ExportOptions base=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                AspectRatioProfile.FIT,false,0,0);
        for(int profile:new int[]{SocialExportProfile.WHATSAPP_STATUS,
                SocialExportProfile.INSTAGRAM_STORY,SocialExportProfile.TIKTOK}){
            boolean mute=profile==SocialExportProfile.TIKTOK;
            assertWrappedExport(colorless,base.withSocialProfile(profile)
                    .withCaption(words,gold).withMute(mute),1080,1920,!mute);
        }
        // Natural silent input and manual ratios, both tall and wide.
        assertWrappedExport(noAudio,base.withCustomSize(432,1008).withCaption(token,gold),
                432,1008,false);
        assertWrappedExport(noAudio,base.withCustomSize(1008,432).withCaption(token,gold),
                1008,432,false);
    }

    @Test(timeout=180000)
    public void mixedArabicLatinCjkCaptionMatchesDecodedVideoAndKeepsGraphemes() throws Exception {
        String mixed="مرحبا بالعالم START 2026 日本語字幕 — Ångström café, selamat datang!";
        // Unbroken text forces a fallback split. Each half must contain only
        // complete modifier/ZWJ emoji, decomposed letters, and flag pairs.
        String cluster="W\u0301👩🏽‍💻🇮🇩";
        String unbroken=String.join("",Collections.nCopies(6,cluster));
        CaptionRenderer renderer=new CaptionRenderer();
        String[] lines=renderer.layoutLines(unbroken,432,720);
        assertEquals(2,lines.length);
        assertEquals(unbroken,lines[0]+lines[1]);
        android.icu.text.BreakIterator chars=android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
        chars.setText(unbroken);
        assertTrue("Line split divided a grapheme",chars.isBoundary(lines[0].length()));
        assertEquals("Expected one decomposed letter, one emoji and one flag per repeat",
                18,countGraphemes(chars));
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                AspectRatioProfile.FIT,false).withCustomSize(432,720).withCaption(mixed,0xffffd786);
        Uri source=importFixture("qa-caption.mp4");
        assertWrappedExport(source,options,432,720,true,null,true);
        String accents=String.join("",Collections.nCopies(12,"W\u0301A\u0308N\u0303"));
        assertWrappedExport(source,options.withCaption(accents,0xffffd786),432,720,true,null,true);
        android.util.Log.i("WfsBeta13ColorQA","mixed RTL/LTR/CJK decoded; grapheme split UTF-16 offset="+lines[0].length());
    }

    @Test(timeout=210000)
    public void decodedPhotoAndMp4PassIndependentCaptionGlyphOracle() throws Exception {
        File grey=new File(ctx.getCacheDir(),"wfs-oracle-"+System.nanoTime()+".png");
        outputFiles.add(grey);
        android.graphics.Bitmap fixture=android.graphics.Bitmap.createBitmap(432,720,
                android.graphics.Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(grey)){
            fixture.eraseColor(0xff484848);
            assertTrue(fixture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }finally{fixture.recycle();}
        byte[] sourcePhoto=hashSource(Uri.fromFile(grey));
        Uri source=importFixture("qa-caption.mp4");
        byte[] sourceVideo=hashSource(source);
        for(String title:new String[]{CaptionGlyphOracle.BIDI,CaptionGlyphOracle.ACCENT_EMOJI}){
            ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                    AspectRatioProfile.FIT,false).withCustomSize(432,720).withCaption(title,0xffffd786);
            byte[] jpeg=PhotoEngine.process(ctx,Uri.fromFile(grey),options).jpeg;
            android.graphics.Bitmap photo=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
            assertNotNull(photo);
            try{CaptionGlyphOracle.verify(photo,title,"jpeg");}finally{photo.recycle();}
            File encoded=realExport(source,options);
            MediaMetadataRetriever video=new MediaMetadataRetriever();
            try{
                assertTrue(containsCodec(encoded,"video/avc"));
                MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(encoded);
                assertTrue(pts.videoSamples>10&&pts.audioSamples>10&&pts.lastPtsDriftMs()<=250);
                video.setDataSource(encoded.getAbsolutePath());
                android.graphics.Bitmap frame=video.getFrameAtTime(500000L,MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull(frame);
                try{CaptionGlyphOracle.verify(frame,title,"mp4");}finally{frame.recycle();}
            }finally{video.release();inst.runOnMainSync(engine::cleanup);}
        }
        assertArrayEquals(sourcePhoto,hashSource(Uri.fromFile(grey)));
        assertArrayEquals(sourceVideo,hashSource(source));
    }

    private static int countGraphemes(android.icu.text.BreakIterator chars){
        chars.first();
        int n=0;
        while(chars.next()!=android.icu.text.BreakIterator.DONE)n++;
        return n;
    }

    @Test public void nativePqAndHlgRemainNativeOnlyOnByteCopy() throws Exception {
        for(String transfer:new String[]{"arib-std-b67","smpte2084"}){
            Uri source=importFixture("qa-caption-"+transfer+".mp4");
            MediaProbe.Info input=MediaProbe.inspect(ctx,source);
            assertHdrFixture(input,transfer);
            byte[] sha=hashSource(source);
            ExportOptions noEdits=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                    AspectRatioProfile.FIT,true).withQuality(OutputProfile.MAX_DETAIL);
            assertTrue(VideoPassthrough.allowed(noEdits,"video/mp4"));
            assertFalse(VideoPassthrough.allowed(noEdits.withCaption("HDR caption",0xffffd786),"video/mp4"));
            Uri copy=MediaSaver.copyOriginalMp4(ctx,source,"wfs-qa-hdr-copy-"+System.nanoTime()+".mp4");
            try{
                assertArrayEquals("Gallery copy changed native HDR bytes",sha,hashSource(copy));
                assertHdrFixture(MediaProbe.inspect(ctx,copy),transfer);
                assertArrayEquals(sha,hashSource(source));
                android.util.Log.i("WfsBeta13ColorQA","gallery mode=native-byte-copy transfer="+transfer);
            }finally{MediaSaver.discardUnaccepted(ctx,copy);}
        }
    }

    @Test(timeout=180000) public void hlgCaptionToneMapsToSdrWhenSupported() throws Exception {
        assertHdrCaptionExport("arib-std-b67");
    }

    @Test(timeout=180000) public void hdr10CaptionToneMapsToSdrWhenSupported() throws Exception {
        assertHdrCaptionExport("smpte2084");
    }

    @Test(timeout=160000)
    public void timelinePreservesTrimOrderPtsAndCaptionWindowInDecodedMp4() throws Exception {
        Uri first=importFixture("qa-timeline-a.mp4"),second=importFixture("qa-timeline-b.mp4");
        byte[] a=hashSource(first),b=hashSource(second);
        TimelineProject draft=TimelineProject.empty()
                .append(TimelineProject.Clip.create(first.toString(),MediaProbe.duration(ctx,first),500,1500))
                .append(TimelineProject.Clip.create(second.toString(),MediaProbe.duration(ctx,second),500,1500))
                .move(1,0).caption(new TimelineProject.Caption("TIMED CAPTION",500,1500,0xffffd786));
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> output=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        mergeEngine=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            try{mergeEngine.startTimeline(ctx,draft,new VideoMergeEngine.Listener(){
                @Override public void onCompleted(File file){output.set(file);done.countDown();}
                @Override public void onFailure(String message){error.set(message);done.countDown();}
            });}catch(Exception ex){error.set(ex.toString());done.countDown();}
        });
        assertTrue("Timeline encoder timed out",done.await(125,TimeUnit.SECONDS));
        assertNull("Timeline export failed",error.get());
        File file=output.get();
        assertNotNull(file);outputFiles.add(file);
        assertTrue(containsCodec(file,"video/avc"));assertTrue(containsCodec(file,"audio/mp4a-latm"));
        MediaProbe.Info info=MediaProbe.inspect(file);
        assertTrue("Duration should reflect both 1s trims",Math.abs(info.durationMs-2000)<=250);
        MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(file);
        assertTrue(pts.videoSamples>=40&&pts.audioSamples>40);
        assertTrue("Audio sample drift "+pts.lastPtsDriftMs(),pts.lastPtsDriftMs()>=0&&pts.lastPtsDriftMs()<=250);
        try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
            reader.setDataSource(file.getAbsolutePath());
            for(int i=0;i<4;i++){
                long t=250000L+i*500000;
                android.graphics.Bitmap img=reader.getFrameAtTime(t,MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull("Missing decoded timeline frame at "+t,img);
                try{
                    assertEquals(320,img.getWidth());assertEquals(568,img.getHeight());
                    int grey=(img.getPixel(120,120)>>16)&255;
                    assertTrue("Wrong trim or order at "+t+" luma="+grey,
                            i<2?grey>=145&&grey<=205:grey>=50&&grey<=105);
                    CaptionPixels pixels=scanCaption(img);
                    assertEquals("Caption window wrong at "+t,i==1||i==2,!pixels.bounds.isEmpty());
                    android.util.Log.i("WfsBeta15TimelineQA","frameUs="+t+" luma="+grey+
                            " caption="+pixels.bounds.flattenToString()+" driftMs="+pts.lastPtsDriftMs());
                }finally{img.recycle();}
            }
        }
        assertArrayEquals(a,hashSource(first));assertArrayEquals(b,hashSource(second));
    }

    @Test(timeout=240000)
    public void timelinePreviewUsesRenderedCaptionAndCutsAndRemovesPrivateFileAfterRecreation() throws Exception {
        Uri first=importFixture("qa-timeline-a.mp4"),second=importFixture("qa-timeline-b.mp4");
        byte[] firstHash=hashSource(first),secondHash=hashSource(second);
        TimelineProject draft=TimelineProject.empty()
                .append(TimelineProject.Clip.create(first.toString(),MediaProbe.duration(ctx,first),500,1500))
                .append(TimelineProject.Clip.create(second.toString(),MediaProbe.duration(ctx,second),500,1500))
                .move(1,0).caption(new TimelineProject.Caption("TIMED CAPTION",500,1500,0xffffd786));
        assertTrue(TimelineDraftStore.save(ctx,draft));
        java.util.Set<File> before=previewFiles();
        java.util.Set<Long> galleryBefore=storyBoostGalleryRows();
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> exported=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        mergeEngine=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            try{mergeEngine.startTimeline(ctx,draft,new VideoMergeEngine.Listener(){
                @Override public void onCompleted(File file){exported.set(file);done.countDown();}
                @Override public void onFailure(String message){error.set(message);done.countDown();}
            });}catch(Exception ex){error.set(ex.toString());done.countDown();}
        });
        try {
            assertTrue("Export composition timed out",done.await(100,TimeUnit.SECONDS));
            assertNull(error.get());assertNotNull(exported.get());
            Intent open=new Intent(ctx,StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
            UiDevice device=UiDevice.getInstance(inst);
            try(ActivityScenario<StudioMergeActivity> activity=ActivityScenario.launch(open)){
                activity.onActivity(a->{
                    android.view.View view=findButton(a.getWindow().getDecorView(),"Preview Timeline");
                    assertNotNull(view);assertTrue(view.isEnabled());assertTrue(view.performClick());
                });
                assertTrue("Rendered Preview Timeline player did not open",
                        device.wait(Until.hasObject(By.desc("Posisi waktu Preview Timeline").enabled(true)),100000));
                File rendered=onlyNewPreview(before);
                assertEquals("Preview published a Gallery item",galleryBefore,storyBoostGalleryRows());
                assertTrue("Two-second preview did not finish playback",
                        device.wait(Until.hasObject(By.text("Putar")),10000));
                android.graphics.Rect seek=device.findObject(By.desc("Posisi waktu Preview Timeline"))
                        .getVisibleBounds();
                assertTrue(device.click(seek.left+seek.width()*30/100,seek.centerY()));
                assertTrue("Seek before the cut did not reach clip 1",device.wait(Until.hasObject(
                        By.text(java.util.regex.Pattern.compile(".* ·  Klip 1"))),5000));
                assertTrue(device.click(seek.left+seek.width()*70/100,seek.centerY()));
                assertTrue("Seek after the cut did not reach clip 2",device.wait(Until.hasObject(
                        By.text(java.util.regex.Pattern.compile(".* ·  Klip 2"))),5000));
                // End the burst on the first side of the cut and wait for the
                // actual settled label (the UI displays Mencari posisi until completion).
                for(int pct:new int[]{25,80,35,75,30})
                    assertTrue(device.click(seek.left+seek.width()*pct/100,seek.centerY()));
                assertTrue("Last rapid seek must win",device.wait(Until.hasObject(
                        By.text(java.util.regex.Pattern.compile(".* ·  Klip 1"))),5000));
                try (MediaMetadataRetriever previewReader=new MediaMetadataRetriever();
                     MediaMetadataRetriever exportReader=new MediaMetadataRetriever()){
                    previewReader.setDataSource(rendered.getAbsolutePath());
                    exportReader.setDataSource(exported.get().getAbsolutePath());
                    for(long timeUs:new long[]{250_000,750_000,1_250_000,1_750_000}){
                        android.graphics.Bitmap a=previewReader.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST);
                        android.graphics.Bitmap b=exportReader.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST);
                        assertNotNull("Preview frame "+timeUs,a);assertNotNull("Export frame "+timeUs,b);
                        try{
                            assertEquals(b.getWidth(),a.getWidth());assertEquals(b.getHeight(),a.getHeight());
                            CaptionPixels previewCaption=scanCaption(a),exportCaption=scanCaption(b);
                            assertEquals(timeUs>=500_000&&timeUs<1_500_000,!previewCaption.bounds.isEmpty());
                            assertEquals(exportCaption.bounds.isEmpty(),previewCaption.bounds.isEmpty());
                            if(!exportCaption.bounds.isEmpty()){
                                assertTrue(Math.abs(exportCaption.bounds.top-previewCaption.bounds.top)<=2);
                                assertTrue(Math.abs(exportCaption.bounds.left-previewCaption.bounds.left)<=2);
                            }
                            int previewGrey=(a.getPixel(120,120)>>16)&255;
                            int exportGrey=(b.getPixel(120,120)>>16)&255;
                            assertTrue("Preview cut marker "+timeUs+" differs from export",Math.abs(previewGrey-exportGrey)<=3);
                        }finally{a.recycle();b.recycle();}
                    }
                }
                activity.recreate();
                assertFalse("Previous Activity must release the private MP4",rendered.exists());
                assertEquals("Preview/recreation changed Gallery",galleryBefore,storyBoostGalleryRows());
                assertEquals("Unexpected private files after recreation",before,previewFiles());
                assertEquals(draft.clips.get(0).id,TimelineDraftStore.load(ctx).clips.get(0).id);
                assertEquals(draft.caption.out,TimelineDraftStore.load(ctx).caption.out);
                assertArrayEquals(firstHash,hashSource(first));assertArrayEquals(secondHash,hashSource(second));
            }
        }finally{TimelineDraftStore.clear(ctx);}
    }

    @Test(timeout=45000)
    public void timelinePrivatePreviewRejectsDeletedSourceAndCanCancelBeforeCompletion() throws Exception {
        Uri deleted=importFixture("qa-timeline-a.mp4"),kept=importFixture("qa-timeline-b.mp4");
        TimelineProject valid=TimelineProject.empty()
                .append(TimelineProject.Clip.create(kept.toString(),MediaProbe.duration(ctx,kept),500,1500));
        TimelineProject unavailable=valid.append(TimelineProject.Clip.create(deleted.toString(),
                MediaProbe.duration(ctx,deleted),500,1500));
        assertEquals(1,ctx.getContentResolver().delete(deleted,null,null));
        java.util.Set<File> before=previewFiles();
        byte[] sha=hashSource(kept);
        byte[] callbacks=new byte[1];
        VideoMergeEngine.Listener listener=new VideoMergeEngine.Listener(){
            @Override public void onCompleted(File file){callbacks[0]++;}
            @Override public void onFailure(String message){callbacks[0]++;}
        };
        inst.runOnMainSync(()->{
            VideoMergeEngine worker=new VideoMergeEngine();
            try{
                worker.startTimelinePreview(ctx,TimelineProject.empty(),listener);
                fail("An empty timeline produced a private preview");
            }catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Timeline kosong"));}
            try{
                worker.startTimelinePreview(ctx,unavailable,listener);
                fail("Deleted source was accepted for partial preview");
            }catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("sumber timeline"));}
            worker.startTimelinePreview(ctx,valid,listener);
            worker.cancel();
        });
        inst.waitForIdleSync();
        assertEquals("Cancelled render must not send a completion or error callback",0,callbacks[0]);
        assertEquals(before,previewFiles());
        assertArrayEquals(sha,hashSource(kept));
    }

    @Test(timeout=90000)
    public void timelineInteractiveSplitAndSoundtrackPanel()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");
        byte[] hash=hashSource(source);
        TimelineProject base=TimelineProject.empty()
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .soundtrack(new TimelineProject.Audio(source.toString(),2000,0,500,250,50));
        assertTrue(TimelineDraftStore.save(ctx,base));
        Intent open=new Intent(ctx,StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
        try(ActivityScenario<StudioMergeActivity> activity=ActivityScenario.launch(open)){
            // A real key event on the focused native seek bar changes the playhead.
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Geser playhead timeline"))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click(),
                            androidx.test.espresso.action.ViewActions.pressKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
            tapTimelineControl("Split");
            TimelineProject split=TimelineDraftStore.load(ctx);assertEquals(2,split.clips.size());
            assertEquals(1000,split.duration());assertEquals(750,split.audio.end());
            tapTimelineControl("Duplikat");
            assertEquals(3,TimelineDraftStore.load(ctx).clips.size());
            tapTimelineControl("Undo");assertEquals(2,TimelineDraftStore.load(ctx).clips.size());
            tapTimelineControl("Edit audio");
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Volume audio (1–100%)"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.replaceText("35"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            assertEquals(50,TimelineDraftStore.load(ctx).audio.volume);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            assertEquals(35,TimelineDraftStore.load(ctx).audio.volume);
            activity.recreate();assertEquals(35,TimelineDraftStore.load(ctx).audio.volume);
            assertEquals(2,TimelineDraftStore.load(ctx).clips.size());
            assertArrayEquals(hash,hashSource(source));
        }finally{TimelineDraftStore.clear(ctx);}
    }

    @Test(timeout=90000)
    public void timelineVoiceAndKeyframeUiPersists()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");byte[] hash=hashSource(source);
        TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create(source.toString(),2000,0,2000))
            .voiceTrack(new TimelineProject.Audio(source.toString(),2000,0,1000,500,30));
        assertTrue(TimelineDraftStore.save(ctx,project));
        Intent open=new Intent(ctx,StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
        try(ActivityScenario<StudioMergeActivity> activity=ActivityScenario.launch(open)){
            tapTimelineControl("Edit voice");
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Volume audio (1–100%)"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.replaceText("45"),
                    androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            assertEquals(30,TimelineDraftStore.load(ctx).voice.volume);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            assertEquals(45,TimelineDraftStore.load(ctx).voice.volume);
            tapTimelineControl("Keyframe");
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Gain awal (25–150%)"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.replaceText("40"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            assertEquals(40,TimelineDraftStore.load(ctx).clips.get(0).gainStart);
            tapTimelineControl("Undo");assertEquals(100,TimelineDraftStore.load(ctx).clips.get(0).gainStart);
            tapTimelineControl("Redo");assertEquals(40,TimelineDraftStore.load(ctx).clips.get(0).gainStart);
            activity.recreate();assertEquals(45,TimelineDraftStore.load(ctx).voice.volume);
            assertEquals(40,TimelineDraftStore.load(ctx).clips.get(0).gainStart);
            assertArrayEquals(hash,hashSource(source));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test(timeout=600000)
    public void timelineMixedCutsAndPictureLayerDecodedProof()throws Exception{
        Uri red=importFixture("qa-crossfade-red.mp4"),blue=importFixture("qa-crossfade-blue.mp4");
        byte[] hashRed=hashSource(red),hashBlue=hashSource(blue);
        TimelineProject project=TimelineProject.empty()
            .append(TimelineProject.Clip.create(red.toString(),2000,0,2000))
            .append(TimelineProject.Clip.create(blue.toString(),2000,0,2000))
            .append(TimelineProject.Clip.create(red.toString(),2000,0,2000))
            .cut(0,500).picture(new PictureLayer(red.toString(),2000,0,1500,1500,35,50,100,0,100,0,PictureLayer.SMOOTH,1500));
        assertEquals(5500,project.duration());assertEquals(3500,project.startOf(2));assertEquals(0,project.cutMs(1));
        File output=null,preview=null;
        try{
            output=renderAdjusted(project,false,280,null);preview=renderAdjusted(project,true,280,null);
            for(File file:new File[]{output,preview}){
                MediaProbe.Info info=MediaProbe.inspect(file);assertTrue(info.video&&info.audio);assertTrue(Math.abs(info.durationMs-5500)<=250);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file,300000,900000,1680000,1820000,2400000,3000000);
                double early=pcm.firstTones.tone440(),late=pcm.lastTones.tone780(),mixA=pcm.overlapTones.tone440(),mixB=pcm.overlapTones.tone780();
                assertTrue(early>.05&&late>.05&&pcm.firstTones.tone780()<early*.1&&pcm.lastTones.tone440()<late*.1);
                assertTrue(mixA>early*.25&&mixA<early*.75&&mixB>late*.25&&mixB<late*.75);
                PcmWindowOracle.Result cut= PcmWindowOracle.decode(file,300000,900000,3100000,3300000,4000000,4600000);
                assertTrue(cut.overlapTones.tone780()>.05&&cut.overlapTones.tone440()<.01);
                assertTrue(cut.lastTones.tone440()>.05&&cut.lastTones.tone780()<.01);
                int mixRed,mixBlue,pipRed,pipBlue,baseRed,baseBlue,finalRed,finalBlue;
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    int mixed=sampleColor(reader,1750000,.5,.5),overlay=sampleColor(reader,2500000,.8,.2),
                        base=sampleColor(reader,2500000,.5,.5),end=sampleColor(reader,4500000,.5,.5);
                    mixRed=android.graphics.Color.red(mixed);mixBlue=android.graphics.Color.blue(mixed);
                    pipRed=android.graphics.Color.red(overlay);pipBlue=android.graphics.Color.blue(overlay);
                    baseRed=android.graphics.Color.red(base);baseBlue=android.graphics.Color.blue(base);
                    finalRed=android.graphics.Color.red(end);finalBlue=android.graphics.Color.blue(end);
                }
                android.util.Log.i("WfsTimelinePreviewQA","mixedCutObserved duration="+info.durationMs+" colors="+mixRed+","+mixBlue+","+pipRed+","+pipBlue+","+baseRed+","+baseBlue+","+finalRed+","+finalBlue);
                assertTrue(mixRed>75&&mixRed<180&&mixBlue>75&&mixBlue<180);
                assertTrue("PiP half opacity must blend red on blue",pipRed>75&&pipRed<180&&pipBlue>75&&pipBlue<180);
                assertTrue(baseRed<30&&baseBlue>200&&finalRed>200&&finalBlue<30);
                assertTrue(MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs()<=250);
                android.util.Log.i("WfsTimelinePreviewQA","mixedCutVerified duration="+info.durationMs+" mixRed="+mixRed+" mixBlue="+mixBlue+" pipRed="+pipRed+" pipBlue="+pipBlue+" baseBlue="+baseBlue+" finalRed="+finalRed);
            }
            assertArrayEquals(hashRed,hashSource(red));assertArrayEquals(hashBlue,hashSource(blue));
        }finally{if(output!=null)output.delete();if(preview!=null)preview.delete();}
    }
    private static int sampleColor(MediaMetadataRetriever reader,long us,double x,double y){
        android.graphics.Bitmap image=reader.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST);
        assertNotNull(image);try{return image.getPixel((int)(x*image.getWidth()),(int)(y*image.getHeight()));}finally{image.recycle();}
    }
    @SdkSuppress(minSdkVersion=35)
    @Test(timeout=1800000)
    public void timelineHdBenchmarkActualAndroid()throws Exception{
        Uri source=importFixture("qa-hd-benchmark.mp4");byte[] hash=hashSource(source);
        long duration=MediaProbe.duration(ctx,source);assertTrue(duration>=30000);
        for(int seconds:new int[]{2,10,30})for(boolean preview:new boolean[]{false,true}){
            TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create(source.toString(),duration,0,seconds*1000L))
                .quality(OutputProfile.HD).adjust(0,new ClipAdjustments(ColorLooks.ORIGINAL,8,0,0,false));
            File file=null;long[] pss={android.os.Debug.getPss()};
            try{
                long begin=android.os.SystemClock.elapsedRealtime();file=renderAdjusted(project,preview,280,pss);
                long elapsed=android.os.SystemClock.elapsedRealtime()-begin;
                MediaProbe.Info info=MediaProbe.inspect(file);AspectRatioProfile.Size size=MediaDimensions.video(file);
                assertEquals(1280,size.width);assertEquals(720,size.height);assertTrue(info.video&&info.audio);
                assertTrue(containsCodec(file,"video/avc"));assertTrue(containsCodec(file,"audio/mp4a-latm"));
                assertTrue(Math.abs(info.durationMs-seconds*1000L)<=250);
                MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(file);
                assertTrue(pts.lastPtsDriftMs()<=250&&pts.videoSamples>=seconds*20);
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    sampleColor(reader,250000,.5,.5);sampleColor(reader,seconds*500000L,.5,.5);sampleColor(reader,seconds*1000000L-250000,.5,.5);
                }
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file,200000,800000,seconds*1000000L-800000,seconds*1000000L-200000,0,0);
                assertTrue(pcm.firstTones.tone440()>.04&&pcm.overlapTones.tone440()>.04);
                long videoBytes=0;MediaExtractor extractor=new MediaExtractor();
                try{
                    extractor.setDataSource(file.getAbsolutePath());int video=-1;
                    for(int i=0;i<extractor.getTrackCount();i++)if("video/avc".equals(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))){video=i;break;}
                    assertTrue(video>=0);extractor.selectTrack(video);
                    while(extractor.getSampleSize()>=0){videoBytes+=extractor.getSampleSize();if(!extractor.advance())break;}
                }finally{extractor.release();}
                long bitrate=videoBytes*8000L/Math.max(1,info.videoDurationMs);
                assertTrue(bitrate>0&&elapsed>0&&pss[0]>0);
                android.util.Log.i("WfsTimelinePreviewQA","hdBenchmarkVerified seconds="+seconds+" mode="+(preview?"preview":"export")+" elapsedMs="+elapsed+" bytes="+file.length()+" videoBitrate="+bitrate+" sampledPssKb="+pss[0]+" frames="+pts.videoSamples+" driftMs="+pts.lastPtsDriftMs());
            }finally{if(file!=null)file.delete();}
        }
        assertArrayEquals(hash,hashSource(source));
    }
    @Test(timeout=90000)
    public void timelinePictureLayerUiStagesUndoAndRecreate()throws Exception{
        Uri a=importFixture("qa-crossfade-red.mp4"),b=importFixture("qa-crossfade-blue.mp4");
        byte[] sha=hashSource(b);
        TimelineProject draft=TimelineProject.empty().append(TimelineProject.Clip.create(a.toString(),2000,0,2000))
            .picture(new PictureLayer(b.toString(),2000,0,1000,500,40,100,0,0,100,100));
        assertTrue(TimelineDraftStore.save(ctx,draft));
        Intent open=new Intent(ctx,StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
        try(ActivityScenario<StudioMergeActivity> activity=ActivityScenario.launch(open)){
            tapTimelineControl("Edit PiP");
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("PiP ukuran (20–60%)"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.scrollTo(),
                    androidx.test.espresso.action.ViewActions.replaceText("50"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            assertEquals(40,TimelineDraftStore.load(ctx).picture.scale);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            assertEquals(50,TimelineDraftStore.load(ctx).picture.scale);
            tapTimelineControl("Undo");assertEquals(40,TimelineDraftStore.load(ctx).picture.scale);
            tapTimelineControl("Redo");assertEquals(50,TimelineDraftStore.load(ctx).picture.scale);
            activity.recreate();assertEquals(50,TimelineDraftStore.load(ctx).picture.scale);
            assertArrayEquals(sha,hashSource(b));
        }finally{TimelineDraftStore.clear(ctx);}
    }
    @Test(timeout=300000)
    public void timelinePictureLayerRendersRealMovingOverlay()throws Exception{
        Uri red=importFixture("qa-crossfade-red.mp4"),blue=importFixture("qa-crossfade-blue.mp4");
        byte[] a=hashSource(red),b=hashSource(blue);
        TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create(red.toString(),2000,0,2000))
                .picture(new PictureLayer(blue.toString(),2000,0,1000,500,40,100,0,0,100,100));
        File output=null,preview=null;
        try{
            output=renderAdjusted(project,false);preview=renderAdjusted(project,true);
            for(File file:new File[]{output,preview}){
                MediaProbe.Info info=MediaProbe.inspect(file);assertTrue(info.video&&info.audio);assertTrue(Math.abs(info.durationMs-2000)<=250);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file);
                assertTrue(pcm.firstTones.tone440()>.05&&pcm.firstTones.tone780()<.01&&pcm.overlapTones.tone440()>.05&&pcm.overlapTones.tone780()<.01);
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    double[] cx=new double[4],cy=new double[4];int[] count=new int[4];
                    long[] times={250000,650000,1350000,1750000};
                    for(int i=0;i<4;i++){
                        android.graphics.Bitmap frame=reader.getFrameAtTime(times[i],MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);
                        try{
                            for(int y=0;y<frame.getHeight();y+=2)for(int x=0;x<frame.getWidth();x+=2){
                                int color=frame.getPixel(x,y);
                                if(android.graphics.Color.blue(color)>180&&android.graphics.Color.red(color)<50){
                                    count[i]++;cx[i]+=x/(double)frame.getWidth();cy[i]+=y/(double)frame.getHeight();
                                }
                            }
                            if(count[i]>0){cx[i]/=count[i];cy[i]/=count[i];}
                        }finally{frame.recycle();}
                    }
                    android.util.Log.i("WfsTimelinePreviewQA","pipObserved count="+java.util.Arrays.toString(count)+" x="+java.util.Arrays.toString(cx)+" y="+java.util.Arrays.toString(cy));
                    assertEquals("Overlay before start",0,count[0]);assertEquals("Overlay after end",0,count[3]);
                    assertTrue(count[1]>500&&count[2]>500);
                    assertTrue("PiP must move diagonally",cx[2]-cx[1]>.25&&cy[2]-cy[1]>.25);
                    android.util.Log.i("WfsTimelinePreviewQA","pipVerified before="+count[0]+" early="+count[1]+" late="+count[2]+" after="+count[3]+" dx="+(cx[2]-cx[1])+" dy="+(cy[2]-cy[1]));
                }
                assertTrue(MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs()<=250);
            }
            assertArrayEquals(a,hashSource(red));assertArrayEquals(b,hashSource(blue));
        }finally{if(output!=null)output.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=300000)
    public void timelineCrossfadeRendersOverlappingVideoAndAudio()throws Exception{
        Uri a=importFixture("qa-crossfade-red.mp4"),b=importFixture("qa-crossfade-blue.mp4");
        byte[] shaA=hashSource(a),shaB=hashSource(b);
        TimelineProject project=TimelineProject.empty().append(TimelineProject.Clip.create(a.toString(),2000,0,2000))
                .append(TimelineProject.Clip.create(b.toString(),2000,0,2000)).crossfade(500);
        assertEquals(3500,project.duration());
        File output=null,preview=null;
        try{
            output=renderAdjusted(project,false);preview=renderAdjusted(project,true);
            for(File file:new File[]{output,preview}){
                MediaProbe.Info info=MediaProbe.inspect(file);
                assertTrue(info.video&&info.audio);assertTrue(Math.abs(info.durationMs-3500)<=250);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file,300000,900000,1680000,1820000,2400000,3000000);
                double early=pcm.firstTones.tone440(),earlyLeak=pcm.firstTones.tone780(),
                        middleA=pcm.overlapTones.tone440(),middleB=pcm.overlapTones.tone780(),
                        late=pcm.lastTones.tone780(),lateLeak=pcm.lastTones.tone440();
                int[] pixels=new int[3];
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    long[] times={500000,1750000,2750000};
                    for(int i=0;i<3;i++){
                        android.graphics.Bitmap frame=reader.getFrameAtTime(times[i],MediaMetadataRetriever.OPTION_CLOSEST);
                        assertNotNull(frame);
                        try{pixels[i]=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);}finally{frame.recycle();}
                    }
                }
                int red0=android.graphics.Color.red(pixels[0]),blue0=android.graphics.Color.blue(pixels[0]),
                        red1=android.graphics.Color.red(pixels[1]),blue1=android.graphics.Color.blue(pixels[1]),
                        red2=android.graphics.Color.red(pixels[2]),blue2=android.graphics.Color.blue(pixels[2]);
                android.util.Log.i("WfsTimelinePreviewQA","crossfadeObserved duration="+info.durationMs+" rgb="+red0+","+blue0+","+red1+","+blue1+","+red2+","+blue2+" tones="+early+","+earlyLeak+","+middleA+","+middleB+","+late+","+lateLeak);
                assertTrue("Red initial frame",red0>200&&blue0<30);
                assertTrue("Both decoded video inputs must overlap",red1>75&&red1<180&&blue1>75&&blue1<180);
                assertTrue("Blue final frame",blue2>200&&red2<30);
                assertTrue("Early tone isolation",early>.05&&earlyLeak<early*.1);
                assertTrue("Late tone isolation",late>.05&&lateLeak<late*.1);
                assertTrue("Both audio ramps must overlap",middleA>early*.25&&middleA<early*.75&&middleB>late*.25&&middleB<late*.75);
                assertTrue(MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs()<=250);
                android.util.Log.i("WfsTimelinePreviewQA","crossfadeVerified duration="+info.durationMs+" midRed="+red1+" midBlue="+blue1+" early440="+early+" early780="+earlyLeak+" overlap440="+middleA+" overlap780="+middleB+" late780="+late+" late440="+lateLeak);
            }
            assertArrayEquals(shaA,hashSource(a));assertArrayEquals(shaB,hashSource(b));
        }finally{if(output!=null)output.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=300000)
    public void timelineZoomTransformsActualFrames()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");byte[] hash=hashSource(source);
        TimelineProject project=TimelineProject.empty()
            .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
            .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
            .zoom(0,50,50).zoom(1,150,150);
        File output=renderAdjusted(project,false),preview=null;
        try{
            preview=renderAdjusted(project,true);
            for(File file:new File[]{output,preview}){
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    android.graphics.Bitmap small=reader.getFrameAtTime(350000,MediaMetadataRetriever.OPTION_CLOSEST);
                    android.graphics.Bitmap large=reader.getFrameAtTime(1350000,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertNotNull(small);assertNotNull(large);
                    try{
                        assertTrue("Zoom out must introduce black border",((small.getPixel(15,15)>>16)&255)<25);
                        assertTrue("Zoom in must fill the frame",((large.getPixel(15,15)>>16)&255)>70);
                        assertTrue("Zoom out center must remain visible",((small.getPixel(small.getWidth()/2,small.getHeight()/2)>>16)&255)>70);
                    }finally{small.recycle();large.recycle();}
                }
            }
            assertArrayEquals(hash,hashSource(source));
        }finally{output.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=90000)
    public void timelineWaveformCoversLateAudio()throws Exception{
        Uri source=importFixture("qa-waveform-late.mp4");byte[] original=hashSource(source);
        long duration=MediaProbe.inspect(ctx,source).audioDurationMs;
        assertTrue(duration>=19950);
        long end=Math.min(20000,duration);
        float[] waveform=AudioWaveformView.decode(ctx,new TimelineProject.Audio(source.toString(),duration,0,end,0,100));
        float early=0,late=0;
        for(int i=0;i<60;i++)early=Math.max(early,waveform[i]);
        for(int i=68;i<waveform.length;i++)late=Math.max(late,waveform[i]);
        assertTrue("Late waveform tone missing",late>.03f);
        assertTrue("Early silent segment must be quiet",early<late*.1f);
        assertArrayEquals(original,hashSource(source));
    }
    @Test(timeout=300000)
    public void timelineDualTrackKeyframeAndWaveformRealMedia()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");byte[] hash=hashSource(source);
        // Distinct tones remove phase-cancellation ambiguity: b=780Hz, a=440Hz.
        Uri voiceSource=importFixture("qa-timeline-a.mp4");byte[] voiceHash=hashSource(voiceSource);
        TimelineProject.Audio music=new TimelineProject.Audio(source.toString(),2000,0,2000,0,35);
        TimelineProject.Audio voice=new TimelineProject.Audio(voiceSource.toString(),2000,500,1500,1000,35);
        float[] peaks=AudioWaveformView.decode(ctx,music);assertEquals(AudioWaveformView.BINS,peaks.length);
        float peak=0;for(float v:peaks){assertTrue(v>=0&&v<=1);peak=Math.max(peak,v);}assertTrue(peak>.015f);
        TimelineProject draft=TimelineProject.empty()
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .adjust(0,new ClipAdjustments(ColorLooks.ORIGINAL,0,0,0,true))
                .adjust(1,new ClipAdjustments(ColorLooks.ORIGINAL,0,0,0,true))
                .soundtrack(music).voiceTrack(voice).keyframes(0,25,60).keyframes(1,60,100);
        File output=renderAdjusted(draft,false),preview=null;
        try{
            preview=renderAdjusted(draft,true);
            for(File file:new File[]{output,preview}){
                assertTrue(MediaProbe.inspect(file).audio);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file);
                double early780=pcm.firstTones.tone780(),early440=pcm.firstTones.tone440(),late780=pcm.overlapTones.tone780(),late440=pcm.overlapTones.tone440();
                android.util.Log.i("WfsTimelinePreviewQA","dualTrackObserved firstRms="+pcm.mutedRms()+" overlapRms="+pcm.audibleRms()+" early780="+early780+" early440="+early440+" late780="+late780+" late440="+late440);
                assertTrue("First audio lane must be audible",pcm.mutedRms()>.015);
                assertTrue("Both distinct tones must survive overlap, with no early voice leakage",PcmToneProbe.provesOverlap(early780,early440,late780,late440));
                assertTrue("Overlapping second lane must raise RMS",pcm.audibleRms()>pcm.mutedRms()*1.3);
                assertTrue(MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs()<=250);
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(file.getAbsolutePath());
                    android.graphics.Bitmap first=reader.getFrameAtTime(300000,MediaMetadataRetriever.OPTION_CLOSEST);
                    android.graphics.Bitmap last=reader.getFrameAtTime(1700000,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertNotNull(first);assertNotNull(last);
                    try{assertTrue("Gain animation must brighten frame",((first.getPixel(120,120)>>16)&255)+10<((last.getPixel(120,120)>>16)&255));}
                    finally{first.recycle();last.recycle();}
                }
                android.util.Log.i("WfsTimelinePreviewQA","dualTrackFirstRms="+pcm.mutedRms()+" overlapRms="+pcm.audibleRms()+" early780="+early780+" early440="+early440+" late780="+late780+" late440="+late440+" keyframe=verified");
            }
            assertArrayEquals(hash,hashSource(source));assertArrayEquals(voiceHash,hashSource(voiceSource));
        }finally{output.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=300000)
    public void timelineSoundtrackAndDipRenderRealMedia()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");
        byte[] hash=hashSource(source);
        ClipAdjustments mute=new ClipAdjustments(ColorLooks.ORIGINAL,0,0,0,true);
        TimelineProject draft=TimelineProject.empty()
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .adjust(0,mute).adjust(1,mute)
                .soundtrack(new TimelineProject.Audio(source.toString(),2000,500,1500,1000,50)).fade(300);
        File output=renderAdjusted(draft,false),preview=null;
        try{
            preview=renderAdjusted(draft,true);
            for(File file:new File[]{output,preview}){
                MediaProbe.Info info=MediaProbe.inspect(file);assertTrue(info.video&&info.audio);
                assertTrue(Math.abs(info.durationMs-2000)<=250);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file);
                assertTrue("Independent audio not audible",pcm.audibleRms()>0.015);
                assertTrue("Soundtrack began early",pcm.mutedRms()<pcm.audibleRms()*0.1);
                assertTrue(MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs()<=250);
                try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){
                    retriever.setDataSource(file.getAbsolutePath());
                    android.graphics.Bitmap normal=retriever.getFrameAtTime(350000,MediaMetadataRetriever.OPTION_CLOSEST);
                    android.graphics.Bitmap dip=retriever.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertNotNull(normal);assertNotNull(dip);
                    try{assertTrue("Cut must visibly dip",((dip.getPixel(120,120)>>16)&255)+15<((normal.getPixel(120,120)>>16)&255));}
                    finally{normal.recycle();dip.recycle();}
                }
                android.util.Log.i("WfsTimelinePreviewQA","soundtrackRms="+pcm.audibleRms()+" gapRms="+pcm.mutedRms()+" dip=verified");
            }
            assertArrayEquals(hash,hashSource(source));
        }finally{output.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=450000)
    public void timelineAdjustedRepeatedClipRendersColorAndMute()throws Exception{
        Uri source=importFixture("qa-timeline-b.mp4");
        byte[] hash=hashSource(source);
        TimelineProject base=TimelineProject.empty()
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500))
                .append(TimelineProject.Clip.create(source.toString(),2000,500,1500));
        TimelineProject edited=base.adjust(0,new ClipAdjustments(ColorLooks.ORIGINAL,-100,0,0,true));
        File export=renderAdjusted(edited,false);
        File preview=null;
        try{
            preview=renderAdjusted(edited,true);
            for(File file:new File[]{export,preview}){
                MediaProbe.Info info=MediaProbe.inspect(file);assertTrue(info.video&&info.audio);
                assertTrue(Math.abs(info.durationMs-2000)<=250);
                long drift=MediaProbe.inspectSampleTimeline(file).lastPtsDriftMs();
                assertTrue(drift>=0&&drift<=250);
                PcmWindowOracle.Result pcm=PcmWindowOracle.decode(file);
                assertTrue("Audible clip unexpectedly silent",pcm.audibleRms()>0.015);
                assertTrue("Muted clip leaked audio",pcm.mutedRms()<pcm.audibleRms()*0.1);
                android.util.Log.i("WfsTimelinePreviewQA","mutedRms="+pcm.mutedRms()+" audibleRms="+pcm.audibleRms());
            }
            try(MediaMetadataRetriever a=new MediaMetadataRetriever();MediaMetadataRetriever b=new MediaMetadataRetriever()){
                a.setDataSource(export.getAbsolutePath());b.setDataSource(preview.getAbsolutePath());
                int[] levels=new int[2];
                int k=0;
                for(long us:new long[]{350000,1350000}){
                    android.graphics.Bitmap x=a.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST);
                    android.graphics.Bitmap y=b.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertNotNull(x);assertNotNull(y);
                    try{
                        levels[k++]=(x.getPixel(120,120)>>16)&255;
                        assertTrue(Math.abs(((x.getPixel(120,120)>>16)&255)-((y.getPixel(120,120)>>16)&255))<=3);
                    }finally{x.recycle();y.recycle();}
                }
                assertTrue("Adjusted first clip must be darker than repeated neutral source",levels[0]+15<levels[1]);
            }
            File silentOutput=renderAdjusted(edited.adjust(1,new ClipAdjustments(ColorLooks.ORIGINAL,0,0,0,true)),false);
            try{assertFalse("All muted clips must not publish AAC",MediaProbe.inspect(silentOutput).audio);}
            finally{silentOutput.delete();}
            assertArrayEquals(hash,hashSource(source));
        }finally{export.delete();if(preview!=null)preview.delete();}
    }
    @Test(timeout=90000)
    public void timelineAdjustmentPanelStagesCancelApplyAndReset()throws Exception{
        Uri source=importFixture("qa-timeline-a.mp4");
        byte[] original=hashSource(source);
        TimelineProject draft=TimelineProject.empty().append(
                TimelineProject.Clip.create(source.toString(),MediaProbe.duration(ctx,source),500,1500));
        assertTrue(TimelineDraftStore.save(ctx,draft));
        Intent open=new Intent(ctx,StudioMergeActivity.class).putExtra(StudioMergeActivity.EXTRA_TIMELINE,true);
        try(ActivityScenario<StudioMergeActivity> a=ActivityScenario.launch(open)){
            openAdjustments(a);
            tapAdjustmentMute(false);
            // Staged edits may not change the persisted project before Apply.
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.same(ClipAdjustments.NEUTRAL));
            tapDialog(android.R.id.button2,"Batalkan penyesuaian klip");
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.same(ClipAdjustments.NEUTRAL));
            openAdjustments(a);
            tapAdjustmentMute(false);
            tapDialog(android.R.id.button1,"Terapkan penyesuaian klip");
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.mute);
            tapTimelineControl("Undo");
            assertFalse(TimelineDraftStore.load(ctx).clips.get(0).adjustments.mute);
            tapTimelineControl("Redo");
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.mute);
            a.recreate();
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.mute);
            openAdjustments(a);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Reset penyesuaian klip"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
            assertTrue("Reset is staged too",TimelineDraftStore.load(ctx).clips.get(0).adjustments.mute);
            tapDialog(android.R.id.button1,"Terapkan penyesuaian klip");
            assertTrue(TimelineDraftStore.load(ctx).clips.get(0).adjustments.same(ClipAdjustments.NEUTRAL));
            assertArrayEquals(original,hashSource(source));
            android.util.Log.i("WfsTimelinePreviewQA","adjustmentPanel=cancel_apply_undo_redo_recreate_reset_verified");
        }finally{TimelineDraftStore.clear(ctx);}
    }
    private void openAdjustments(ActivityScenario<StudioMergeActivity> a){
        // Use a real click after revealing the control inside the editor ScrollView.
        tapTimelineControl("Color Look • Adjust • Audio");
    }
    private static void tapTimelineControl(String label){
        androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
                androidx.test.espresso.matcher.ViewMatchers.withText(label),
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.Button.class)))
                .perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
    }
    private static void tapAdjustmentMute(boolean expectedBefore){
        androidx.test.espresso.ViewInteraction mute=androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Bisukan audio klip"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog());
        mute.perform(androidx.test.espresso.action.ViewActions.scrollTo());
        mute.check(androidx.test.espresso.assertion.ViewAssertions.matches(expectedBefore?
                androidx.test.espresso.matcher.ViewMatchers.isChecked():
                androidx.test.espresso.matcher.ViewMatchers.isNotChecked()));
        mute.perform(androidx.test.espresso.action.ViewActions.click());
        mute.check(androidx.test.espresso.assertion.ViewAssertions.matches(expectedBefore?
                androidx.test.espresso.matcher.ViewMatchers.isNotChecked():
                androidx.test.espresso.matcher.ViewMatchers.isChecked()));
    }
    private static void tapDialog(int id,String description){
        // Real dialog button click, independent of displayed capitalization or locale.
        androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
                androidx.test.espresso.matcher.ViewMatchers.withId(id),
                androidx.test.espresso.matcher.ViewMatchers.withContentDescription(description)))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                .perform(androidx.test.espresso.action.ViewActions.click());
    }

    private File renderAdjusted(TimelineProject draft,boolean privatePreview)throws Exception{
        return renderAdjusted(draft,privatePreview,125,null);
    }
    private File renderAdjusted(TimelineProject draft,boolean privatePreview,int timeoutSeconds,long[] sampledPss)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> result=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        VideoMergeEngine worker=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            VideoMergeEngine.Listener listener=new VideoMergeEngine.Listener(){
                public void onCompleted(File f){worker.transferCompletedFile(f);result.set(f);done.countDown();}
                public void onFailure(String reason){error.set(reason);done.countDown();}
            };
            try{if(privatePreview)worker.startTimelinePreview(ctx,draft,listener);else worker.startTimeline(ctx,draft,listener);}
            catch(RuntimeException ex){error.set(ex.toString());done.countDown();}
        });
        try{
            long deadline=android.os.SystemClock.elapsedRealtime()+timeoutSeconds*1000L;
            boolean finished;
            if(sampledPss==null)finished=done.await(timeoutSeconds,TimeUnit.SECONDS);
            else{
                while(!(finished=done.await(500,TimeUnit.MILLISECONDS))&&android.os.SystemClock.elapsedRealtime()<deadline)
                    sampledPss[0]=Math.max(sampledPss[0],android.os.Debug.getPss());
                sampledPss[0]=Math.max(sampledPss[0],android.os.Debug.getPss());
            }
            assertTrue("Adjusted render timed out",finished);assertNull(error.get());assertNotNull(result.get());return result.get();
        }
        finally{inst.runOnMainSync(worker::cancel);}
    }

    private java.util.Set<Long> storyBoostGalleryRows(){
        java.util.Set<Long> ids=new java.util.HashSet<>();
        Uri collection=MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        try(android.database.Cursor rows=ctx.getContentResolver().query(collection,
                new String[]{MediaStore.Video.Media._ID},
                MediaStore.Video.Media.RELATIVE_PATH+"=?",new String[]{"Movies/WFS StoryBoost/"},null)){
            assertNotNull(rows);
            while(rows.moveToNext())ids.add(rows.getLong(0));
        }
        return ids;
    }

    private java.util.Set<File> previewFiles(){
        File[] files=ctx.getCacheDir().listFiles((dir,name)->name.startsWith(VideoMergeEngine.PREVIEW_PREFIX));
        assertNotNull(files);
        return new java.util.HashSet<>(java.util.Arrays.asList(files));
    }
    private File onlyNewPreview(java.util.Set<File> before){
        java.util.Set<File> now=previewFiles();now.removeAll(before);
        assertEquals("A single private render belongs to this preview",1,now.size());
        return now.iterator().next();
    }
    private static android.widget.Button findButton(android.view.View view,String text){
        if(view instanceof android.widget.Button&&text.contentEquals(((android.widget.Button)view).getText()))
            return (android.widget.Button)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup parent=(android.view.ViewGroup)view;
            for(int i=0;i<parent.getChildCount();i++){
                android.widget.Button match=findButton(parent.getChildAt(i),text);
                if(match!=null)return match;
            }
        }
        return null;
    }

    private void assertHdrCaptionExport(String transfer) throws Exception {
        Uri uri=importFixture("qa-caption-"+transfer+".mp4");
        MediaProbe.Info input=MediaProbe.inspect(ctx,uri);
        assertHdrFixture(input,transfer);
        String decoder=findMain10Decoder(transfer);
        boolean yuvTarget=hasGlYuvTarget();
        hdrLog("input="+transfer+" decoder="+decoder+" yuvTarget="+yuvTarget+
                " standard="+input.colorStandard+" transfer="+input.colorTransfer+
                " status="+(decoder==null||!yuvTarget?"CAPABILITY_SKIP":"READY"));
        Assume.assumeTrue("No HEVC Main10 decoder",decoder!=null);
        Assume.assumeTrue("No OpenGL ES 3 EXT_YUV_target for sampling HDR input",yuvTarget);
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                AspectRatioProfile.FIT,false).withCustomSize(432,720).withCaption(
                "HDR SHARING PREVIEW AND ENCODED SAFE AREA SHOULD MATCH",0xffffd786);
        assertWrappedExport(uri,options,432,720,true,transfer);
    }

    private static void hdrLog(String message){
        String run=InstrumentationRegistry.getArguments().getString("wfs.hdr.run","ci");
        android.util.Log.i("WfsBeta14HdrQA","run="+run+" "+message);
    }

    private static void assertHdrFixture(MediaProbe.Info info,String transfer){
        assertTrue(info.video&&info.audio&&info.isHdr());
        assertEquals(MediaFormat.COLOR_STANDARD_BT2020,info.colorStandard);
        assertEquals("arib-std-b67".equals(transfer)?MediaFormat.COLOR_TRANSFER_HLG:
                MediaFormat.COLOR_TRANSFER_ST2084,info.colorTransfer);
    }

    private static String findMain10Decoder(String transfer){
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC,320,544);
        MediaCodecList codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        int main10=MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;
        int[] profiles="smpte2084".equals(transfer)?new int[]{
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,main10}:new int[]{main10};
        for(int profile:profiles){
            format.setInteger(MediaFormat.KEY_PROFILE,profile);
            String name=codecs.findDecoderForFormat(format);
            if(name!=null)return name;
        }
        return null;
    }

    private static boolean hasGlYuvTarget(){
        var display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY||!EGL14.eglInitialize(display,new int[1],0,new int[1],0))return false;
        var context=EGL14.EGL_NO_CONTEXT;
        var surface=EGL14.EGL_NO_SURFACE;
        try{
            var configs=new android.opengl.EGLConfig[1];
            int[] count={0};
            int es3Bit=0x40; // EGL_OPENGL_ES3_BIT_KHR
            if(!EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,es3Bit,
                    EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE},0,
                    configs,0,1,count,0)||count[0]==0)return false;
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,1,
                    EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT||surface==EGL14.EGL_NO_SURFACE||
                    !EGL14.eglMakeCurrent(display,surface,surface,context))return false;
            String extensions=GLES30.glGetString(GLES30.GL_EXTENSIONS);
            return extensions!=null&&(" "+extensions+" ").contains(" GL_EXT_YUV_target ");
        }finally{
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            // EGL's default display is shared with Android UI and Media3.
            EGL14.eglReleaseThread();
        }
    }

    @Test(timeout=120000)
    public void captionFreeFrameStaysUnmarkedAndOriginalRemainsEligibleForPassthrough() throws Exception {
        Uri original=importFixture("qa-caption.mp4");
        byte[] before=hashSource(original);
        ExportOptions copy=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                AspectRatioProfile.FIT,true,0,0).withQuality(OutputProfile.MAX_DETAIL);
        assertTrue(VideoPassthrough.allowed(copy,"video/mp4"));
        assertTrue(VideoPassthrough.allowed(copy.withCaption(" \u00a0 \t",0xffffd786),"video/mp4"));
        assertFalse(VideoPassthrough.allowed(copy.withCaption("must be rendered",0xffffd786),"video/mp4"));
        File encoded=realExport(original,copy.withCustomSize(432,1008).withQuality(OutputProfile.HD));
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try{
            reader.setDataSource(encoded.getAbsolutePath());
            android.graphics.Bitmap frame=reader.getFrameAtTime(500000L,MediaMetadataRetriever.OPTION_CLOSEST);
            assertNotNull(frame);
            try{
                assertEquals(432,frame.getWidth());
                assertEquals(1008,frame.getHeight());
                assertTrue("Caption-free monochrome video contains caption pixels",scanCaption(frame).bounds.isEmpty());
            }finally{frame.recycle();}
            assertArrayEquals(before,hashSource(original));
        }finally{reader.release();inst.runOnMainSync(engine::cleanup);}
    }

    private void assertWrappedExport(Uri source,ExportOptions options,int w,int h,boolean audio) throws Exception {
        assertWrappedExport(source,options,w,h,audio,null);
    }

    private void assertWrappedExport(Uri source,ExportOptions options,int w,int h,boolean audio,String hdr) throws Exception {
        assertWrappedExport(source,options,w,h,audio,hdr,false);
    }

    private void assertWrappedExport(Uri source,ExportOptions options,int w,int h,boolean audio,String hdr,
                                     boolean separatedMarks) throws Exception {
        byte[] original=hashSource(source);
        File encoded=realExport(source,options);
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        String hdrEvidence="";
        try{
            assertTrue(containsCodec(encoded,"video/avc"));
            if(hdr!=null){
                MediaProbe.Info converted=MediaProbe.inspect(encoded);
                assertTrue("Tone-mapped output must explicitly signal SDR, got transfer="+converted.colorTransfer,
                        converted.isSdr());
                assertFalse(converted.isHdr());
                hdrEvidence="input="+hdr+" output=avc-sdr mode=Media3-OpenGL standard="+
                        converted.colorStandard+" transfer="+converted.colorTransfer;
            }
            assertEquals("Wrong AAC presence",audio,containsCodec(encoded,"audio/mp4a-latm"));
            MediaProbe.SampleTimeline pts=MediaProbe.inspectSampleTimeline(encoded);
            assertTrue(pts.videoSamples>10);
            assertEquals("Wrong audio sample presence",audio,pts.audioSamples>0);
            if(audio)assertTrue(pts.lastPtsDriftMs()>=0&&pts.lastPtsDriftMs()<=250);
            reader.setDataSource(encoded.getAbsolutePath());
            android.graphics.Bitmap frame=reader.getFrameAtTime(500000L,MediaMetadataRetriever.OPTION_CLOSEST);
            assertNotNull(frame);
            try{
                assertEquals(w,frame.getWidth());assertEquals(h,frame.getHeight());
                if(hdr!=null){
                    int low=(frame.getPixel(w/6,h/3)>>16)&255;
                    int high=(frame.getPixel(w*5/6,h/3)>>16)&255;
                    assertTrue("Tone-mapped highlight ramp collapsed: "+low+"/"+high,high-low>18);
                    hdrEvidence+=" gradient="+low+","+high;
                }
                // Arabic marks and accent dots may produce ink rows separate
                // from their line's body. Compare the two actual line regions
                // with preview instead of counting every detached mark as a line.
                int lineSplit=-1;
                if(separatedMarks){
                    assertEquals(2,new CaptionRenderer().layoutLines(options.caption,w,h).length);
                    float fontPx=Math.min(w*.058f,h*.04f);
                    lineSplit=Math.round(h*SocialExportProfile.captionCenterY(options.socialProfile)-fontPx*.45f);
                }
                CaptionPixels pixels=scanCaption(frame,lineSplit);
                android.graphics.Rect actual=pixels.bounds;
                CaptionPixels preview=drawPreviewPixels(w,h,options.caption,options.captionColor,
                        options.socialProfile,lineSplit);
                android.graphics.Rect reference=preview.bounds;
                if(separatedMarks){
                    assertTrue("Missing first or second line ink",pixels.topLine.width()>w*.20f&&pixels.bottomLine.width()>w*.20f);
                    assertBoundsMatch(preview.topLine,pixels.topLine,w,h);
                    assertBoundsMatch(preview.bottomLine,pixels.bottomLine,w,h);
                    android.util.Log.i("WfsBeta13CaptionQA","previewFirst="+preview.topLine+" decodedFirst="+
                            pixels.topLine+" previewSecond="+preview.bottomLine+" decodedSecond="+pixels.bottomLine);
                }else assertEquals("Expected two distinct rendered lines",2,pixels.bands);
                assertBoundsMatch(reference,actual,w,h);
                SocialExportProfile.Profile zone=SocialExportProfile.get(options.socialProfile);
                assertTrue("Caption outside vertical safe zone "+actual,
                        actual.top>=h*zone.safeTop&&actual.bottom<=h*(1f-zone.safeBottom));
                assertTrue("Caption outside horizontal safe margins "+actual,
                        actual.left>=w*.035f&&actual.right<=w*.965f);
                if(hdr!=null)hdrEvidence+=" decoded="+actual.flattenToString().replace(' ',',')+
                        " preview="+reference.flattenToString().replace(' ',',')+
                        " videoSamples="+pts.videoSamples+" audioSamples="+pts.audioSamples+
                        " driftMs="+pts.lastPtsDriftMs();
                android.util.Log.i("WfsBeta12CaptionQA","profile="+options.socialProfile+" canvas="+w+"x"+h+
                        " bands="+pixels.bands+" audioSamples="+pts.audioSamples+" driftMs="+pts.lastPtsDriftMs()+
                        " preview="+reference+" encoded="+actual);
            }finally{frame.recycle();}
            assertArrayEquals("Source video changed",original,hashSource(source));
            if(hdr!=null){
                StringBuilder sha=new StringBuilder();
                for(byte value:original)sha.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                hdrLog(hdrEvidence+" sha256="+sha+" status=PASS");
            }
        }finally{reader.release();inst.runOnMainSync(engine::cleanup);}
    }

    private android.graphics.Rect drawPreviewCaption(int w,int h,String text,int color,int profile) {
        return drawPreviewPixels(w,h,text,color,profile,-1).bounds;
    }

    private CaptionPixels drawPreviewPixels(int w,int h,String text,int color,int profile,int splitAt) {
        AtomicReference<CaptionPixels> result=new AtomicReference<>();
        inst.runOnMainSync(()->{
            CaptionPreviewView preview=new CaptionPreviewView(ctx);
            preview.update(text,color,profile,false,true);
            preview.layout(0,0,w,h);
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,h,
                    android.graphics.Bitmap.Config.ARGB_8888);
            try{
                preview.draw(new android.graphics.Canvas(bitmap));
                result.set(scanCaption(bitmap,splitAt));
            }finally{bitmap.recycle();}
        });
        CaptionPixels pixels=result.get();
        assertFalse("No preview caption pixels found",pixels.bounds.isEmpty());
        return pixels;
    }

    private static void assertBoundsMatch(android.graphics.Rect reference,android.graphics.Rect actual,int w,int h){
        assertFalse("No encoded caption pixels found",actual.isEmpty());
        assertEquals("Caption left",reference.left/(float)w,actual.left/(float)w,.025f);
        assertEquals("Caption right",reference.right/(float)w,actual.right/(float)w,.025f);
        assertEquals("Caption top",reference.top/(float)h,actual.top/(float)h,.02f);
        assertEquals("Caption bottom",reference.bottom/(float)h,actual.bottom/(float)h,.02f);
    }

    private static android.graphics.Rect captionPixels(android.graphics.Bitmap bitmap) {
        android.graphics.Rect bounds=scanCaption(bitmap).bounds;
        assertFalse("No caption pixels found",bounds.isEmpty());
        return bounds;
    }

    private static final class CaptionPixels {
        final android.graphics.Rect bounds=new android.graphics.Rect();
        final android.graphics.Rect topLine=new android.graphics.Rect(),bottomLine=new android.graphics.Rect();
        int bands;
    }

    private static CaptionPixels scanCaption(android.graphics.Bitmap bitmap) {
        return scanCaption(bitmap,-1);
    }

    private static CaptionPixels scanCaption(android.graphics.Bitmap bitmap,int splitAt) {
        CaptionPixels pixels=new CaptionPixels();
        android.graphics.Rect result=pixels.bounds;
        int previousRow=-bitmap.getHeight();
        int minimumGap=Math.max(2,bitmap.getHeight()/200);
        int[] row=new int[bitmap.getWidth()];
        for(int y=0;y<bitmap.getHeight();y++){
            bitmap.getPixels(row,0,row.length,0,y,row.length,1);
            int hits=0;
            for(int x=0;x<row.length;x++){
                int pixel=row[x],r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
                if(r>165&&g>125&&r-b>58&&g-b>34){
                    hits++;
                    if(result.isEmpty())result.set(x,y,x+1,y+1);
                    else result.union(x,y,x+1,y+1);
                    if(splitAt>=0){
                        android.graphics.Rect line=y<splitAt?pixels.topLine:pixels.bottomLine;
                        if(line.isEmpty())line.set(x,y,x+1,y+1);
                        else line.union(x,y,x+1,y+1);
                    }
                }
            }
            if(hits>=2){
                if(y-previousRow>minimumGap)pixels.bands++;
                previousRow=y;
            }
        }
        return pixels;
    }

    private byte[] hashSource(Uri source) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream in=ctx.getContentResolver().openInputStream(source)){
            assertNotNull(in);
            byte[] bytes=new byte[8192];
            for(int n;(n=in.read(bytes))!=-1;)digest.update(bytes,0,n);
        }
        return digest.digest();
    }

    @Test(timeout=350000)
    @SdkSuppress(minSdkVersion=35)
    public void splitExportProducesTwoIndependentTimedH264AacSegments() throws Exception {
        ExportOptions first=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,0,1000)
                .withQuality(OutputProfile.FULL_HD);
        ExportOptions second=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,1000,2000)
                .withQuality(OutputProfile.FULL_HD);
        File p1=realExport(av,first);
        File p2=realExport(av,second);
        for(File f:new File[]{p1,p2}){
            MediaProbe.Info info=MediaProbe.inspect(f);
            assertTrue(info.video);
            assertTrue(info.audio);
            assertTrue(containsCodec(f,"video/avc"));
            assertTrue(containsCodec(f,"audio/mp4a-latm"));
            assertTrue("Split output expected ~1000ms, actual="+info.durationMs,
                    Math.abs(info.durationMs-1000)<420);
        }
        assertNotEquals("Two split exports must be distinct files",
                p1.getAbsolutePath(),p2.getAbsolutePath());
    }

    @Test(timeout=240000)
    @SdkSuppress(minSdkVersion=35)
    public void media3MergeConcatenatesTwoClipsIntoOneMp4() throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> produced=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        mergeEngine=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            try{
                mergeEngine.start(ctx,java.util.Arrays.asList(av,av),
                        OutputProfile.FULL_HD,new VideoMergeEngine.Listener(){
                    @Override public void onCompleted(File file){
                        produced.set(file);done.countDown();
                    }
                    @Override public void onFailure(String message){
                        error.set(message);done.countDown();
                    }
                });
            }catch(Exception ex){error.set(ex.toString());done.countDown();}
        });
        assertTrue("Media3 merge did not finish",done.await(175,TimeUnit.SECONDS));
        assertNull("Merge failed: "+error.get(),error.get());
        File merged=produced.get();
        assertNotNull(merged);
        outputFiles.add(merged);
        MediaProbe.Info info=MediaProbe.inspect(merged);
        assertTrue(info.video);assertTrue(info.audio);
        assertTrue(containsCodec(merged,"video/avc"));
        assertTrue(containsCodec(merged,"audio/mp4a-latm"));
        assertTrue("Two 2-second clips should produce ~4s, got "+info.durationMs,
                Math.abs(info.durationMs-4000)<750);
        assertTrue("Merged A/V drift too large",
                Math.abs(info.audioDurationMs-info.videoDurationMs)<450);
    }

    @Test(timeout=350000)
    @SdkSuppress(minSdkVersion=35)
    public void silentAndRotatedSourcesExportWithoutInventingAudio() throws Exception {
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,0,1800)
                .withQuality(OutputProfile.FULL_HD);
        File noAudio=realExport(silent,options);
        MediaProbe.Info output=MediaProbe.inspect(noAudio);
        assertTrue(output.video);
        assertFalse("Silent fixture must stay silent",output.audio);
        assertTrue(containsCodec(noAudio,"video/avc"));
        File upright=realExport(rotated,options);
        MediaProbe.Info rotatedOutput=MediaProbe.inspect(upright);
        assertTrue(rotatedOutput.video);
        assertTrue(rotatedOutput.audio);
        android.media.MediaMetadataRetriever md=new android.media.MediaMetadataRetriever();
        try{
            md.setDataSource(upright.getAbsolutePath());
            int width=Integer.parseInt(md.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height=Integer.parseInt(md.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String angle=md.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if(angle!=null&&Integer.parseInt(angle)%180!=0){
                int swap=width;width=height;height=swap;
            }
            assertEquals(1080,width);
            assertEquals(1920,height);
        }finally{md.release();}
    }

    @Test(timeout=245000)
    @SdkSuppress(minSdkVersion=35)
    public void mixedSoundAndSilentClipMergeDoesNotLoseVideoFrames() throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> produced=new AtomicReference<>();
        AtomicReference<String> failure=new AtomicReference<>();
        mergeEngine=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            try{
                mergeEngine.start(ctx,java.util.Arrays.asList(av,silent),
                        OutputProfile.FULL_HD,new VideoMergeEngine.Listener(){
                    @Override public void onCompleted(File file){
                        produced.set(file);done.countDown();
                    }
                    @Override public void onFailure(String msg){
                        failure.set(msg);done.countDown();
                    }
                });
            }catch(Exception e){failure.set(e.toString());done.countDown();}
        });
        assertTrue("Mixed audio/silent merge timeout",done.await(185,TimeUnit.SECONDS));
        assertNull("Mixed clip merge failure: "+failure.get(),failure.get());
        File merged=produced.get();
        assertNotNull("Merge callback returned no MP4",merged);
        outputFiles.add(merged);
        assertTrue("Merged output empty",merged.length()>1024);
        MediaProbe.Info info=MediaProbe.inspect(merged);
        assertTrue("Mixed composition must retain video",info.video);
        assertTrue("Mixed composition must retain first clip audio",info.audio);
        MediaProbe.SampleTimeline timeline=MediaProbe.inspectSampleTimeline(merged);
        assertTrue("Mixed merge has no encoded video samples",timeline.videoSamples>40);
        assertTrue("Mixed merge AAC samples missing",timeline.audioSamples>30);
        assertTrue("Mixed-order audio samples finish more than 250ms from video",
                timeline.lastPtsDriftMs()<=250);
        assertTrue("No silent leg should truncate the second clip: "+info.durationMs,
                Math.abs(info.durationMs-4000)<850);
        assertTrue(containsCodec(merged,"video/avc"));
        assertTrue(containsCodec(merged,"audio/mp4a-latm"));
        assertTrue("A/V track should cover sequence within 500ms: "+
                info.audioDurationMs+"/"+info.videoDurationMs,
                Math.abs(info.audioDurationMs-info.videoDurationMs)<500);
    }

    @Test(timeout=260000)
    @SdkSuppress(minSdkVersion=35)
    public void reverseMixedMergePadsSilentFirstClipAndKeepsRealSamplePts()throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> produced=new AtomicReference<>();
        AtomicReference<String> failure=new AtomicReference<>();
        mergeEngine=new VideoMergeEngine();
        inst.runOnMainSync(()->{
            try{
                mergeEngine.start(ctx,java.util.Arrays.asList(silent,av),
                        OutputProfile.FULL_HD,new VideoMergeEngine.Listener(){
                    @Override public void onCompleted(File file){
                        produced.set(file);done.countDown();
                    }
                    @Override public void onFailure(String msg){
                        failure.set(msg);done.countDown();
                    }
                });
            }catch(Exception e){failure.set(e.toString());done.countDown();}
        });
        assertTrue("Reverse mixed merge timeout",done.await(190,TimeUnit.SECONDS));
        assertNull("Reverse mixed merge error "+failure.get(),failure.get());
        File output=produced.get();
        assertNotNull(output);
        outputFiles.add(output);
        MediaProbe.Info tracks=MediaProbe.inspect(output);
        assertTrue(tracks.video);
        assertTrue("Second clip AAC must be retained",tracks.audio);
        assertTrue(containsCodec(output,"video/avc"));
        assertTrue(containsCodec(output,"audio/mp4a-latm"));
        assertTrue("Four-second video sequence was truncated: "+tracks.durationMs,
                Math.abs(tracks.durationMs-4000)<850);
        MediaProbe.SampleTimeline actual=MediaProbe.inspectSampleTimeline(output);
        assertTrue("Expected actual encoded video frames",actual.videoSamples>=40);
        assertTrue("Expected actual encoded AAC samples",actual.audioSamples>=30);
        assertTrue("Video presentation timestamps do not reach end of composition: "+actual.lastVideoUs,
                actual.lastVideoUs>=3_400_000);
        assertTrue("Audio presentation timestamps do not reach end of composition: "+actual.lastAudioUs,
                actual.lastAudioUs>=3_300_000);
        assertTrue("Real sample PTS drift exceeds 250ms, track metadata alone is insufficient: "+
                        actual.lastPtsDriftMs()+"ms",actual.lastPtsDriftMs()<=250);
        android.util.Log.i("WfsMediaPtsQA","reverseMixed videoSamples="+actual.videoSamples+
                " audioSamples="+actual.audioSamples+
                " videoLastUs="+actual.lastVideoUs+" audioLastUs="+actual.lastAudioUs+
                " driftMs="+actual.lastPtsDriftMs());
    }

    @Test(timeout=160000)
    @SdkSuppress(minSdkVersion=35)
    public void cancelAfterRealMedia3ProgressPreservesOriginalAndCache() throws Exception {
        cancelAtActualProgress(1);
    }

    @Test(timeout=160000) @SdkSuppress(minSdkVersion=35)
    public void cancelLateInRealMedia3EncodePreservesOriginalAndCache() throws Exception {
        cancelAtActualProgress(85);
    }

    private void cancelAtActualProgress(int minimumProgress) throws Exception {
        // A longer RIGHTS-SAFE actual H264/AAC MP4 forces real async encoding.
        Uri extended=importFixture("qa-h264-extended.mp4");
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        byte[] beforeHash;
        try(InputStream in=ctx.getContentResolver().openInputStream(extended)){
            assertNotNull(in);
            byte[] buffer=new byte[32768];
            for(int n;(n=in.read(buffer))>=0;){
                if(n>0)digest.update(buffer,0,n);
            }
            beforeHash=digest.digest();
        }
        String[] initial=ctx.getCacheDir().list((dir,name)->
                name.startsWith("storyboost_")&&name.endsWith(".mp4"));
        java.util.Set<String> previous=new java.util.HashSet<>(java.util.Arrays.asList(
                initial==null?new String[0]:initial));
        java.util.concurrent.atomic.AtomicInteger callbacks=
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger encodedProgress=
                new java.util.concurrent.atomic.AtomicInteger(-1);
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,0,0)
                .withQuality(OutputProfile.FULL_HD);
        engine=new VideoEngine();
        inst.runOnMainSync(()->engine.start(ctx,extended,options,new VideoEngine.Listener(){
            @Override public void onSuccess(File file,AspectRatioProfile.Size dimensions){
                callbacks.incrementAndGet();
                outputFiles.add(file);
            }
            @Override public void onError(String error){callbacks.incrementAndGet();}
        }));
        long deadline=android.os.SystemClock.uptimeMillis()+65000L;
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        while(android.os.SystemClock.uptimeMillis()<deadline&&callbacks.get()==0&&!cancelled.get()){
            inst.runOnMainSync(()->{
                int pct=engine.progress();
                if(pct>=minimumProgress&&pct<100&&callbacks.get()==0){
                    encodedProgress.set(pct);
                    engine.cancel();
                    cancelled.set(true);
                }
            });
            if(!cancelled.get())android.os.SystemClock.sleep(40);
        }
        assertTrue("No in-flight cancellation at "+minimumProgress+"% was proven",cancelled.get());
        android.os.SystemClock.sleep(1600);
        assertEquals("Cancelled export emitted a success/error callback",0,callbacks.get());
        String[] after=ctx.getCacheDir().list((dir,name)->
                name.startsWith("storyboost_")&&name.endsWith(".mp4"));
        for(String file:after==null?new String[0]:after)
            assertTrue("In-progress cancellation leaked partial encoded MP4 "+file,
                    previous.contains(file));
        digest.reset();
        try(InputStream in=ctx.getContentResolver().openInputStream(extended)){
            assertNotNull(in);
            byte[] buffer=new byte[32768];
            for(int n;(n=in.read(buffer))>=0;){
                if(n>0)digest.update(buffer,0,n);
            }
        }
        assertArrayEquals("Cancellation mutated original MediaStore fixture",
                beforeHash,digest.digest());
        android.util.Log.i("WfsMediaCancelQA","PASS delayed Media3 cancellation at progress="+
                encodedProgress.get()+"%, threshold="+minimumProgress+"%, no callback/cache leak, unchanged source bytes");
    }

    @Test(timeout=50000)
    @SdkSuppress(minSdkVersion=35)
    public void cancellationOfRunningEncoderDeletesItsTemporaryOutput() throws Exception {
        String[] before=ctx.getCacheDir().list((folder,name)->name.startsWith("storyboost_")
                &&name.endsWith(".mp4"));
        final java.util.concurrent.atomic.AtomicInteger callbacks=
                new java.util.concurrent.atomic.AtomicInteger();
        engine=new VideoEngine();
        ExportOptions options=new ExportOptions(ColorLooks.ORIGINAL,
                AspectRatioProfile.PORTRAIT_9_16,AspectRatioProfile.FIT,true,0,2000)
                .withQuality(OutputProfile.FULL_HD);
        inst.runOnMainSync(()->engine.start(ctx,av,options,new VideoEngine.Listener(){
            @Override public void onSuccess(File file,AspectRatioProfile.Size size){
                callbacks.incrementAndGet();outputFiles.add(file);
            }
            @Override public void onError(String message){callbacks.incrementAndGet();}
        }));
        // start() has returned after Media3 begins the asynchronous transform.
        inst.runOnMainSync(engine::cancel);
        android.os.SystemClock.sleep(1100);
        assertEquals("Cancellation must not report successful export",0,callbacks.get());
        String[] after=ctx.getCacheDir().list((folder,name)->name.startsWith("storyboost_")
                &&name.endsWith(".mp4"));
        java.util.Set<String> prior=new java.util.HashSet<>(java.util.Arrays.asList(
                before==null?new String[0]:before));
        for(String name:after==null?new String[0]:after)
            assertTrue("Cancel leaked cache MP4: "+name,prior.contains(name));
    }
}
