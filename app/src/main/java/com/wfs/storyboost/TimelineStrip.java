package com.wfs.storyboost;

import android.content.Context;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import android.widget.SeekBar;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.ArrayList;

/** Native, accessible sequence overview. Width is bounded for very short/long clips. */
final class TimelineStrip extends LinearLayout {
    private final LinearLayout track;
    private final TextView status;
    private final HorizontalScrollView scroll;
    private final IntConsumer select;
    private String selectionKey="";
    private final SeekBar scrub;
    private final TimelineOverviewView overview;
    private final TextView audioLane,voiceLane;
    private final AudioWaveformView audioWave,voiceWave;
    private final java.util.function.BiConsumer<Integer,Integer> reorder;
    private final TextView captionLane,pictureLane;
    private TimelineProject current=TimelineProject.empty();
    private int currentSelection=-1,zoom=1;
    private long positionMs;
    private boolean dragging;
    private boolean closed;
    private int thumbnailGeneration;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(2*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final ThreadPoolExecutor loader=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final ArrayList<Future<?>> pending=new ArrayList<>();
    private void cancelThumbnails(){
        thumbnailGeneration++;
        for(Future<?> task:pending)task.cancel(true);
        pending.clear();loader.purge();
    }
    void close(){
        closed=true;cancelThumbnails();loader.shutdownNow();thumbnails.evictAll();audioWave.close();voiceWave.close();
    }
    private void requestThumbnail(TimelineProject.Clip clip,ImageView target){
        if(closed)return;
        final int generation=thumbnailGeneration;
        final Context app=getContext().getApplicationContext();
        final String key=clip.uri+"#"+clip.in+"#"+clip.out;
        pending.add(loader.submit(()->{
            Uri uri=Uri.parse(clip.uri);
            if(Thread.currentThread().isInterrupted()||!SourceAccess.canRead(app,uri))return;
            Bitmap bitmap=thumbnails.get(key);
            if(bitmap==null){
                try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
                    reader.setDataSource(app,uri);
                    bitmap=reader.getScaledFrameAtTime((clip.in+(clip.length()/2))*1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,90);
                    if(bitmap!=null&&!Thread.currentThread().isInterrupted())thumbnails.put(key,bitmap);
                }catch(Exception|OutOfMemoryError unavailable){return;}
            }
            final Bitmap result=bitmap;
            if(result!=null&&!Thread.currentThread().isInterrupted())target.post(()->{
                if(!closed&&generation==thumbnailGeneration&&target.isAttachedToWindow())target.setImageBitmap(result);
            });
        }));
    }
    TimelineStrip(Context context,IntConsumer select,LongConsumer seek,java.util.function.BiConsumer<Integer,Integer> reorder){
        super(context);this.select=select;this.reorder=reorder;setOrientation(VERTICAL);
        setPadding(dp(12),dp(12),dp(12),dp(12));
        setBackground(StudioUi.solid(context,StudioUi.PANEL,16));
        addView(StudioUi.text(context,"TIMELINE  /  VIDEO",11,StudioUi.CYAN,true));
        status=StudioUi.text(context,"00:00.000",12,StudioUi.TEXT,true);
        addView(status);StudioUi.gap(this,context,8);
        scrub=new SeekBar(context);scrub.setMax(10000);scrub.setContentDescription("Geser playhead timeline");
        addView(scrub,new LayoutParams(-1,dp(48)));
        scrub.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                if(user){positionMs=TimelineNavigation.fromProgress(progress,current.duration());showPosition();if(!dragging)seek.accept(positionMs);}
            }
            public void onStopTrackingTouch(SeekBar bar){dragging=false;seek.accept(positionMs);}
        });
        overview=new TimelineOverviewView(context,ms->{positionMs=ms;scrub.setProgress(TimelineNavigation.toProgress(ms,current.duration()));showPosition();seek.accept(ms);});
        addView(overview,new LayoutParams(-1,dp(132)));StudioUi.gap(this,context,8);
        LinearLayout toolbar=StudioUi.row(context);
        toolbar.addView(StudioUi.text(context,"KLIP • LONG PRESS UNTUK URUTKAN",10,StudioUi.MUTED,true),new LayoutParams(0,-2,1));
        Button magnify=StudioUi.button(context,"Zoom 1×",false);
        magnify.setOnClickListener(v->{zoom=zoom==3?1:zoom+1;magnify.setText("Zoom "+zoom+"×");bind(current,currentSelection);});
        toolbar.addView(magnify,new LayoutParams(dp(100),dp(48)));addView(toolbar);
        scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(true);
        track=StudioUi.row(context);scroll.addView(track,new HorizontalScrollView.LayoutParams(-2,-2));
        addView(scroll,new LayoutParams(-1,-2));
        audioLane=StudioUi.text(context,"AUDIO • belum ada soundtrack",12,StudioUi.GREEN,true);
        voiceLane=StudioUi.text(context,"VOICE • belum ada track",12,StudioUi.CYAN,true);
        audioWave=new AudioWaveformView(context);voiceWave=new AudioWaveformView(context);
        pictureLane=StudioUi.text(context,"PIP • belum ada overlay",12,StudioUi.LAVENDER,true);
        captionLane=StudioUi.text(context,"CAPTION • kosong",11,StudioUi.LAVENDER,true);
        StudioUi.gap(this,context,10);addView(audioLane);addView(audioWave,new LayoutParams(-1,dp(38)));
        StudioUi.gap(this,context,8);addView(voiceLane);addView(voiceWave,new LayoutParams(-1,dp(38)));
        StudioUi.gap(this,context,8);addView(pictureLane);
        StudioUi.gap(this,context,8);addView(captionLane);
    }
    private int dp(int n){return StudioUi.dp(getContext(),n);}
    private static String time(long ms){return String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,ms/1000%60,ms%1000);}
    void bind(TimelineProject model,int selected){
        cancelThumbnails();current=model;currentSelection=selected;track.removeAllViews();
        scrub.setEnabled(!model.clips.isEmpty());overview.bind(model,selected);
        audioLane.setText(model.audio==null?"AUDIO • belum ada soundtrack":"AUDIO • "+time(model.audio.start)+" – "+time(model.audio.end())+" • "+model.audio.volume+"%");
        voiceLane.setText(model.voice==null?"VOICE • belum ada track":"VOICE • "+time(model.voice.start)+" – "+time(model.voice.end())+" • "+model.voice.volume+"%");
        audioWave.bind(model.audio,model.duration());voiceWave.bind(model.voice,model.duration());
        pictureLane.setText(model.picture==null?"PIP • belum ada overlay":"PIP • "+time(model.picture.start)+" – "+time(model.picture.end())+" • "+model.picture.scale+"% • opacity "+model.picture.opacity+"%");
        captionLane.setText(model.caption==null?"CAPTION • kosong":"CAPTION • "+time(model.caption.in)+" – "+time(model.caption.out));
        if(model.clips.isEmpty()){status.setText("Timeline kosong");selectionKey="";positionMs=0;scrub.setProgress(0);return;}
        positionMs=Math.max(0,Math.min(positionMs,model.duration()-1));showPosition();
        scrub.setProgress(TimelineNavigation.toProgress(positionMs,model.duration()));
        for(int i=0;i<model.clips.size();i++){
            final int index=i;TimelineProject.Clip clip=model.clips.get(i);
            LinearLayout cell=StudioUi.column(getContext());
            long start=model.startOf(i);
            int width=zoom*Math.max(100,Math.min(240,(int)(100+220d*clip.length()/model.duration())));
            cell.addView(StudioUi.text(getContext(),time(start),10,StudioUi.MUTED,false));
            Button tile=StudioUi.button(getContext(),"Klip "+(i+1)+(clip.adjustments.mute?" · Bisu":"")+
                    (clip.adjustments.hasColor()?" · FX":"")+(clip.gainStart!=100||clip.gainEnd!=100||clip.zoomStart!=100||clip.zoomEnd!=100?" · KF":""),i==selected);
            tile.setContentDescription("Pilih segmen timeline "+(i+1)+", "+time(start)+" sampai "+time(start+clip.length()));
            tile.setSelected(i==selected);
            tile.setOnClickListener(v->select.accept(index));
            // Local drag only: stable project identity prevents a stale drop from moving a changed draft.
            tile.setOnLongClickListener(v->v.startDragAndDrop(android.content.ClipData.newPlainText("clip",""),
                    new android.view.View.DragShadowBuilder(v),new DragToken(model,index),0));
            cell.setOnDragListener((v,event)->{
                Object state=event.getLocalState();
                if(!(state instanceof DragToken)||((DragToken)state).project!=current)return false;
                switch(event.getAction()){
                    case android.view.DragEvent.ACTION_DRAG_STARTED:return true;
                    case android.view.DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(.65f);return true;
                    case android.view.DragEvent.ACTION_DRAG_EXITED:
                    case android.view.DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1f);return true;
                    case android.view.DragEvent.ACTION_DROP:
                        v.setAlpha(1f);int from=((DragToken)state).index;
                        if(from!=index)reorder.accept(from,index);return true;
                    default:return true;
                }
            });
            ImageView thumb=new ImageView(getContext());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(StudioUi.solid(getContext(),StudioUi.INNER,8));
            thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            thumb.setOnClickListener(v->select.accept(index));
            cell.addView(thumb,new LayoutParams(-1,dp(56)));
            requestThumbnail(clip,thumb);
            cell.addView(tile,new LayoutParams(-1,dp(48)));
            cell.addView(StudioUi.text(getContext(),time(clip.length())+(model.overlap.fadeIn(i)>0?" • overlap "+model.overlap.fadeIn(i)+"ms":""),10,StudioUi.MUTED,false));
            LayoutParams p=new LayoutParams(dp(width),-2);p.rightMargin=dp(6);track.addView(cell,p);
            if(i==selected&&!clip.id.equals(selectionKey)){
                selectionKey=clip.id;scroll.post(()->{if(cell.getParent()==track)scroll.smoothScrollTo(cell.getLeft(),0);});
            }
        }
    }
    private static final class DragToken {
        final TimelineProject project;final int index;
        DragToken(TimelineProject p,int i){project=p;index=i;}
    }
    private void showPosition(){
        overview.position(positionMs);
        String value=time(positionMs)+" / "+time(current.duration())+" • "+current.clips.size()+" klip"+
                (!current.cuts.isEmpty()?" • Transisi per batas":current.crossfadeMs>0?" • Crossfade "+current.crossfadeMs+" ms":current.fadeMs>0?" • Dip "+current.fadeMs+" ms":"");
        if(!value.contentEquals(status.getText()))status.setText(value);
    }
    long currentPosition(){return positionMs;}
    void position(long globalMs,long total){
        if(dragging)return;
        positionMs=Math.max(0,Math.min(globalMs,Math.max(0,total-1)));
        scrub.setProgress(TimelineNavigation.toProgress(positionMs,total));showPosition();
    }
}
