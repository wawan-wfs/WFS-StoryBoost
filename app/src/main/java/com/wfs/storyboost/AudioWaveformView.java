package com.wfs.storyboost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.net.Uri;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.AudioFormat;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Real PCM peak overview of the first 15 seconds of a trimmed audio selection.
 * No disk cache, bounded 80 bins, cancels on replacement or lifecycle exit.
 */
final class AudioWaveformView extends View {
    static final int BINS=80;
    static final long MAX_MS=15000;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;
    private TimelineProject.Audio current;
    private float[] peaks;
    private String state="Tidak ada audio";
    private int generation;
    private boolean closed;
    AudioWaveformView(Context context){super(context);setContentDescription(state);}
    void bind(TimelineProject.Audio audio){
        if(closed||audio==current)return;
        current=audio;int token=++generation;
        if(task!=null)task.cancel(true);
        peaks=null;state=audio==null?"Tidak ada audio":"Membaca PCM…";setContentDescription(state);invalidate();
        if(audio==null)return;
        task=worker.submit(()->{
            float[] result=null;
            try{result=decode(getContext().getApplicationContext(),audio);}catch(Exception|OutOfMemoryError ignored){}
            float[] decoded=result;
            post(()->{if(closed||token!=generation)return;peaks=decoded;
                state=decoded==null?"Waveform tidak tersedia":audio.length()>MAX_MS?"PCM 15 detik pertama":"Waveform PCM";
                setContentDescription(state);invalidate();});
        });
    }
    void close(){closed=true;++generation;if(task!=null)task.cancel(true);worker.shutdownNow();peaks=null;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);paint.setColor(StudioUi.INNER);canvas.drawRoundRect(0,0,getWidth(),getHeight(),8,8,paint);
        if(peaks==null){paint.setColor(StudioUi.MUTED);paint.setTextSize(StudioUi.dp(getContext(),11));canvas.drawText(state,8,getHeight()*.65f,paint);return;}
        float width=getWidth()/(float)BINS,center=getHeight()/2f;
        paint.setColor(StudioUi.GREEN);paint.setStrokeWidth(Math.max(1,width*.65f));
        for(int i=0;i<BINS;i++){float h=peaks[i]*center*.9f;canvas.drawLine((i+.5f)*width,center-h,(i+.5f)*width,center+h,paint);}
    }
    static float[] decode(Context context,TimelineProject.Audio audio)throws Exception{
        Uri uri=Uri.parse(audio.uri);SourceAccess.requireReadable(context,uri);
        long start=audio.in*1000L,end=Math.min(audio.out,audio.in+MAX_MS)*1000L;
        MediaExtractor ex=new MediaExtractor();MediaCodec codec=null;
        try{
            ex.setDataSource(context,uri,null);MediaFormat format=null;
            for(int i=0;i<ex.getTrackCount();i++){
                MediaFormat f=ex.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){format=f;ex.selectTrack(i);break;}
            }
            if(format==null)throw new java.io.IOException("No audio");
            ex.seekTo(start,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            codec.configure(format,null,null,0);codec.start();
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean fed=false,done=false;long samples=0;float[] bins=new float[BINS];
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=SystemClock.elapsedRealtime()+12000;
            while(!done&&!Thread.currentThread().isInterrupted()&&SystemClock.elapsedRealtime()<deadline){
                if(!fed){
                    int slot=codec.dequeueInputBuffer(5000);
                    if(slot>=0){ByteBuffer input=codec.getInputBuffer(slot);input.clear();
                        long pts=ex.getSampleTime();int bytes=pts<0||pts>=end+100000?-1:ex.readSampleData(input,0);
                        if(bytes<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);fed=true;}
                        else {codec.queueInputBuffer(slot,0,bytes,pts,0);ex.advance();}
                    }
                }
                int slot=codec.dequeueOutputBuffer(info,5000);
                if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(f.getInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)!=AudioFormat.ENCODING_PCM_16BIT)
                        throw new java.io.IOException("PCM16 required");
                }else if(slot>=0){
                    ByteBuffer pcm=codec.getOutputBuffer(slot);
                    if(pcm!=null&&info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        pcm.order(ByteOrder.LITTLE_ENDIAN);pcm.position(info.offset);pcm.limit(info.offset+info.size);
                        int n=0;
                        while(pcm.remaining()>=2){
                            float sample=Math.abs(pcm.getShort()/32768f);
                            long t=info.presentationTimeUs+(n++/channels)*1000000L/rate;
                            if(t>=start&&t<end){int bin=(int)((t-start)*BINS/(end-start));bins[bin]=Math.max(bins[bin],sample);samples++;}
                        }
                    }
                    done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(slot,false);
                }
            }
            if(!done||samples==0||Thread.currentThread().isInterrupted())throw new java.io.IOException("Incomplete waveform");
            SourceAccess.requireReadable(context,uri);return bins;
        }finally{try{if(codec!=null)codec.release();}finally{ex.release();}}
    }
}
