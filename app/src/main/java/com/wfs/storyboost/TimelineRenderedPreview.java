package com.wfs.storyboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.File;
import java.util.Locale;

/** Plays one private MP4 made by the same composition path as Timeline export. */
final class TimelineRenderedPreview {
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Session current;

    TimelineRenderedPreview(Activity activity){this.activity=activity;}

    void open(TimelineProject snapshot){
        close();
        if(snapshot.clips.isEmpty()||activity.isFinishing())return;
        Session s=new Session(snapshot);
        current=s;
        LinearLayout card=StudioUi.card(activity,16);
        card.addView(StudioUi.text(activity,"Menyiapkan preview dari hasil render MP4…",14,StudioUi.TEXT,true));
        StudioUi.gap(card,activity,14);
        s.progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        s.progress.setMax(100);s.progress.setIndeterminate(true);card.addView(s.progress);
        s.dialog=new AlertDialog.Builder(activity).setTitle("Preview Timeline").setView(card)
                .setNegativeButton("Batal",(d,w)->close(s)).create();
        s.dialog.setOnCancelListener(d->close(s));
        s.dialog.show();
        try{
            s.worker.startTimelinePreview(activity,snapshot,new VideoMergeEngine.Listener(){
                @Override public void onCompleted(File file){
                    if(current!=s)return;
                    s.worker.transferCompletedFile(file);
                    s.file=file;
                    android.util.Log.i("WfsTimelinePreviewQA","renderMs="+(android.os.SystemClock.elapsedRealtime()-s.startedAt)+" bytes="+file.length()+" durationMs="+snapshot.duration());
                    s.dialog.dismiss();
                    showPlayer(s);
                }
                @Override public void onFailure(String detail){fail(s,"Preview gagal",detail);}
            });
            main.post(s.poll);
        }catch(RuntimeException ex){fail(s,"Preview gagal",ex.getMessage());}
    }

