package com.wfs.storyboost;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Effect;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Composition editor with optional independent soundtrack and non-overlap dip-to-black. */
@SuppressWarnings("UnsafeOptInUsageError")
public final class VideoMergeEngine {
    static final String PREVIEW_PREFIX=TimelinePreviewCache.PREFIX;
    public interface Listener {
        void onCompleted(File output);
        void onFailure(String message);
    }
    private Transformer transformer;
    private File intermediate;
    private boolean cancelled;
    public void start(Context context,List<Uri> videos,int quality,Listener listener){
        if(videos==null||videos.isEmpty()||videos.size()>10)
            throw new IllegalArgumentException("Pilih 1 sampai 10 video");
        cancelled=false;
        final List<Uri> sources=new ArrayList<>(videos);
        ArrayList<EditedMediaItem> items=new ArrayList<>();
        AspectRatioProfile.Size first;
        try{first=MediaDimensions.read(context,videos.get(0));}
        catch(Exception e){throw new IllegalArgumentException("Dimensi video pertama tidak tersedia",e);}
        int longest=quality==OutputProfile.HD?1280:
                quality==OutputProfile.FULL_HD?1920:3840;
        float factor=Math.min(1f,longest/(float)Math.max(first.width,first.height));
        int width=Math.max(2,(int)(Math.round(first.width*factor)/2)*2);
        int height=Math.max(2,(int)(Math.round(first.height*factor)/2)*2);
        for(Uri video:sources){
            try{
                if(!MediaProbe.inspect(context,video).video)
                    throw new IllegalArgumentException("Video tidak memiliki track yang valid");
            }catch(Exception ex){throw new IllegalArgumentException("Sumber tidak didukung: "+ex.getMessage(),ex);}
            items.add(new EditedMediaItem.Builder(MediaItem.fromUri(video)).build());
        }
        EditedMediaItemSequence sequence=EditedMediaItemSequence.withAudioAndVideoFrom(items);
        Composition composition=new Composition.Builder(sequence)
                .setEffects(new Effects(Collections.emptyList(),Collections.singletonList(
                        Presentation.createForWidthAndHeight(width,height,
                                Presentation.LAYOUT_SCALE_TO_FIT))))
                .build();
        encode(context,sources,composition,-1,false,false,width,height,0,"wfs_join_",listener);
    }

