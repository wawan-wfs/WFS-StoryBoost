// SPDX-License-Identifier: MIT
package com.wfs.storyboost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

/** Opt-in, local-only bounded preview. No production export, no file writes or photo persistence. */
public final class RestorationLabActivity extends Activity {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private Future<?> pending;private int generation;private boolean closed;
    private TextView status;private ImageView before,after;private Button deblur,sr,demo;
    private android.widget.CheckBox refined;
    private int dp(int n){return StudioUi.dp(this,n);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(StudioUi.BG);getWindow().setNavigationBarColor(StudioUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(StudioUi.BG);
        LinearLayout root=StudioUi.column(this);root.setPadding(dp(18),dp(20),dp(18),dp(24));scroll.addView(root);setContentView(scroll);
        Button back=StudioUi.button(this,"‹ Kembali ke Studio",false);back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
        StudioUi.gap(root,this,14);root.addView(StudioUi.text(this,"RESTORATION LAB",11,StudioUi.CYAN,true));
        root.addView(StudioUi.text(this,"AI Deblur & SR 2×",25,StudioUi.TEXT,true));
        StudioUi.gap(root,this,8);root.addView(StudioUi.text(this,"Eksperimen neural asli · offline · preview maksimal 512 px. Dilatih dengan gambar sintetis, bukan model foto produksi.",13,StudioUi.MUTED,false));
        StudioUi.gap(root,this,14);
        refined=new android.widget.CheckBox(this);refined.setText("Gunakan model refined (eksperimental)");refined.setTextColor(StudioUi.TEXT);refined.setChecked(true);root.addView(refined,new LinearLayout.LayoutParams(-1,dp(48)));
        deblur=StudioUi.button(this,"Pilih foto · Deblur",true);sr=StudioUi.button(this,"Pilih foto · SR 2×",true);demo=StudioUi.button(this,"Uji demo sintetis SR 2×",false);
        for(Button button:new Button[]{deblur,sr,demo}){root.addView(button,new LinearLayout.LayoutParams(-1,dp(52)));StudioUi.gap(root,this,7);}
        deblur.setOnClickListener(v->pick(false));sr.setOnClickListener(v->pick(true));demo.setOnClickListener(v->run(null,true));
        status=StudioUi.text(this,"Pilih foto untuk preview lokal. Media asli tidak diubah.",13,StudioUi.GREEN,false);root.addView(status);
        StudioUi.gap(root,this,12);LinearLayout panels=StudioUi.row(this);
        before=panel(panels,"Input / bilinear");after=panel(panels,"Output neural");
        root.addView(panels);StudioUi.gap(root,this,14);
        root.addView(StudioUi.text(this,"Hasil tidak disimpan. Preview bukan bukti restorasi detail nyata.\nby WFS Project",12,StudioUi.MUTED,false));
    }
    private ImageView panel(LinearLayout row,String title){
        LinearLayout box=StudioUi.card(this,9);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),0,dp(2),0);row.addView(box,p);
        box.addView(StudioUi.text(this,title,12,StudioUi.TEXT,true));ImageView image=new ImageView(this);
        image.setContentDescription(title);image.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(image,new LinearLayout.LayoutParams(-1,dp(180)));return image;
    }
    private void pick(boolean upscale){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,upscale?202:201);}catch(Exception e){status.setText("Pemilih foto tidak tersedia.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result==RESULT_OK&&(request==201||request==202)&&data!=null&&data.getData()!=null)run(data.getData(),request==202);
    }
    private Bitmap read(Uri uri,boolean upscale)throws Exception{
        int limit=upscale?256:512;
        if(uri==null){int n=64;int[] pixels=new int[n*n];for(int y=0;y<n;y++)for(int x=0;x<n;x++){int g=(x/8+y/8)%2==0?64:192;pixels[y*n+x]=Color.rgb(g,g,g);}return Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888);}
        if(!"content".equals(uri.getScheme()))throw new java.io.IOException("Invalid source scheme");
        SourceAccess.requireReadable(this,uri);
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            if(w<=0||h<=0||(long)w*h>100000000L)throw new IllegalArgumentException("Ukuran foto terlalu besar");
            float scale=Math.min(1f,limit/(float)Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
    private static int channel(int v){return Math.max(0,Math.min(255,v));}
    private void run(Uri uri,boolean upscale){
        final boolean useRefined=refined.isChecked();
        final int token=++generation;if(pending!=null)pending.cancel(true);worker.purge();
        before.setImageDrawable(null);after.setImageDrawable(null);status.setText("Inferensi CPU berlangsung…");
        pending=worker.submit(()->{
            Bitmap input=null,baseline=null,output=null;boolean handed=false;
            try{
                input=read(uri,upscale);
                baseline=upscale?Bitmap.createScaledBitmap(input,input.getWidth()*2,input.getHeight()*2,true):input;
                int w=baseline.getWidth(),h=baseline.getHeight();int[] rgba=new int[w*h];baseline.getPixels(rgba,0,w,0,0,w,h);float[] y=new float[rgba.length];
                for(int i=0;i<y.length;i++)y[i]=Math.max(0f,Math.min(1f,(.2126f*Color.red(rgba[i])+.7152f*Color.green(rgba[i])+.0722f*Color.blue(rgba[i]))/255f));
                RestorationLab model;
                try(InputStream stream=getAssets().open("restoration-lab/"+(upscale?"sr2x":"deblur")+(useRefined?"-refined":"")+".weights.txt")){model=RestorationLab.load(stream,upscale,useRefined);}
                long start=SystemClock.elapsedRealtimeNanos();float[] prediction=model.infer(y,w,h);long ms=(SystemClock.elapsedRealtimeNanos()-start)/1000000L;
                for(int i=0;i<rgba.length;i++){int delta=Math.round((prediction[i]-y[i])*255);int old=rgba[i];
                    rgba[i]=Color.argb(Color.alpha(old),channel(Color.red(old)+delta),channel(Color.green(old)+delta),channel(Color.blue(old)+delta));}
                output=Bitmap.createBitmap(rgba,w,h,Bitmap.Config.ARGB_8888);
                if(uri!=null)SourceAccess.requireReadable(this,uri);
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                Bitmap left=baseline,right=output;handed=true;
                runOnUiThread(()->{
                    if(closed||generation!=token){left.recycle();right.recycle();return;}
                    before.setImageBitmap(left);after.setImageBitmap(right);
                    status.setText("Preview neural siap · "+(useRefined?"Refined":"Original")+" · "+w+" × "+h+" · "+ms+" ms CPU");
                });
            }catch(Exception|OutOfMemoryError ex){
                runOnUiThread(()->{if(!closed&&generation==token)status.setText("Preview gagal atau dibatalkan. Media asli tetap aman.");});
            }finally{
                if(input!=null&&input!=baseline)input.recycle();
                if(!handed){if(baseline!=null)baseline.recycle();if(output!=null)output.recycle();}
            }
        });
    }
    @Override protected void onDestroy(){closed=true;++generation;if(pending!=null)pending.cancel(true);worker.shutdownNow();super.onDestroy();}
}