    private void showPlayer(Session s){
        FrameLayout frame=new FrameLayout(activity);
        frame.setBackgroundColor(0xff080b18);
        s.video=new VideoView(activity);
        s.video.setContentDescription("Video hasil render Timeline");
        frame.addView(s.video,new FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER));
        LinearLayout card=StudioUi.card(activity,12);
        int screenHeight=activity.getResources().getConfiguration().screenHeightDp;
        card.addView(frame,new LinearLayout.LayoutParams(-1,dp(Math.max(96,Math.min(230,screenHeight-250)))));
        StudioUi.gap(card,activity,8);
        s.position=StudioUi.text(activity,"Memuat video…",12,StudioUi.MUTED,false);
        card.addView(s.position);
        s.seek=new SeekBar(activity);
        s.seek.setContentDescription("Posisi waktu Preview Timeline");
        s.seek.setEnabled(false);card.addView(s.seek,new LinearLayout.LayoutParams(-1,dp(48)));
        s.play=StudioUi.button(activity,"Putar",true);s.play.setEnabled(false);
        card.addView(s.play,new LinearLayout.LayoutParams(-1,dp(48)));
        ScrollView scroll=new ScrollView(activity);scroll.addView(card);
        s.dialog=new AlertDialog.Builder(activity).setTitle("Preview Timeline").setView(scroll)
                .setPositiveButton("Tutup",(d,w)->close(s)).create();
        s.dialog.setOnDismissListener(d->close(s));
        s.showingVideo=true;
        s.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){
                if(s.player==null)return;
                s.dragging=true;s.resumeAfterSeek=s.video.isPlaying()||s.resumeAfterSeek;s.video.pause();
            }
            @Override public void onProgressChanged(SeekBar bar,int ms,boolean fromUser){
                if(!fromUser||s.player==null)return;
                s.display(ms);
                if(!s.dragging)s.seekTo(ms,s.video.isPlaying()||s.resumeAfterSeek);
            }
            @Override public void onStopTrackingTouch(SeekBar bar){
                if(s.player==null)return;
                s.dragging=false;s.seekTo(bar.getProgress(),s.resumeAfterSeek);
            }
        });
        s.play.setOnClickListener(v->{
            if(s.player==null)return;
            s.background=false;
            if(s.video.isPlaying()||(s.pendingSeek>=0&&s.resumeAfterSeek))s.pausePlayback();
            else if(s.ended)s.seekTo(0,true);
            else if(s.pendingSeek>=0)s.resumeAfterSeek=true;
            else s.video.start();
            s.setPlayText();
        });
        s.video.setOnPreparedListener(player->{
            if(current!=s)return;
            s.player=player;s.pendingSeek=-1;s.issuedSeek=-1;
            int duration=s.video.getDuration();
            if(duration<=0){fail(s,"Video preview tidak bisa diputar",null);return;}
            s.seek.setMax(duration);
            s.seek.setKeyProgressIncrement(Math.max(1,Math.min(250,duration/10)));
            s.seek.setEnabled(true);s.play.setEnabled(true);
            player.setOnSeekCompleteListener(done->{
                if(current!=s||done!=s.player)return;
                if(s.pendingSeek!=s.issuedSeek){s.sendSeek();return;}
                s.pendingSeek=-1;s.issuedSeek=-1;
                if(!s.dragging){
                    int actual=s.video.getCurrentPosition();
                    s.seek.setProgress(actual);s.display(actual);
                    android.util.Log.i("WfsTimelinePreviewQA","seekSettledMs="+actual);
                }
                if(s.resumeAfterSeek&&!s.dragging&&!s.background)s.video.start();
                if(!s.dragging)s.resumeAfterSeek=false;
                s.setPlayText();
            });
            s.display(0);
            android.util.Log.i("WfsTimelinePreviewQA","readyMs="+(android.os.SystemClock.elapsedRealtime()-s.startedAt)+" durationMs="+duration);
            if(!s.background)s.video.start();
            s.setPlayText();
        });
        s.video.setOnCompletionListener(player->{
            if(current!=s)return;
            if(s.pendingSeek>=0||s.dragging)return;
            s.ended=true;s.resumeAfterSeek=false;
            s.seek.setProgress(s.seek.getMax());s.display(s.seek.getMax());s.setPlayText();
        });
        s.video.setOnErrorListener((player,what,extra)->{
            fail(s,"Video preview tidak bisa diputar",null);return true;
        });
        s.dialog.show();
        s.video.setVideoURI(Uri.fromFile(s.file));
    }

    void pause(){
        Session s=current;
        if(s==null)return;
        // VideoView may release its surface while backgrounded. Close the private
        // render rather than retain a stale MediaPlayer or restart at zero silently.
        close(s);
    }
    void close(){if(current!=null)close(current);}
    private void close(Session s){
        if(current!=s)return;
        current=null;
        main.removeCallbacks(s.poll);
        s.worker.cancel();
        if(s.video!=null)s.video.stopPlayback();
        if(s.dialog!=null)s.dialog.dismiss();
        if(s.file!=null)MediaSaver.discardTemporaryFile(s.file);
    }
    private void fail(Session s,String title,String detail){
        if(current!=s)return;
        close(s);
        Toast.makeText(activity,title+(detail==null?"":". "+detail),Toast.LENGTH_LONG).show();
    }
    private int dp(int units){return StudioUi.dp(activity,units);}
    private static String clock(int ms){
        return String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,(ms/1000)%60,ms%1000);
    }

    private final class Session {
        final TimelineProject snapshot;
        final long startedAt=android.os.SystemClock.elapsedRealtime();
        final VideoMergeEngine worker=new VideoMergeEngine();
        AlertDialog dialog;
        ProgressBar progress;
        VideoView video;
        MediaPlayer player;
        Button play;
        TextView position;
        SeekBar seek;
        File file;
        boolean showingVideo,dragging,resumeAfterSeek,ended,background;
        int pendingSeek=-1,issuedSeek=-1;
        Session(TimelineProject snapshot){this.snapshot=snapshot;}
        void seekTo(int ms,boolean resume){
            if(player==null)return;
            if(ended)ended=false;
            pendingSeek=Math.max(0,Math.min(ms,Math.max(0,seek.getMax()-1)));
            position.setText("Mencari posisi…");
            resumeAfterSeek=resume;
            video.pause();
            if(issuedSeek<0)sendSeek();
            setPlayText();
        }
        void sendSeek(){
            issuedSeek=pendingSeek;
            try{player.seekTo(issuedSeek,MediaPlayer.SEEK_CLOSEST);}
            catch(RuntimeException ex){fail(this,"Posisi preview tidak tersedia",ex.getMessage());}
        }
        void pausePlayback(){
            resumeAfterSeek=false;
            if(video!=null)video.pause();
            setPlayText();
        }
        void display(int ms){
            int index=snapshot.clipIndexAt(ms);
            if(index<0&&ms>=snapshot.duration())index=snapshot.clips.size()-1;
            String next=clock(ms)+" / "+clock(seek.getMax())+(index<0?"":"  ·  Klip "+(index+1));
            if(!next.contentEquals(position.getText()))position.setText(next);
        }
        void setPlayText(){
            if(play!=null&&video!=null){
                String next=video.isPlaying()||(pendingSeek>=0&&resumeAfterSeek)?"Jeda":"Putar";
                if(!next.contentEquals(play.getText()))play.setText(next);
            }
        }
        final Runnable poll=new Runnable(){
            @Override public void run(){
                if(current!=Session.this)return;
                if(!showingVideo){
                    int value=worker.progress();
                    if(value>=0){progress.setIndeterminate(false);progress.setProgress(value);}
                }else if(player!=null&&!dragging&&pendingSeek<0&&!ended){
                    int ms=video.getCurrentPosition();seek.setProgress(ms);display(ms);setPlayText();
                }
                main.postDelayed(this,showingVideo?150:350);
            }
        };
    }
}