    /** Timeline export uses trimmed clips and composition-global caption time. */
    void startTimeline(Context context,TimelineProject draft,Listener listener){
        startTimeline(context,draft,"wfs_join_",listener);
    }
    void startTimelinePreview(Context context,TimelineProject draft,Listener listener){
        startTimeline(context,draft,PREVIEW_PREFIX,listener);
    }
    private void startTimeline(Context context,TimelineProject draft,String filePrefix,Listener listener){
        if(draft==null||draft.clips.isEmpty())throw new IllegalArgumentException("Timeline kosong.");
        ArrayList<Uri> sources=new ArrayList<>();
        ArrayList<EditedMediaItem> items=new ArrayList<>();
        boolean hasAudio=false,hasHdr=false;
        float maxFps=0f;
        AspectRatioProfile.Size canvas;
        try{
            Uri first=Uri.parse(draft.clips.get(0).uri);
            SourceAccess.requireReadable(context,first);
            AspectRatioProfile.Size original=MediaDimensions.video(context,first);
            int limit=draft.quality==OutputProfile.HD?1280:
                    draft.quality==OutputProfile.FULL_HD?1920:3840;
            float scale=Math.min(1f,limit/(float)Math.max(original.width,original.height));
            canvas=new AspectRatioProfile.Size(Math.max(2,Math.round(original.width*scale/2)*2),
                    Math.max(2,Math.round(original.height*scale/2)*2));
            for(TimelineProject.Clip clip:draft.clips){
                Uri uri=Uri.parse(clip.uri);
                SourceAccess.requireReadable(context,uri);
                MediaProbe.Info input=MediaProbe.inspect(context,uri);
                long duration=MediaProbe.duration(context,uri);
                if(!input.video||duration<=0||clip.out>duration)
                    throw new IllegalArgumentException("Media atau trim klip "+(sources.size()+1)+" berubah.");
                hasAudio|=clip.adjustments.keepsAudio(input.audio);
                hasHdr|=input.isHdr();maxFps=Math.max(maxFps,input.fps);
                sources.add(uri);
                MediaItem media=new MediaItem.Builder().setUri(uri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clip.in).setEndPositionMs(clip.out).build()).build();
                List<Effect> frame=new ArrayList<>();
                VideoColorEffects.append(frame,clip.adjustments);
                frame.add(Presentation.createForWidthAndHeight(canvas.width,canvas.height,Presentation.LAYOUT_SCALE_TO_FIT));
                items.add(new EditedMediaItem.Builder(media)
                        .setRemoveAudio(clip.adjustments.mute)
                        .setEffects(new Effects(Collections.emptyList(),frame)).build());
            }
        }catch(Exception problem){
            throw new IllegalArgumentException("Periksa akses dan trim sumber timeline. "+
                    (problem.getMessage()==null?"":problem.getMessage()),problem);
        }
        EditedMediaItemSequence sequence=hasAudio?EditedMediaItemSequence.withAudioAndVideoFrom(items)
                :EditedMediaItemSequence.withVideoFrom(items);
        ArrayList<EditedMediaItemSequence> sequences=new ArrayList<>();sequences.add(sequence);
        for(TimelineProject.Audio audio:new TimelineProject.Audio[]{draft.audio,draft.voice}){
            if(audio==null)continue;
            Uri uri=Uri.parse(audio.uri);
            try{
                SourceAccess.requireReadable(context,uri);
                MediaProbe.Info input=MediaProbe.inspect(context,uri);
                if(!input.audio||input.audioDurationMs<audio.out)
                    throw new IllegalArgumentException("Sumber soundtrack berubah atau tidak memiliki audio.");
                sources.add(uri);
                MediaItem media=new MediaItem.Builder().setUri(uri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(audio.in).setEndPositionMs(audio.out).build()).build();
                androidx.media3.common.audio.GainProcessor gain=new androidx.media3.common.audio.GainProcessor(
                        new androidx.media3.common.audio.DefaultGainProvider.Builder(audio.volume/100f).build());
                EditedMediaItem sound=new EditedMediaItem.Builder(media).setRemoveVideo(true)
                        .setEffects(new Effects(Collections.singletonList(gain),Collections.emptyList())).build();
                EditedMediaItemSequence.Builder lane=new EditedMediaItemSequence.Builder(
                        Collections.singleton(androidx.media3.common.C.TRACK_TYPE_AUDIO));
                if(audio.start>0)lane.addGap(audio.start*1000L);
                lane.addItem(sound);
                if(audio.end()<draft.duration())lane.addGap((draft.duration()-audio.end())*1000L);
                sequences.add(lane.build());hasAudio=true;
            }catch(Exception ex){throw new IllegalArgumentException("Soundtrack tidak dapat dibaca. "+ex.getMessage(),ex);}
        }
        Composition.Builder builder=new Composition.Builder(sequences);
        List<Effect> globalEffects=new ArrayList<>();
        if(draft.hasZoom())globalEffects.add((androidx.media3.effect.MatrixTransformation)timeUs->{
            android.graphics.Matrix matrix=new android.graphics.Matrix();float scale=draft.zoomAtUs(timeUs);matrix.setScale(scale,scale);return matrix;
        });
        if(draft.hasKeyframes()||(draft.fadeMs>0&&draft.clips.size()>1)){
            globalEffects.add((androidx.media3.effect.RgbMatrix)(timeUs,useHdr)->{
                float g=draft.fadeGain(timeUs)*draft.gainAtUs(timeUs);
                return new float[]{g,0,0,0,0,g,0,0,0,0,g,0,0,0,0,1};
            });
        }
        if(hasHdr)builder.setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
        if(draft.caption!=null){
            ExportOptions style=new ExportOptions(ColorLooks.ORIGINAL,AspectRatioProfile.ORIGINAL,
                    AspectRatioProfile.FIT,false).withCaption(draft.caption.text,draft.caption.color);
            globalEffects.add(new OverlayEffect(Collections.singletonList(new VideoCaptionOverlay(style,draft.caption))));
        }
        if(!globalEffects.isEmpty())builder.setEffects(new Effects(Collections.emptyList(),globalEffects));
        int bitrate=VideoBitrate.estimate(canvas.width,canvas.height,draft.quality,maxFps);
        encode(context,sources,builder.build(),draft.duration(),hasAudio,hasHdr,canvas.width,canvas.height,bitrate,filePrefix,listener);
    }

    private void encode(Context context,List<Uri> sources,Composition composition,long expectedMs,
                        boolean expectedAudio,boolean sourceHdr,int width,int height,int bitrate,String filePrefix,Listener listener){
        cancelled=false;
        intermediate=new File(context.getCacheDir(),filePrefix+java.util.UUID.randomUUID()+".mp4");
        final File attemptFile=intermediate;
        Transformer.Builder encoder=new Transformer.Builder(context);
        if(bitrate>0)encoder.setEncoderFactory(new androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(new androidx.media3.transformer.VideoEncoderSettings.Builder()
                        .setBitrate(bitrate).setiFrameIntervalSeconds(2f).build()).build());
        transformer=encoder
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition result,ExportResult export){
                        if(cancelled||intermediate!=attemptFile)return;
                        try{
                            for(Uri source:sources)SourceAccess.requireReadable(context,source);
                            if(!intermediate.isFile()||intermediate.length()<1024)
                                throw new java.io.IOException("Gabungan video kosong");
                            MediaProbe.Info info=MediaProbe.inspect(intermediate);
                            if(!info.video||info.durationMs<=0)
                                throw new java.io.IOException("Output video tidak terbaca");
                            if(expectedMs>0){
                                long outputMs=info.durationMs;
                                if(Math.abs(outputMs-expectedMs)>Math.max(250,expectedMs/200)||
                                        info.videoDurationMs<=0||info.audio!=expectedAudio||
                                        (expectedAudio&&(info.audioDurationMs<=0||
                                        Math.abs(info.videoDurationMs-info.audioDurationMs)>250))||
                                        (sourceHdr&&info.isHdr()))
                                    throw new java.io.IOException("Durasi, audio, atau warna output timeline tidak sesuai.");
                                AspectRatioProfile.Size actual=MediaDimensions.video(intermediate);
                                if(actual.width!=width||actual.height!=height)
                                    throw new java.io.IOException("Ukuran output timeline tidak sesuai.");
                            }
                            listener.onCompleted(intermediate);
                        }catch(Exception ex){
                            clean();
                            listener.onFailure(ex.getMessage());
                        }
                    }
                    @Override public void onError(Composition result,ExportResult export,
                                                   ExportException ex){
                        if(cancelled||intermediate!=attemptFile)return;
                        String message=VideoEngine.describeFailure(ex);
                        clean();
                        listener.onFailure(message);
                    }
                }).build();
        transformer.start(composition,intermediate.getAbsolutePath());
    }
    /**
     * Transfer a completed MP4 to its Gallery worker or private player on the main thread.
     * The recipient is then the only owner allowed to delete it.
     */
    void transferCompletedFile(File file) {
        if (!file.equals(intermediate)) throw new IllegalStateException("Export file ownership changed");
        intermediate = null;
        transformer = null;
    }

    public int progress(){
        if(transformer==null)return -1;
        ProgressHolder h=new ProgressHolder();
        return transformer.getProgress(h)==Transformer.PROGRESS_STATE_AVAILABLE?h.progress:-1;
    }
    public void cancel(){
        cancelled=true;
        if(transformer!=null)transformer.cancel();
        clean();
    }
    public void clean(){
        if(intermediate!=null&&intermediate.exists())intermediate.delete();
        intermediate=null;transformer=null;
    }
}
