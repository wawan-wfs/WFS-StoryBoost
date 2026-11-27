package com.wfs.storyboost;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.DefaultGainProvider;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.util.Size;
import androidx.media3.effect.MatrixTransformation;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbMatrix;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Experimental two-lane overlap compositor. Padding is generated black, never user media.
 * Video lanes remain continuous for the whole output; the top lane is alpha-animated
 * in global time. Original clip audio is independently ramped in matching audio lanes.
 */
@SuppressWarnings("UnsafeOptInUsageError")
final class OverlapComposition {
    private OverlapComposition(){}
    private static synchronized Uri black(Context context)throws java.io.IOException{
        File image=new File(context.getCacheDir(),"wfs-overlap-black-v1.png");
        if(image.isFile()&&image.length()>0)return Uri.fromFile(image);
        File staging=File.createTempFile("wfs-overlap-black-",".tmp",context.getCacheDir());
        Bitmap pixel=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        pixel.eraseColor(android.graphics.Color.BLACK);
        try{
            try(FileOutputStream out=new FileOutputStream(staging)){
                if(!pixel.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Black padding encode failed");
            }
            if(!staging.renameTo(image))throw new java.io.IOException("Black padding commit failed");
            return Uri.fromFile(image);
        }finally{pixel.recycle();staging.delete();}
    }
    private static MediaItem media(TimelineProject.Clip clip){
        return new MediaItem.Builder().setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.in).setEndPositionMs(clip.out).build()).build();
    }
    private static Effect canvas(int width,int height){
        return Presentation.createForWidthAndHeight(width,height,Presentation.LAYOUT_SCALE_TO_FIT);
    }
    private static EditedMediaItem pad(Uri black,long duration,int width,int height,int fps){
        MediaItem item=new MediaItem.Builder().setUri(black).setImageDurationMs(duration).build();
        return new EditedMediaItem.Builder(item).setFrameRate(fps).setDurationUs(duration*1000L)
                .setEffects(new Effects(Collections.emptyList(),Collections.singletonList(canvas(width,height)))).build();
    }
    static List<EditedMediaItemSequence> sequences(Context context,TimelineProject draft,boolean[] originalAudio,
                                                    int width,int height,int fps)throws java.io.IOException{
        Uri black=black(context);OverlapPlan plan=draft.overlap;
        List<EditedMediaItemSequence> result=new ArrayList<>();
        // inputId 0 is drawn above inputId 1 by the Media3 compositor.
        for(int lane=0;lane<2;lane++){
            EditedMediaItemSequence.Builder sequence=new EditedMediaItemSequence.Builder(Collections.singleton(C.TRACK_TYPE_VIDEO));
            long cursor=0;
            for(int i=lane;i<draft.clips.size();i+=2){
                if(plan.starts[i]>cursor)sequence.addItem(pad(black,plan.starts[i]-cursor,width,height,fps));
                TimelineProject.Clip clip=draft.clips.get(i);
                List<Effect> effects=new ArrayList<>();
                VideoColorEffects.append(effects,clip.adjustments);
                effects.add(canvas(width,height));
                if(clip.zoomStart!=100||clip.zoomEnd!=100)effects.add((MatrixTransformation)timeUs->{
                    Matrix m=new Matrix();float scale=clip.zoomAt(clip.in*1000L+timeUs);m.setScale(scale,scale);return m;
                });
                if(clip.gainStart!=100||clip.gainEnd!=100)effects.add((RgbMatrix)(timeUs,useHdr)->{
                    float g=clip.gainAt(clip.in*1000L+timeUs);
                    return new float[]{g,0,0,0,0,g,0,0,0,0,g,0,0,0,0,1};
                });
                sequence.addItem(new EditedMediaItem.Builder(media(clip)).setRemoveAudio(true)
                        .setEffects(new Effects(Collections.emptyList(),effects)).build());
                cursor=plan.ends[i];
            }
            if(cursor<plan.duration)sequence.addItem(pad(black,plan.duration-cursor,width,height,fps));
            result.add(sequence.build());
        }
        // Alternating audio lanes use explicit gaps and independent linear ramps.
        for(int lane=0;lane<2;lane++){
            boolean any=false;for(int i=lane;i<originalAudio.length;i+=2)any|=originalAudio[i];
            if(!any)continue;
            EditedMediaItemSequence.Builder sequence=new EditedMediaItemSequence.Builder(Collections.singleton(C.TRACK_TYPE_AUDIO));
            long cursor=0;
            for(int i=lane;i<draft.clips.size();i+=2){
                if(!originalAudio[i])continue;
                if(plan.starts[i]>cursor)sequence.addGap((plan.starts[i]-cursor)*1000L);
                TimelineProject.Clip clip=draft.clips.get(i);
                DefaultGainProvider.Builder ramp=new DefaultGainProvider.Builder(1f);
                if(plan.fadeIn(i)>0)ramp.addFadeAt(0,plan.fadeIn(i)*1000L,DefaultGainProvider.FADE_IN_LINEAR);
                if(plan.fadeOut(i)>0)ramp.addFadeAt((clip.length()-plan.fadeOut(i))*1000L,
                        plan.fadeOut(i)*1000L,DefaultGainProvider.FADE_OUT_LINEAR);
                List<AudioProcessor> processors=Collections.singletonList(new GainProcessor(ramp.build()));
                sequence.addItem(new EditedMediaItem.Builder(media(clip)).setRemoveVideo(true)
                        .setEffects(new Effects(processors,Collections.emptyList())).build());
                cursor=plan.ends[i];
            }
            if(cursor<plan.duration)sequence.addGap((plan.duration-cursor)*1000L);
            result.add(sequence.build());
        }
        return result;
    }
    static EditedMediaItemSequence pictureSequence(Context context,PictureLayer layer,long total,int width,int height,int fps)throws java.io.IOException{
        Uri padding=black(context);
        EditedMediaItemSequence.Builder sequence=new EditedMediaItemSequence.Builder(Collections.singleton(C.TRACK_TYPE_VIDEO));
        if(layer.start>0)sequence.addItem(pad(padding,layer.start,width,height,fps));
        MediaItem media=new MediaItem.Builder().setUri(Uri.parse(layer.uri))
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(layer.in).setEndPositionMs(layer.out).build()).build();
        sequence.addItem(new EditedMediaItem.Builder(media).setRemoveAudio(true)
                .setEffects(new Effects(Collections.emptyList(),Collections.singletonList(canvas(width,height)))).build());
        if(layer.end()<total)sequence.addItem(pad(padding,total-layer.end(),width,height,fps));
        return sequence.build();
    }
    static VideoCompositorSettings settings(OverlapPlan plan,int width,int height,PictureLayer picture,boolean crossfade){
        return new VideoCompositorSettings(){
            @Override public Size getOutputSize(List<Size> inputs){return new Size(width,height);}
            @Override public OverlaySettings getOverlaySettings(int inputId,long timeUs){
                if(picture!=null&&inputId==0){
                    float scale=picture.scale/100f;
                    return new StaticOverlaySettings.Builder().setScale(scale,scale)
                            .setBackgroundFrameAnchor(picture.anchorX(timeUs),picture.anchorY(timeUs))
                            .setAlphaScale(picture.visible(timeUs)?picture.opacity/100f:0f).build();
                }
                int baseId=inputId-(picture==null?0:1);
                return new StaticOverlaySettings.Builder().setAlphaScale(crossfade&&baseId==0?plan.alphaTop(timeUs):1f).build();
            }
        };
    }
}
