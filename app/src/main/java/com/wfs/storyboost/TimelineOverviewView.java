package com.wfs.storyboost;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.Locale;

/** Proportional global-time navigator, independent of minimum-width clip cards.
 * Existing native seek bar remains the accessible interactive scrub control.
 */
final class TimelineOverviewView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private TimelineProject model=TimelineProject.empty();
    private long position;private int selected=-1;
    private final java.util.function.LongConsumer seek;
    private boolean touching;private long pending;
    TimelineOverviewView(Context context,java.util.function.LongConsumer seek){
        super(context);this.seek=seek;
        // Native seekbar is the TalkBack/keyboard equivalent; do not duplicate it.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(true);
    }
    private long timeAt(float x){
        float span=Math.max(1,getWidth()-dp(58));
        double fraction=Math.max(0,Math.min(1,(x-dp(52))/span));
        return Math.min(Math.max(0,model.duration()-1),Math.round(fraction*model.duration()));
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(model.duration()==0)return false;
        switch(event.getActionMasked()){
            case android.view.MotionEvent.ACTION_DOWN:
                if(event.getX()<dp(52)||event.getY()<dp(19))return false;
                touching=true;pending=timeAt(event.getX());getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case android.view.MotionEvent.ACTION_MOVE:
                if(!touching)return false;pending=timeAt(event.getX());invalidate();return true;
            case android.view.MotionEvent.ACTION_UP:
                if(!touching)return false;pending=timeAt(event.getX());touching=false;
                getParent().requestDisallowInterceptTouchEvent(false);performClick();seek.accept(pending);invalidate();return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                touching=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            default:return touching;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    void bind(TimelineProject model,int selected){this.model=model;this.selected=selected;position=Math.min(position,Math.max(0,model.duration()-1));invalidate();}
    void position(long ms){if(position!=ms){position=ms;invalidate();}}
    private float dp(int n){return StudioUi.dp(getContext(),n);}
    private float x(long ms){return dp(52)+(getWidth()-dp(58))*ms/(float)Math.max(1,model.duration());}
    private void rect(Canvas c,float left,float top,float right,float bottom,int color){
        paint.setColor(color);c.drawRoundRect(left,top,right,bottom,dp(3),dp(3),paint);
    }
    private void lane(Canvas c,int row,String name,long start,long end,int color){
        float y=dp(24+row*20);paint.setColor(StudioUi.MUTED);paint.setTextSize(dp(10));c.drawText(name,dp(5),y+dp(12),paint);
        rect(c,dp(52),y,getWidth()-dp(6),y+dp(16),StudioUi.INNER);
        if(end>start)rect(c,x(start),y,x(end),y+dp(16),color);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);rect(c,0,0,getWidth(),getHeight(),StudioUi.BG);
        if(model.duration()==0)return;
        paint.setColor(StudioUi.MUTED);paint.setTextSize(dp(10));
        for(int i=0;i<=4;i++){
            long t=model.duration()*i/4;float x=x(t);paint.setColor(StudioUi.INNER);c.drawLine(x,dp(20),x,dp(145),paint);
            String text=String.format(Locale.ROOT,"%.1fs",t/1000d);
            paint.setColor(StudioUi.MUTED);float offset=i==4?paint.measureText(text):0;c.drawText(text,x-offset,dp(15),paint);
        }
        lane(c,0,"Video",0,0,0);
        for(int i=0;i<model.clips.size();i++){
            float left=x(model.startOf(i)),right=x(model.overlap.ends[i]);
            rect(c,left,dp(24),right,dp(40),i==selected?0xff8a6bce:0xff385d8f);
            if(model.overlap.fadeIn(i)>0)rect(c,left,dp(24),x(model.startOf(i)+model.overlap.fadeIn(i)),dp(40),0xffcc9b5b);
        }
        PictureLayer picture=model.picture;
        lane(c,1,"PiP",picture==null?0:picture.start,picture==null?0:picture.end(),0xff8662b5);
        PictureLayer second=model.picture2;
        lane(c,2,"PiP 2",second==null?0:second.start,second==null?0:second.end(),0xffb86b9d);
        TimelineProject.Audio sound=model.audio,voice=model.voice;
        lane(c,3,"Audio",sound==null?0:sound.start,sound==null?0:sound.end(),0xff26867a);
        lane(c,4,"Voice",voice==null?0:voice.start,voice==null?0:voice.end(),0xff347d9d);
        TimelineProject.Caption caption=model.caption;
        lane(c,5,"Caption",caption==null?0:caption.in,caption==null?0:caption.out,0xff9c7344);
        paint.setColor(0xfff5f5ff);paint.setStrokeWidth(dp(1));c.drawLine(x(touching?pending:position),dp(19),x(touching?pending:position),dp(145),paint);
    }
}
