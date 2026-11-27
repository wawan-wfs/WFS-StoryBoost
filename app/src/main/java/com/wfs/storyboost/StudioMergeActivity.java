package com.wfs.storyboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real multi-clip storyboard with reorder/delete, single-clip preview and
 * Media3 sequence export, optional independent soundtrack and dip-to-black transitions.
 */
public final class StudioMergeActivity extends Activity {
    static final String EXTRA_TIMELINE="one_track_timeline",EXTRA_MIGRATE_URI="timeline_migrate_uri";
    private static final int PICK_CLIPS=901,PICK_AUDIO=902,PICK_VOICE=903,PICK_PICTURE=904;
    private long previewGeneration;
    private final ArrayList<Uri> clips=new ArrayList<>();
    private final ExecutorService thumbs=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private VideoMergeEngine engine;
    private VideoView preview;
    private TimelineRenderedPreview renderedPreview;
    private Button openRenderedPreview;
    private android.media.MediaPlayer trimPlayer;
    private LinearLayout clipList;
    private TextView count,info;
    private int quality=OutputProfile.FULL_HD,selected=-1;
    private boolean working,destroyed;
    private boolean timelineMode,importing,unreadableDraft;
    private TimelineProject.History timeline;
    private Button undoTimeline,redoTimeline,adjustTimeline;
    private TimelineStrip timelineStrip;
    private final Button[] qualityButtons=new Button[3];
    private java.util.concurrent.atomic.AtomicBoolean mergeStop=
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private ProgressBar progress;
    private AlertDialog dialog;
    private AlertDialog mergeResultDialog;
    private Uri lastSaved;
    private int d(int n){return StudioUi.dp(this,n);}
    private TextView txt(String text,int size,int color,boolean bold){
        return StudioUi.text(this,text,size,color,bold);
    }
    private Button button(String t,boolean main){return StudioUi.button(this,t,main);}
    private void gap(LinearLayout l,int n){StudioUi.gap(l,this,n);}
    private LinearLayout col(){return StudioUi.column(this);}
    private LinearLayout row(){return StudioUi.row(this);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(StudioUi.BG);
        getWindow().setNavigationBarColor(StudioUi.BG);
        timelineMode=getIntent().getBooleanExtra(EXTRA_TIMELINE,false);
        if(timelineMode){
            TimelineProject restored=TimelineDraftStore.load(this);
            unreadableDraft=restored==null&&TimelineDraftStore.exists(this);
            timeline=new TimelineProject.History(restored==null?TimelineProject.empty():restored);
            syncTimelineClips();
            renderedPreview=new TimelineRenderedPreview(this);
        }
        build();
        if(timelineMode){
            if(unreadableDraft)toast("Draft timeline tidak terbaca. Gunakan Reset untuk mulai ulang.");
            else if(savedSeed(state))migrateSingleSource();
            if(!clips.isEmpty())select(0);
            main.post(trimPreviewTicker);
        }
    }
    private boolean savedSeed(Bundle state){return state==null&&getIntent().hasExtra(EXTRA_MIGRATE_URI);}
    private void build(){
        LinearLayout root=col();root.setBackgroundColor(StudioUi.BG);
        root.setFitsSystemWindows(true);
        LinearLayout header=row();header.setPadding(d(12),d(9),d(12),d(7));
        Button back=button("‹",false);back.setTextSize(26);
        header.addView(back,new LinearLayout.LayoutParams(d(44),d(45)));
        back.setOnClickListener(v->finish());
        LinearLayout title=col();title.addView(txt(timelineMode?"Timeline Studio":"Video Merge Studio",19,StudioUi.TEXT,true));
        title.addView(txt(timelineMode?"VIDEO + AUDIO  •  DRAF OTOMATIS":"MULTI-CLIP  •  REORDER & JOIN",10,StudioUi.CYAN,true));
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1f);l.leftMargin=d(9);
        header.addView(title,l);
        root.addView(header);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout body=col();body.setPadding(d(16),d(13),d(16),d(30));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout hero=StudioUi.card(this,16);
        hero.setBackground(StudioUi.gradient(this,0xFF343568,0xFF19283E,18));
        hero.addView(txt(timelineMode?"Studio / Video project":"Buat satu cerita dari banyak klip.",19,StudioUi.TEXT,true));
        gap(hero,8);
        hero.addView(txt(timelineMode?"Sampai 10 klip. Sumber yang sama boleh ditambahkan lagi. "+
                "Putar klip terpilih atau buka preview lengkap dengan caption.":
                "Pilih sampai 10 video. Ubah urutan, preview satu klip, lalu gabungkan menjadi MP4.",12,StudioUi.MUTED,false));
        gap(hero,12);
        Button add=button("＋  Tambahkan Video",true);
        hero.addView(add,new LinearLayout.LayoutParams(-1,d(48)));
        add.setOnClickListener(v->pickVideos());
        body.addView(hero);gap(body,15);

