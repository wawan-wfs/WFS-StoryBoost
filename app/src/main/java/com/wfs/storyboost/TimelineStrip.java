package com.wfs.storyboost;

import android.content.Context;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Native, accessible sequence overview. Width is bounded for very short/long clips. */
final class TimelineStrip extends LinearLayout {
    private final LinearLayout track;
    private final TextView status;
    private final HorizontalScrollView scroll;
    private final IntConsumer select;
    private String selectionKey="";
    TimelineStrip(Context context,IntConsumer select){
        super(context);this.select=select;setOrientation(VERTICAL);
        setPadding(dp(12),dp(12),dp(12),dp(12));
        setBackground(StudioUi.solid(context,StudioUi.PANEL,16));
        addView(StudioUi.text(context,"TIMELINE  /  VIDEO",11,StudioUi.CYAN,true));
        status=StudioUi.text(context,"00:00.000",12,StudioUi.TEXT,true);
        addView(status);StudioUi.gap(this,context,8);
        scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(true);
        track=StudioUi.row(context);scroll.addView(track,new HorizontalScrollView.LayoutParams(-2,-2));
        addView(scroll,new LayoutParams(-1,-2));
    }
    private int dp(int n){return StudioUi.dp(getContext(),n);}
    private static String time(long ms){return String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,ms/1000%60,ms%1000);}
    void bind(TimelineProject model,int selected){
        track.removeAllViews();
        if(model.clips.isEmpty()){status.setText("Timeline kosong");selectionKey="";return;}
        status.setText("00:00.000  —  "+time(model.duration())+"  •  "+model.clips.size()+" klip");
        for(int i=0;i<model.clips.size();i++){
            final int index=i;TimelineProject.Clip clip=model.clips.get(i);
            LinearLayout cell=StudioUi.column(getContext());
            long start=model.startOf(i);
            int width=Math.max(100,Math.min(240,(int)(100+220d*clip.length()/model.duration())));
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
    void position(long globalMs,long total){
        String value=time(globalMs)+" / "+time(total)+"  •  Posisi klip terpilih";
        if(!value.contentEquals(status.getText()))status.setText(value);
    }
}
