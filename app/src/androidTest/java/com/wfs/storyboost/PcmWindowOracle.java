package com.wfs.storyboost;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes actual AAC to signed PCM16 and measures two interior windows away from cuts. */
final class PcmWindowOracle {
    static final class Result {
        long mutedCount,audibleCount;
        final PcmToneProbe firstTones=new PcmToneProbe(),overlapTones=new PcmToneProbe(),lastTones=new PcmToneProbe();
        long lastCount;
        double mutedSquares,audibleSquares;
        double mutedRms(){return Math.sqrt(mutedSquares/Math.max(1,mutedCount));}
        double audibleRms(){return Math.sqrt(audibleSquares/Math.max(1,audibleCount));}
    }
    static Result decode(File file)throws Exception{return decode(file,200000,800000,1200000,1800000,0,0);}
    static Result decode(File file,long firstFrom,long firstTo,long middleFrom,long middleTo,long lastFrom,long lastTo)throws Exception{
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec codec=null;
        try{
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat input=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){input=f;extractor.selectTrack(i);break;}
            }
            if(input==null)throw new AssertionError("No AAC track to decode");
            codec=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
            input.setInteger(MediaFormat.KEY_PCM_ENCODING,android.media.AudioFormat.ENCODING_PCM_16BIT);
            codec.configure(input,null,null,0);codec.start();
            int rate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=input.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean inputDone=false,outputDone=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            Result result=new Result();
            long deadline=SystemClock.elapsedRealtime()+25000;
            while(!outputDone&&SystemClock.elapsedRealtime()<deadline){
                if(!inputDone){
                    int slot=codec.dequeueInputBuffer(5000);
                    if(slot>=0){
                        ByteBuffer b=codec.getInputBuffer(slot);b.clear();
                        int n=extractor.readSampleData(b,0);
                        if(n<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else {codec.queueInputBuffer(slot,0,n,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int out=codec.dequeueOutputBuffer(info,5000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat f=codec.getOutputFormat();
                    rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(f.getInteger(MediaFormat.KEY_PCM_ENCODING,android.media.AudioFormat.ENCODING_PCM_16BIT)
                            !=android.media.AudioFormat.ENCODING_PCM_16BIT)throw new AssertionError("Expected PCM16");
                }else if(out>=0){
                    ByteBuffer pcm=codec.getOutputBuffer(out);
                    if(info.size>0&&pcm!=null&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        pcm.order(ByteOrder.LITTLE_ENDIAN);pcm.position(info.offset);pcm.limit(info.offset+info.size);
                        int sample=0;
                        while(pcm.remaining()>=2){
                            double value=pcm.getShort()/32768.0;
                            long us=info.presentationTimeUs+(sample++/channels)*1000000L/rate;
                            if(us>=firstFrom&&us<firstTo){result.mutedCount++;result.mutedSquares+=value*value;result.firstTones.add(us,value);}
                            else if(us>=middleFrom&&us<middleTo){result.audibleCount++;result.audibleSquares+=value*value;result.overlapTones.add(us,value);}
                            else if(us>=lastFrom&&us<lastTo){result.lastCount++;result.lastTones.add(us,value);}
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(out,false);
                }
            }
            if(!outputDone||result.mutedCount<1000||result.audibleCount<1000||(lastTo>lastFrom&&result.lastCount<1000))
                throw new AssertionError("Incomplete decoded PCM evidence");
            return result;
        }finally{
            if(codec!=null)codec.release();
            extractor.release();
        }
    }
}