        preview=new VideoView(this);
        preview.setBackground(StudioUi.solid(this,0xFF080B18,17));
        body.addView(preview,new LinearLayout.LayoutParams(-1,d(190)));
        if(timelineMode){
            body.addView(txt("Preview sumber • warna final tersedia di Preview Timeline",11,StudioUi.MUTED,false));
        }
        gap(body,12);
        LinearLayout actions=row();
        count=txt("Belum ada klip",13,StudioUi.TEXT,true);
        actions.addView(count,new LinearLayout.LayoutParams(0,-2,1f));
        Button play=button("▶ Play",false);
        actions.addView(play,new LinearLayout.LayoutParams(d(104),d(40)));
        play.setOnClickListener(v->{if(selected<0)return;
            if(preview.isPlaying())preview.pause();else preview.start();});
        body.addView(actions);gap(body,11);
        if(timelineMode){
            openRenderedPreview=button("Preview Timeline",false);
            body.addView(openRenderedPreview,new LinearLayout.LayoutParams(-1,d(48)));
            openRenderedPreview.setOnClickListener(v->{
                if(!canEditTimeline()||clips.isEmpty())return;
                preview.pause();
                renderedPreview.open(timeline.current());
            });
            gap(body,10);
            timelineStrip=new TimelineStrip(this,this::select,this::seekTimeline,(from,to)->{
                if(canEditTimeline())editTimeline(timeline.current().move(from,to));
            });body.addView(timelineStrip);gap(body,10);
            LinearLayout historyBar=row();
            undoTimeline=timelineButton(historyBar,"Undo",()->historyStep(false));
            redoTimeline=timelineButton(historyBar,"Redo",()->historyStep(true));
            timelineButton(historyBar,"Reset",this::resetTimeline);
            body.addView(historyBar);gap(body,7);
            LinearLayout editBar=row();
            timelineButton(editBar,"Trim klip",this::editTrim);
            timelineButton(editBar,"Caption waktu",this::editTimedCaption);
            body.addView(editBar);gap(body,7);
            LinearLayout cutBar=row();
            timelineButton(cutBar,"Split",this::splitAtPlayhead);
            timelineButton(cutBar,"Duplikat",this::duplicateClip);
            timelineButton(cutBar,"Fade",this::editFade);
            LinearLayout transitionBar=row();
            timelineButton(transitionBar,"Transisi klip →",this::editCutTransition);
            body.addView(cutBar);gap(body,7);
            body.addView(transitionBar);gap(body,7);
            LinearLayout audioBar=row();
            timelineButton(audioBar,"＋ Audio",this::pickAudio);
            timelineButton(audioBar,"Edit audio",this::editSoundtrack);
            body.addView(audioBar);gap(body,7);
            LinearLayout voiceBar=row();
            timelineButton(voiceBar,"＋ Voice",()->pickTrack(true));
            timelineButton(voiceBar,"Edit voice",()->editTrack(true));
            timelineButton(voiceBar,"Keyframe",this::editKeyframes);
            body.addView(voiceBar);gap(body,7);
            LinearLayout pictureBar=row();
            timelineButton(pictureBar,"＋ PiP",this::pickPicture);
            timelineButton(pictureBar,"Edit PiP",this::editPicture);
            body.addView(pictureBar);gap(body,7);
            adjustTimeline=button("Color Look • Adjust • Audio",true);
            adjustTimeline.setOnClickListener(v->editClipAdjustments());
            body.addView(adjustTimeline,new LinearLayout.LayoutParams(-1,d(48)));gap(body,11);
        }
        clipList=col();body.addView(clipList);
        info=txt("Dip-to-black menjaga durasi. Crossfade overlap memendekkan durasi; gunakan Preview Timeline untuk hasil campuran.",11,StudioUi.MUTED,false);
        gap(body,10);body.addView(info);

