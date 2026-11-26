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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

/** Real PCM peak overview across the selected audio range, aligned to project duration.
 * Full decode up to two minutes; longer clips use explicitly labelled distributed samples.
 * No disk cache, bounded 80 bins, cancels on replacement or lifecycle exit.
 */
final class AudioWaveformView extends View {
    static final int BINS=80;
    static final long MAX_MS=120000;
    static final int SAMPLE_WINDOWS=8;
    private long timelineDuration;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private Future<?> task;
    private TimelineProject.Audio current;
    private float[] peaks;
    private String state="Tidak ada audio";
    private int generation;
    private boolean closed;
    AudioWaveformView(Context context){super(context);setContentDescription(state);}
    void bind(TimelineProject.Audio audio,long total){
        timelineDuration=total;invalidate();
        if(closed)return;
        if(audio==current){
            if(audio==null||SourceAccess.canRead(getContext(),Uri.parse(audio.uri)))return;
            ++generation;if(task!=null)task.cancel(true);worker.purge();peaks=null;
            state="Izin audio hilang";setContentDescription(state);invalidate();return;
        }
        current=audio;int token=++generation;
        if(task!=null)task.cancel(true);worker.purge();
        peaks=null;state=audio==null?"Tidak ada audio":"Membaca PCM…";setContentDescription(state);invalidate();
        if(audio==null)return;
        task=worker.submit(()->{
            float[] result=null;
            try{result=decode(getContext().getApplicationContext(),audio);}catch(Exception|OutOfMemoryError ignored){}
            float[] decoded=result;
            post(()->{if(closed||token!=generation)return;peaks=decoded;
                state=decoded==null?"Waveform tidak tersedia":audio.length()>MAX_MS?"PCM sampel sepanjang track":"Waveform PCM lengkap";
                setContentDescription(state);invalidate();});
        });
    }
    void close(){closed=true;++generation;if(task!=null)task.cancel(true);worker.shutdownNow();peaks=null;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);paint.setColor(StudioUi.INNER);canvas.drawRoundRect(0,0,getWidth(),getHeight(),8,8,paint);
        if(peaks==null){paint.setColor(StudioUi.MUTED);paint.setTextSize(StudioUi.dp(getContext(),11));canvas.drawText(state,8,getHeight()*.65f,paint);return;}
        if(current==null||timelineDuration<=0)return;
        float left=getWidth()*current.start/(float)timelineDuration;
        float span=getWidth()*current.length()/(float)timelineDuration;
        float width=span/BINS,center=getHeight()/2f;
        if(current.length()>MAX_MS){paint.setColor(StudioUi.MUTED);paint.setTextSize(StudioUi.dp(getContext(),10));canvas.drawText("Sampel PCM",8,StudioUi.dp(getContext(),11),paint);}
        paint.setColor(StudioUi.GREEN);paint.setStrokeWidth(Math.max(1,width*.65f));
        for(int i=0;i<BINS;i++){float h=peaks[i]*center*.9f;float x=left+(i+.5f)*width;canvas.drawLine(x,center-h,x,center+h,paint);}
    }
    static float[] decode(Context context,TimelineProject.Audio audio)throws Exception{
        if(audio.length()<=MAX_MS)return decodeRange(context,audio.uri,audio.in,audio.out,BINS,30000);
        float[] all=new float[BINS];long deadline=SystemClock.elapsedRealtime()+40000;
        for(int window=0;window<SAMPLE_WINDOWS;window++){
            if(Thread.currentThread().isInterrupted()||SystemClock.elapsedRealtime()>=deadline)throw new java.io.IOException("Waveform sampling cancelled or timed out");
            long lo=audio.in+audio.length()*window/SAMPLE_WINDOWS,hi=audio.in+audio.length()*(window+1)/SAMPLE_WINDOWS;
            long middle=lo+(hi-lo)/2,start=middle-750,end=middle+750;
            float[] part=decodeRange(context,audio.uri,start,end,BINS/SAMPLE_WINDOWS,Math.max(1,deadline-SystemClock.elapsedRealtime()));
            System.arraycopy(part,0,all,window*part.length,part.length);
        }
        return all;
    }
    private static float[] decodeRange(Context context,String source,long from,long to,int binCount,long budget)throws Exception{
        Uri uri=Uri.parse(source);SourceAccess.requireReadable(context,uri);
        long start=from*1000L,end=to*1000L;
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
            if(rate<=0||channels<=0)throw new java.io.IOException("Invalid PCM format");
            boolean fed=false,done=false;long samples=0;float[] bins=new float[binCount];
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=SystemClock.elapsedRealtime()+budget;
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
                    if(rate<=0||channels<=0)throw new java.io.IOException("Invalid PCM format");
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
                            if(t>=start&&t<end){int bin=(int)((t-start)*binCount/(end-start));bins[bin]=Math.max(bins[bin],sample);samples++;}
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
