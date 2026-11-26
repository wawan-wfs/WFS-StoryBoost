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

/** Native, accessible sequence overview. Width is bounded for very short/long clips. */
final class TimelineStrip extends LinearLayout {
    private final LinearLayout track;
    private final TextView status;
    private final HorizontalScrollView scroll;
    private final IntConsumer select;
    private String selectionKey="";
    private final SeekBar scrub;
    private final TextView audioLane;
    private final TextView captionLane;
    private TimelineProject current=TimelineProject.empty();
    private int currentSelection=-1,zoom=1;
    private long positionMs;
    private boolean dragging;
    TimelineStrip(Context context,IntConsumer select,LongConsumer seek){
        super(context);this.select=select;setOrientation(VERTICAL);
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
                if(user){positionMs=TimelineNavigation.fromProgress(progress,current.duration());showPosition();}
            }
            public void onStopTrackingTouch(SeekBar bar){dragging=false;seek.accept(positionMs);}
        });
        LinearLayout toolbar=StudioUi.row(context);
        toolbar.addView(StudioUi.text(context,"VIDEO / AUDIO / CAPTION",10,StudioUi.MUTED,true),new LayoutParams(0,-2,1));
        Button magnify=StudioUi.button(context,"Zoom 1×",false);
        magnify.setOnClickListener(v->{zoom=zoom==3?1:zoom+1;magnify.setText("Zoom "+zoom+"×");bind(current,currentSelection);});
        toolbar.addView(magnify,new LayoutParams(dp(100),dp(48)));addView(toolbar);
        scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(true);
        track=StudioUi.row(context);scroll.addView(track,new HorizontalScrollView.LayoutParams(-2,-2));
        addView(scroll,new LayoutParams(-1,-2));
        audioLane=StudioUi.text(context,"AUDIO • belum ada soundtrack",12,StudioUi.GREEN,true);
        captionLane=StudioUi.text(context,"CAPTION • kosong",11,StudioUi.LAVENDER,true);
        StudioUi.gap(this,context,10);addView(audioLane);StudioUi.gap(this,context,8);addView(captionLane);
    }
    private int dp(int n){return StudioUi.dp(getContext(),n);}
    private static String time(long ms){return String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,ms/1000%60,ms%1000);}
    void bind(TimelineProject model,int selected){
        current=model;currentSelection=selected;track.removeAllViews();
        scrub.setEnabled(!model.clips.isEmpty());
        audioLane.setText(model.audio==null?"AUDIO • belum ada soundtrack":"AUDIO • "+time(model.audio.start)+" – "+time(model.audio.end())+" • "+model.audio.volume+"%");
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
                    (clip.adjustments.hasColor()?" · FX":""),i==selected);
            tile.setContentDescription("Pilih segmen timeline "+(i+1)+", "+time(start)+" sampai "+time(start+clip.length()));
            tile.setSelected(i==selected);
            tile.setOnClickListener(v->select.accept(index));
            cell.addView(tile,new LayoutParams(-1,dp(48)));
            cell.addView(StudioUi.text(getContext(),time(clip.length()),10,StudioUi.MUTED,false));
            LayoutParams p=new LayoutParams(dp(width),-2);p.rightMargin=dp(6);track.addView(cell,p);
            if(i==selected&&!clip.id.equals(selectionKey)){
                selectionKey=clip.id;scroll.post(()->{if(cell.getParent()==track)scroll.smoothScrollTo(cell.getLeft(),0);});
            }
        }
    }
    private void showPosition(){
        String value=time(positionMs)+" / "+time(current.duration())+" • "+current.clips.size()+" klip"+
                (current.fadeMs>0?" • Fade "+current.fadeMs+" ms":"");
        if(!value.contentEquals(status.getText()))status.setText(value);
    }
    long currentPosition(){return positionMs;}
    void position(long globalMs,long total){
        if(dragging)return;
        positionMs=Math.max(0,Math.min(globalMs,Math.max(0,total-1)));
        scrub.setProgress(TimelineNavigation.toProgress(positionMs,total));showPosition();
    }
}