        LinearLayout qualityBar=row();qualityBar.setPadding(d(9),d(8),d(9),d(8));
        qualityBar.addView(txt("KUALITAS",11,StudioUi.CYAN,true),
                new LinearLayout.LayoutParams(0,-2,1f));
        String[] names={"HD","1080p","Max"};
        for(int i=0;i<3;i++){
            final int mode=i;
            Button q=button(names[i],i==quality);
            qualityButtons[i]=q;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(69),d(38));
            if(i>0)p.leftMargin=d(5);
            qualityBar.addView(q,p);
            q.setOnClickListener(v->{
                if(timelineMode){
                    if(!canEditTimeline())return;
                    editTimeline(timeline.current().quality(mode));
                }else quality=mode;
                for(int index=0;index<qualityBar.getChildCount();index++){
                    View child=qualityBar.getChildAt(index);
                    if(child instanceof Button){
                        int selected=index-1;
                        child.setBackground(selected==quality?
                                StudioUi.gradient(this,0xFF9A57E9,0xFF3B7EEC,12):
                                StudioUi.solid(this,StudioUi.INNER,12));
                    }
                }
            });
        }
        root.addView(qualityBar);
        Button export=button(timelineMode?"Ekspor Timeline MP4   ↗":"Gabungkan & Ekspor MP4   ↗",true);
        LinearLayout.LayoutParams e=new LinearLayout.LayoutParams(-1,d(55));
        e.setMargins(d(16),0,d(16),d(14));root.addView(export,e);
        export.setOnClickListener(v->startMerge());
        renderList();
        setContentView(root);
    }
    private void pickVideos(){
        if(working||(timelineMode&&!canEditTimeline()))return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_CLIPS);}
        catch(Exception e){toast("Galeri tidak tersedia: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if((request==PICK_AUDIO||request==PICK_VOICE)&&timelineMode){if(data.getData()!=null)importSoundtrack(data.getData(),request==PICK_VOICE);return;}
        if(request==PICK_PICTURE&&timelineMode){if(data.getData()!=null)importPicture(data.getData());return;}
        if(request!=PICK_CLIPS)return;
        ArrayList<Uri> incoming=new ArrayList<>();
        if(data.getClipData()!=null){
            for(int i=0;i<data.getClipData().getItemCount();i++)
                incoming.add(data.getClipData().getItemAt(i).getUri());
        }else if(data.getData()!=null)incoming.add(data.getData());
        if(timelineMode){importTimeline(incoming);return;}
        for(Uri item:incoming){
            if(clips.size()>=10)break;
            if(clips.contains(item))continue;
            try{getContentResolver().takePersistableUriPermission(item,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            clips.add(item);
        }
        if(selected<0&&!clips.isEmpty())select(0);
        renderList();
    }
    private void select(int index){selectAt(index,timelineMode&&index>=0&&index<clips.size()?timeline.current().clips.get(index).in:250);}
    private void selectAt(int index,long position){
        if(index<0||index>=clips.size()||(timelineMode&&working))return;
        final long generation=++previewGeneration;
        selected=index;
        preview.stopPlayback();
        trimPlayer=null;
        Uri uri=clips.get(index);
        if(timelineMode&&!SourceAccess.canRead(this,uri)){
            toast("Izin baca klip hilang. Impor ulang atau hapus klip untuk melanjutkan.");
            renderList();return;
        }
        final boolean mute=timelineMode&&timeline.current().clips.get(index).adjustments.mute;
        preview.setOnPreparedListener(player->{
            if(selected!=index||destroyed||previewGeneration!=generation)return;
            if(timelineMode){
                trimPlayer=player;
                player.setVolume(mute?0f:1f,mute?0f:1f);
                player.seekTo(position,android.media.MediaPlayer.SEEK_CLOSEST);
            }else preview.seekTo((int)position);
        });
        preview.setVideoURI(uri);
        renderList();
        if(timelineMode)timelineStrip.position(timeline.current().startOf(index)+position-timeline.current().clips.get(index).in,timeline.current().duration());
    }
    private String name(Uri uri){
        try(Cursor c=getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "Video "+(clips.indexOf(uri)+1);
    }
    private void move(int index,int delta){
        int newPos=index+delta;
        if(newPos<0||newPos>=clips.size())return;
        if(timelineMode){
            if(canEditTimeline())editTimeline(timeline.current().move(index,newPos));
            return;
        }
        Collections.swap(clips,index,newPos);
        if(selected==index)selected=newPos;else if(selected==newPos)selected=index;
        renderList();
    }
    private void renderList(){
        if(clipList==null)return;
        clipList.removeAllViews();
        count.setText(clips.isEmpty()?"Belum ada klip":clips.size()+" klip dalam storyboard");
        if(timelineMode){
            openRenderedPreview.setEnabled(!clips.isEmpty()&&!unreadableDraft);
            undoTimeline.setEnabled(timeline.canUndo()&&!unreadableDraft);
            redoTimeline.setEnabled(timeline.canRedo()&&!unreadableDraft);
            TimelineProject p=timeline.current();
            timelineStrip.bind(p,selected);
            adjustTimeline.setEnabled(!unreadableDraft&&selected>=0&&selected<p.clips.size());
            info.setText(unreadableDraft?"Draft tidak terbaca. Pilih Reset untuk memulai ulang.":
                    "Total "+p.duration()+" ms • "+(p.caption==null?"Tanpa caption":
                    "Caption "+p.caption.in+"–"+p.caption.out+" ms"));
        }
        if(clips.isEmpty()){
            clipList.addView(txt("Tambah beberapa video untuk mulai menyusun urutan.",12,StudioUi.MUTED,false));
            return;
        }
        for(int i=0;i<clips.size();i++){
            final int index=i;
            Uri clip=clips.get(index);
            LinearLayout entry=row();
            entry.setPadding(d(9),d(8),d(9),d(8));
            entry.setBackground(StudioUi.solid(this,index==selected?0xFF34345B:StudioUi.PANEL,13));
            TextView number=txt(String.valueOf(index+1),14,StudioUi.LAVENDER,true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(StudioUi.solid(this,0xFF202D4A,9));
            entry.addView(number,new LinearLayout.LayoutParams(d(33),d(48)));
            LinearLayout title=col();
            TextView file=txt(name(clip),12,StudioUi.TEXT,true);file.setMaxLines(1);file.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.addView(file);gap(title,3);
            if(timelineMode){
                TimelineProject.Clip segment=timeline.current().clips.get(index);
                title.addView(txt(segment.in+"–"+segment.out+" ms  •  "+segment.length()+" ms",10,StudioUi.MUTED,false));
                TextView fx=txt(segment.adjustments.summary(),10,StudioUi.CYAN,false);
                fx.setMaxLines(2);title.addView(fx);
            }else{
                long duration=MediaProbe.duration(this,clip);
                title.addView(txt(duration>0?String.format(java.util.Locale.US,"%d detik",duration/1000):"Preview",10,StudioUi.MUTED,false));
            }
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);
            tp.leftMargin=d(9);entry.addView(title,tp);
            String[] symbols={"↑","↓","×"};
            for(int j=0;j<3;j++){
                final int action=j;
                Button b=button(symbols[j],false);b.setTextSize(18);
                b.setContentDescription((j==0?"Naikkan":j==1?"Turunkan":"Hapus")+" klip "+(index+1));
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(d(32),d(38));
                bp.leftMargin=d(4);entry.addView(b,bp);
                b.setOnClickListener(v->{
                    if(action==0)move(index,-1);
                    else if(action==1)move(index,1);
                    else{
                        if(timelineMode){
                            if(canEditTimeline())editTimeline(timeline.current().remove(index));
                            return;
                        }
                        clips.remove(index);
                        if(clips.isEmpty()){selected=-1;preview.stopPlayback();}
                        else if(selected>=clips.size())selected=clips.size()-1;
                        renderList();
                    }
                });
            }
            entry.setOnClickListener(v->select(index));
            clipList.addView(entry);gap(clipList,7);
        }
    }
    private void startMerge(){
        if(working||(timelineMode&&!canEditTimeline()))return;
        if(clips.isEmpty()){toast("Tambahkan video terlebih dahulu.");return;}
        working=true;
        mergeStop=new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean token=mergeStop;
        final VideoMergeEngine worker=new VideoMergeEngine();
        engine=worker;
        final ArrayList<Uri> sources=new ArrayList<>(clips);
        if(timelineMode&&timeline.current().audio!=null)sources.add(Uri.parse(timeline.current().audio.uri));
        if(timelineMode&&timeline.current().voice!=null)sources.add(Uri.parse(timeline.current().voice.uri));
        if(timelineMode&&timeline.current().picture!=null)sources.add(Uri.parse(timeline.current().picture.uri));
        LinearLayout progressCard=StudioUi.card(this,17);
        progressCard.addView(txt("Sedang menggabungkan "+clips.size()+" klip",17,StudioUi.TEXT,true));
        gap(progressCard,12);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progressCard.addView(progress);
        dialog=new AlertDialog.Builder(this).setView(progressCard).setCancelable(false)
                .setNegativeButton("Batal",(d,w)->cancelMerge()).create();
        dialog.show();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try{
            VideoMergeEngine.Listener listener=new VideoMergeEngine.Listener(){
                @Override public void onCompleted(java.io.File file){
                    if(token.get())return;
                    worker.transferCompletedFile(file);
                    thumbs.execute(()->{
                        try{
                            if(token.get())return;
                            String exportName="WFS_JOIN_"+System.currentTimeMillis()+".mp4";
                            Uri saved=MediaSaver.saveVideo(StudioMergeActivity.this,file,exportName,token::get,sources);
                            MediaSaver.beforeCallback(saved,exportName);
                            runOnUiThread(()->{
                                if(token.get()||destroyed){
                                    MediaSaver.discardUnaccepted(StudioMergeActivity.this,saved);
                                    return;
                                }
                                lastSaved=saved;finishMerge(token,null);
                            });
                        }catch(Exception ex){
                            if(!token.get())
                                runOnUiThread(()->finishMerge(token,ex.getMessage()));
                        }finally{
                            MediaSaver.discardTemporaryFile(file);
                        }
                    });
                }
                @Override public void onFailure(String message){
                    if(!token.get())finishMerge(token,message);
                }
            };
            if(timelineMode)worker.startTimeline(this,timeline.current(),listener);
            else worker.start(this,sources,quality,listener);
            main.post(new Runnable(){
                @Override public void run(){
                    if(!working||engine!=worker||destroyed||token.get())return;
                    int value=worker.progress();if(value>=0)progress.setProgress(value);
                    main.postDelayed(this,400);
                }
            });
        }catch(Exception e){finishMerge(token,e.getMessage());}
    }
    private void finishMerge(java.util.concurrent.atomic.AtomicBoolean token,String failure){
        if(!destroyed&&token==mergeStop&&!token.get())finishMerge(failure);
    }
    private void cancelMerge(){
        mergeStop.set(true);
        if(engine!=null)engine.cancel();
        finishMerge("Penggabungan dibatalkan.");
    }
    private void finishMerge(String failure){
        working=false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(dialog!=null)dialog.dismiss();
        if(failure!=null){toast(failure);if(engine!=null)engine.clean();return;}
        if(isFinishing()||destroyed)return;
        mergeResultDialog=new AlertDialog.Builder(this).setTitle("Video berhasil digabung")
            .setMessage("MP4 tersimpan di Galeri → Movies/WFS StoryBoost.")
            .setPositiveButton("Selesai",null)
            .setNeutralButton("Bagikan",(d,w)->share()).show();
    }
    private void share(){
        if(lastSaved==null)return;
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("video/mp4");
        send.putExtra(Intent.EXTRA_STREAM,lastSaved);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send,"Bagikan gabungan video"));
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private Button timelineButton(LinearLayout row,String label,Runnable action){
        Button button=button(label,false);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,d(43),1);
        params.rightMargin=d(5);row.addView(button,params);
        button.setOnClickListener(v->action.run());
        return button;
    }
    private boolean canEditTimeline(){return !working&&!importing&&!unreadableDraft;}
    private void syncTimelineClips(){
        clips.clear();
        TimelineProject model=timeline.current();
        quality=model.quality;
        for(TimelineProject.Clip clip:model.clips)clips.add(Uri.parse(clip.uri));
    }
    private void migrateSingleSource(){
        Uri uri=Uri.parse(getIntent().getStringExtra(EXTRA_MIGRATE_URI));
        if(!timeline.current().clips.isEmpty()){
            toast("Draft timeline sudah ada. Draft itu tetap dibuka tanpa ditimpa.");
            return;
        }
        if(!TimelineDraftStore.hasReadGrant(this,uri)||!SourceAccess.canRead(this,uri)){
            toast("Impor melalui pemilih file agar izin bertahan setelah aplikasi ditutup.");
            return;
        }
        StudioDraftStore.Draft legacy=StudioDraftStore.load(this,uri);
        if(legacy==null){toast("Draft satu media tidak tersedia.");return;}
        try{
            editTimeline(TimelineDraftStore.fromSingleSource(uri,legacy.options,MediaProbe.duration(this,uri)));
            toast("Trim dan caption disalin ke timeline. Efek, mute, rasio, dan split tetap di draft lama.");
        }catch(IllegalArgumentException invalid){toast(invalid.getMessage());}
    }
    private void importTimeline(List<Uri> incoming){
        if(!canEditTimeline()||incoming.isEmpty())return;
        TimelineProject prior=timeline.current();
        importing=true;
        toast("Memeriksa sumber klip…");
        thumbs.execute(()->{
            TimelineProject next=prior;
            int rejected=0;
            for(Uri uri:incoming){
                if(next.clips.size()>=TimelineProject.MAX_CLIPS){rejected++;continue;}
                try{
                    if(!TimelineDraftStore.hasReadGrant(this,uri))
                        getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if(!TimelineDraftStore.hasReadGrant(this,uri)||!MediaProbe.inspect(this,uri).video)
                        throw new IllegalArgumentException("Klip tidak dapat disimpan.");
                    long length=MediaProbe.duration(this,uri);
                    next=next.append(TimelineProject.Clip.create(uri.toString(),length,0,length));
                }catch(Exception unsupported){rejected++;}
            }
            TimelineProject accepted=next;
            int skipped=rejected;
            runOnUiThread(()->{
                if(destroyed)return;
                importing=false;
                editTimeline(accepted);
                if(skipped>0)toast(skipped+" klip dilewati. Periksa izin, format, atau batas 10 klip.");
            });
        });
    }
    private String selectedId(){
        List<TimelineProject.Clip> items=timeline.current().clips;
        return selected>=0&&selected<items.size()?items.get(selected).id:null;
    }
    private boolean editTimeline(TimelineProject next){
        if(next==timeline.current())return true;
        if(!TimelineDraftStore.save(this,next)){toast("Perubahan gagal disimpan.");return false;}
        String id=selectedId();
        timeline.apply(next);
        bindTimeline(id);return true;
    }
    private void seekTimeline(long globalMs){
        if(!canEditTimeline())return;
        TimelineProject project=timeline.current();
        int index=project.clipIndexAt(globalMs);
        if(index<0)return;
        long sourceMs=TimelineNavigation.sourcePosition(project,index,globalMs);
        preview.pause();
        if(index==selected&&trimPlayer!=null){trimPlayer.seekTo(sourceMs,android.media.MediaPlayer.SEEK_CLOSEST);}
        else selectAt(index,sourceMs);
        timelineStrip.position(globalMs,project.duration());
    }
    private void splitAtPlayhead(){
        if(!canEditTimeline()||selected<0)return;
        TimelineProject p=timeline.current();
        long position=timelineStrip.currentPosition();
        if(p.clipIndexAt(position)!=selected){toast("Pilih posisi di dalam klip.");return;}
        try{editTimeline(p.split(selected,TimelineNavigation.sourcePosition(p,selected,position)));}
        catch(IllegalArgumentException ex){toast(ex.getMessage());}
    }
    private void duplicateClip(){
        if(!canEditTimeline()||selected<0)return;
        try{editTimeline(timeline.current().duplicate(selected));}
        catch(IllegalArgumentException ex){toast(ex.getMessage());}
    }
    private void editCutTransition(){
        if(!canEditTimeline()||selected<0||selected+1>=clips.size()){toast("Pilih klip sebelum batas transisi.");return;}
        TimelineProject prior=timeline.current();int index=selected;
        int[] values={0,150,300,500,750,1000};
        String[] labels={"Cut langsung","Crossfade 150 ms","Crossfade 300 ms","Crossfade 500 ms","Crossfade 750 ms","Crossfade 1000 ms"};
        int checked=-1;for(int i=0;i<values.length;i++)if(values[i]==prior.cutMs(index))checked=i;
        final int[] pending={checked};
        new AlertDialog.Builder(this).setTitle("Klip "+(index+1)+" → "+(index+2))
            .setMessage(null).setSingleChoiceItems(labels,checked,(d,i)->pending[0]=i)
            .setNegativeButton("Batal",null).setPositiveButton("Terapkan",(d,w)->{
                if(!canEditTimeline()||timeline.current()!=prior||pending[0]<0)return;
                TimelineProject next=prior.cut(index,values[pending[0]]);
                if(next==prior)return;
                if(next.duration()<prior.duration()||prior.fadeMs>0)new AlertDialog.Builder(this).setTitle("Terapkan transisi per batas?")
                    .setMessage("Mode ini menonaktifkan dip-to-black global. Jika durasi memendek, media tambahan di luar timeline dipangkas. Undo tersedia.")
                    .setNegativeButton("Batal",null).setPositiveButton("Terapkan",(a,z)->{if(canEditTimeline()&&timeline.current()==prior)editTimeline(next);}).show();
                else editTimeline(next);
            }).show();
    }
    private void editFade(){
        if(!canEditTimeline()||clips.size()<2){toast("Tambahkan minimal dua klip.");return;}
        final TimelineProject prior=timeline.current();
        String[] labels={"Cut langsung","Dip-to-black 150 ms","Dip-to-black 300 ms","Dip-to-black 500 ms","Dip-to-black 1000 ms",
                "Crossfade overlap 150 ms (eksperimental)","Crossfade overlap 300 ms (eksperimental)","Crossfade overlap 500 ms (eksperimental)","Crossfade overlap 1000 ms (eksperimental)"};
        int[] choices={0,150,300,500,1000,-150,-300,-500,-1000};int checked=0;
        int active=prior.crossfadeMs>0?-prior.crossfadeMs:prior.fadeMs;
        for(int i=0;i<choices.length;i++)if(choices[i]==active)checked=i;
        final int[] pending={checked};
        new AlertDialog.Builder(this).setTitle("Transisi global (reset pengaturan per batas)")
                .setSingleChoiceItems(labels,checked,(d,i)->pending[0]=i)
                .setNegativeButton("Batal",null).setPositiveButton("Terapkan",(d,w)->{
                    if(canEditTimeline()&&timeline.current()==prior){
                        int choice=choices[pending[0]];
                        TimelineProject next=choice<0?prior.crossfade(-choice):prior.fade(choice);
                        if(next.duration()<prior.duration())new AlertDialog.Builder(this).setTitle("Durasi timeline memendek")
                                .setMessage("Crossfade menumpuk klip. Caption dan audio di luar akhir baru akan dipangkas. Undo tersedia.")
                                .setNegativeButton("Batal",null).setPositiveButton("Terapkan",(confirm,which)->{
                                    if(canEditTimeline()&&timeline.current()==prior)editTimeline(next);
                                }).show();
                        else editTimeline(next);
                    }
                }).show();
    }
    private void pickAudio(){pickTrack(false);}
    private void pickTrack(boolean voice){
        if(!canEditTimeline()||clips.isEmpty()){toast("Tambahkan video sebelum soundtrack.");return;}
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(pick,voice?PICK_VOICE:PICK_AUDIO);}catch(Exception ex){toast("Pemilih audio tidak tersedia.");}
    }
    private void importSoundtrack(Uri uri,boolean voice){
        if(!canEditTimeline()||clips.isEmpty())return;
        TimelineProject prior=timeline.current();importing=true;
        try{
            if(!TimelineDraftStore.hasReadGrant(this,uri))
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(Exception ex){importing=false;toast("Izin audio tidak dapat disimpan.");return;}
        thumbs.execute(()->{
            TimelineProject.Audio candidate=null;
            try{
                SourceAccess.requireReadable(this,uri);
                MediaProbe.Info info=MediaProbe.inspect(this,uri);
                if(info.audio&&info.audioDurationMs>0&&info.audioDurationMs<=TimelineProject.MAX_SOURCE_MS)
                    candidate=new TimelineProject.Audio(uri.toString(),info.audioDurationMs,0,
                            Math.min(info.audioDurationMs,prior.duration()),0,50);
            }catch(Exception ignored){}
            final TimelineProject.Audio result=candidate;
            runOnUiThread(()->{
                importing=false;if(destroyed||timeline.current()!=prior)return;
                if(result==null){toast("Audio tidak valid atau durasi tidak tersedia.");return;}
                showSoundtrack(prior,result,voice);
            });
        });
    }
    private void editSoundtrack(){editTrack(false);}
    private void editTrack(boolean voice){
        if(!canEditTimeline())return;
        TimelineProject prior=timeline.current();
        TimelineProject.Audio audio=voice?prior.voice:prior.audio;
        if(audio==null){pickTrack(voice);return;}
        showSoundtrack(prior,audio,voice);
    }
    private void showSoundtrack(TimelineProject prior,TimelineProject.Audio audio,boolean voice){
        preview.pause();LinearLayout form=StudioUi.card(this,16);
        form.addView(txt("Audio independen • hasil mix tersedia di Preview Timeline",12,StudioUi.MUTED,false));
        EditText in=numberField(form,"Mulai sumber audio (ms)",audio.in);
        EditText out=numberField(form,"Akhir sumber audio (ms)",audio.out);
        EditText start=numberField(form,"Posisi di timeline (ms)",audio.start);
        EditText volume=numberField(form,"Volume audio (1–100%)",audio.volume);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog panel=new AlertDialog.Builder(this).setTitle(voice?"Voice track":"Soundtrack").setView(scroll)
                .setNegativeButton("Batal",null).setNeutralButton("Hapus",null).setPositiveButton("Terapkan",null).show();
        panel.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!canEditTimeline()||timeline.current()!=prior)return;
            try{
                TimelineProject.Audio next=new TimelineProject.Audio(audio.uri,audio.duration,
                        Long.parseLong(in.getText().toString()),Long.parseLong(out.getText().toString()),
                        Long.parseLong(start.getText().toString()),Integer.parseInt(volume.getText().toString()));
                if(editTimeline(voice?prior.voiceTrack(next):prior.soundtrack(next)))panel.dismiss();
            }catch(IllegalArgumentException ex){toast("Periksa rentang dan volume. "+ex.getMessage());}
        });
        panel.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(canEditTimeline()&&timeline.current()==prior&&editTimeline(voice?prior.voiceTrack(null):prior.soundtrack(null)))panel.dismiss();
        });
    }
    private void pickPicture(){
        if(!canEditTimeline()||clips.isEmpty()){toast("Tambahkan video utama dahulu.");return;}
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(pick,PICK_PICTURE);}catch(Exception ex){toast("Pemilih video tidak tersedia.");}
    }
    private void importPicture(Uri uri){
        if(!canEditTimeline()||clips.isEmpty())return;
        TimelineProject prior=timeline.current();importing=true;
        try{if(!TimelineDraftStore.hasReadGrant(this,uri))getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception ex){importing=false;toast("Izin PiP tidak dapat disimpan.");return;}
        thumbs.execute(()->{
            PictureLayer candidate=null;
            try{
                SourceAccess.requireReadable(this,uri);MediaProbe.Info info=MediaProbe.inspect(this,uri);
                long duration=MediaProbe.duration(this,uri);
                if(info.video&&!info.isHdr()&&duration>0&&duration<=TimelineProject.MAX_SOURCE_MS)
                    candidate=new PictureLayer(uri.toString(),duration,0,Math.min(duration,prior.duration()),0,35,100,90,10,90,10);
            }catch(Exception ignored){}
            PictureLayer ready=candidate;
            runOnUiThread(()->{importing=false;if(destroyed||timeline.current()!=prior)return;
                if(ready==null){toast("Video PiP SDR tidak valid.");return;}showPicture(prior,ready);});
        });
    }
    private void editPicture(){
        if(!canEditTimeline())return;
        TimelineProject prior=timeline.current();if(prior.picture==null){pickPicture();return;}showPicture(prior,prior.picture);
    }
    private void showPicture(TimelineProject prior,PictureLayer picture){
        preview.pause();LinearLayout form=StudioUi.card(this,16);
        form.addView(txt("Layer video bisu independen. Posisi 0–100 dari kiri/atas ke kanan/bawah. Gunakan Preview Timeline.",12,StudioUi.MUTED,false));
        EditText in=numberField(form,"PiP mulai sumber (ms)",picture.in);
        EditText out=numberField(form,"PiP akhir sumber (ms)",picture.out);
        EditText start=numberField(form,"PiP posisi timeline (ms)",picture.start);
        EditText scale=numberField(form,"PiP ukuran (20–60%)",picture.scale);
        EditText opacity=numberField(form,"PiP opacity (25–100%)",picture.opacity);
        EditText xs=numberField(form,"PiP X awal (0–100)",picture.xStart);
        EditText ys=numberField(form,"PiP Y awal (0–100)",picture.yStart);
        EditText xe=numberField(form,"PiP X akhir (0–100)",picture.xEnd);
        EditText ye=numberField(form,"PiP Y akhir (0–100)",picture.yEnd);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog panel=new AlertDialog.Builder(this).setTitle("Picture-in-picture").setView(scroll)
                .setNegativeButton("Batal",null).setNeutralButton("Hapus",null).setPositiveButton("Terapkan",null).show();
        panel.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!canEditTimeline()||timeline.current()!=prior)return;
            try{
                PictureLayer next=new PictureLayer(picture.uri,picture.duration,Long.parseLong(in.getText().toString()),
                    Long.parseLong(out.getText().toString()),Long.parseLong(start.getText().toString()),Integer.parseInt(scale.getText().toString()),
                    Integer.parseInt(opacity.getText().toString()),Integer.parseInt(xs.getText().toString()),Integer.parseInt(ys.getText().toString()),
                    Integer.parseInt(xe.getText().toString()),Integer.parseInt(ye.getText().toString()));
                if(editTimeline(prior.picture(next)))panel.dismiss();
            }catch(IllegalArgumentException ex){toast("Periksa durasi, ukuran, posisi dan opacity PiP.");}
        });
        panel.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(canEditTimeline()&&timeline.current()==prior&&editTimeline(prior.picture(null)))panel.dismiss();
        });
    }
    private void editKeyframes(){
        if(!canEditTimeline()||selected<0)return;
        final int index=selected;final TimelineProject prior=timeline.current();
        TimelineProject.Clip clip=prior.clips.get(index);
        LinearLayout form=StudioUi.card(this,16);
        form.addView(txt("Animasi pencahayaan dan zoom linear. Hasil di Preview Timeline.",12,StudioUi.MUTED,false));
        EditText first=numberField(form,"Gain awal (25–150%)",clip.gainStart);
        EditText last=numberField(form,"Gain akhir (25–150%)",clip.gainEnd);
        EditText zoomFirst=numberField(form,"Zoom awal (50–200%)",clip.zoomStart);
        EditText zoomLast=numberField(form,"Zoom akhir (50–200%)",clip.zoomEnd);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog panel=new AlertDialog.Builder(this).setTitle("Keyframe").setView(scroll)
                .setNegativeButton("Batal",null).setPositiveButton("Terapkan",null).show();
        panel.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!canEditTimeline()||timeline.current()!=prior)return;
            try{if(editTimeline(prior.keyframes(index,Integer.parseInt(first.getText().toString()),
                    Integer.parseInt(last.getText().toString())).zoom(index,Integer.parseInt(zoomFirst.getText().toString()),
                    Integer.parseInt(zoomLast.getText().toString()))))panel.dismiss();}
            catch(IllegalArgumentException ex){toast("Gain 25–150%, zoom 50–200%.");}
        });
    }
    private void editClipAdjustments(){
        if(!canEditTimeline()||selected<0||selected>=timeline.current().clips.size())return;
        final String id=selectedId();final int index=selected;
        preview.pause();
        ClipAdjustmentPanel.show(this,index,timeline.current().clips.get(index).adjustments,next->{
            if(!canEditTimeline()||!id.equals(selectedId()))return false;
            return editTimeline(timeline.current().adjust(index,next));
        });
    }
    private void bindTimeline(String id){
        syncTimelineClips();
        selected=-1;
        for(int i=0;i<timeline.current().clips.size();i++)
            if(timeline.current().clips.get(i).id.equals(id)){selected=i;break;}
        for(int i=0;i<qualityButtons.length;i++)if(qualityButtons[i]!=null)
            qualityButtons[i].setBackground(i==quality?
                    StudioUi.gradient(this,0xFF9A57E9,0xFF3B7EEC,12):StudioUi.solid(this,StudioUi.INNER,12));
        if(clips.isEmpty()){preview.stopPlayback();trimPlayer=null;renderList();}
        else select(selected<0?0:selected);
    }
    private void historyStep(boolean redo){
        if(!canEditTimeline()||(redo?!timeline.canRedo():!timeline.canUndo()))return;
        String id=selectedId();
        TimelineProject target=redo?timeline.redoTarget():timeline.undoTarget();
        if(!TimelineDraftStore.save(this,target)){toast("Perubahan gagal disimpan.");return;}
        if(redo)timeline.redo();else timeline.undo();
        bindTimeline(id);
    }
    private void resetTimeline(){
        if(working||importing)return;
        new AlertDialog.Builder(this).setTitle("Kosongkan timeline?")
                .setMessage("Media asli dan draft satu media tidak dihapus.")
                .setNegativeButton("Batal",null)
                .setPositiveButton("Reset",(dialog,w)->{
                    TimelineProject empty=TimelineProject.empty();
                    if(!TimelineDraftStore.save(this,empty)){toast("Timeline gagal dikosongkan.");return;}
                    if(unreadableDraft){unreadableDraft=false;timeline=new TimelineProject.History(empty);}
                    else timeline.apply(empty);
                    bindTimeline(null);
                }).show();
    }
    private EditText numberField(LinearLayout layout,String label,long value){
        layout.addView(txt(label,12,StudioUi.TEXT,true));
        EditText input=new EditText(this);
        input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        input.setText(String.valueOf(value));input.setContentDescription(label);
        layout.addView(input,new LinearLayout.LayoutParams(-1,-2));
        return input;
    }
    private void saveDialog(String title,LinearLayout card,Runnable save,Runnable remove){
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(title).setView(card)
                .setNegativeButton("Batal",null).setPositiveButton("Simpan",null);
        if(remove!=null)builder.setNeutralButton("Hapus",(d,w)->remove.run());
        AlertDialog form=builder.show();
        form.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{save.run();form.dismiss();}
            catch(IllegalArgumentException invalid){toast("Periksa nilai. "+invalid.getMessage());}
        });
    }
    private void editTrim(){
        if(!canEditTimeline()||selected<0)return;
        int index=selected;
        TimelineProject.Clip segment=timeline.current().clips.get(index);
        LinearLayout form=StudioUi.card(this,16);
        EditText in=numberField(form,"Mulai klip (ms)",segment.in);
        EditText out=numberField(form,"Akhir, maksimal "+segment.duration+" ms",segment.out);
        saveDialog("Trim klip "+(index+1),form,()->editTimeline(timeline.current().trim(index,
                Long.parseLong(in.getText().toString()),Long.parseLong(out.getText().toString()))),null);
    }
    private void editTimedCaption(){
        if(!canEditTimeline()||clips.isEmpty())return;
        TimelineProject project=timeline.current();
        TimelineProject.Caption prior=project.caption;
        LinearLayout form=StudioUi.card(this,16);
        EditText copy=new EditText(this);
        copy.setHint("Caption (maks. 80 karakter)");copy.setSingleLine(true);
        copy.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        if(prior!=null)copy.setText(prior.text);
        form.addView(copy,new LinearLayout.LayoutParams(-1,-2));
        EditText in=numberField(form,"Mulai di timeline (ms)",prior==null?0:prior.in);
        EditText out=numberField(form,"Selesai, maksimal "+project.duration()+" ms",prior==null?project.duration():prior.out);
        saveDialog("Caption waktu",form,()->editTimeline(timeline.current().caption(new TimelineProject.Caption(
                ExportOptions.normalizeCaption(copy.getText().toString()),
                Long.parseLong(in.getText().toString()),Long.parseLong(out.getText().toString()),
                prior==null?0xffffd786:prior.color))),()->editTimeline(timeline.current().caption(null)));
    }
    private final Runnable trimPreviewTicker=new Runnable(){
        @Override public void run(){
            if(destroyed)return;
            if(selected>=0&&selected<clips.size()&&preview.isPlaying()){
                TimelineProject.Clip segment=timeline.current().clips.get(selected);
                if(timelineStrip!=null)timelineStrip.position(timeline.current().startOf(selected)+
                        Math.max(0,Math.min(segment.length(),preview.getCurrentPosition()-segment.in)),timeline.current().duration());
                if(preview.getCurrentPosition()>=segment.out){
                    preview.pause();
                    if(trimPlayer!=null)trimPlayer.seekTo(segment.in,android.media.MediaPlayer.SEEK_CLOSEST);
                    if(timelineStrip!=null)timelineStrip.position(timeline.current().startOf(selected),timeline.current().duration());
                }
            }
            main.postDelayed(this,100);
        }
    };
    @Override protected void onPause(){
        super.onPause();
        if(preview!=null)preview.pause();
        if(renderedPreview!=null)renderedPreview.pause();
    }
    @Override protected void onDestroy(){
        destroyed=true;mergeStop.set(true);main.removeCallbacksAndMessages(null);
        if(engine!=null&&working)engine.cancel();
        if(renderedPreview!=null)renderedPreview.close();
        if(timelineStrip!=null)timelineStrip.close();
        // Let a queued writer release its transferred file after seeing cancellation.
        thumbs.shutdown();
        if(preview!=null)preview.stopPlayback();
        if(dialog!=null)dialog.dismiss();
        if(mergeResultDialog!=null)mergeResultDialog.dismiss();
        super.onDestroy();
    }
}
