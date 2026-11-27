package com.wfs.storyboost;

/** Alternating video lanes. Adjacent overlaps are capped at half of either clip,
 * so no third clip can overlap; all times are half-open milliseconds. */
final class OverlapPlan {
    final long[] starts,ends,overlaps;
    final long duration;
    OverlapPlan(long[] lengths,int requested){
        this(lengths,uniform(lengths.length,requested));
    }
    private static int[] uniform(int n,int ms){
        if(ms<0||ms>1000)throw new IllegalArgumentException("Crossfade 0–1000 ms.");
        int[] cuts=new int[Math.max(0,n-1)];java.util.Arrays.fill(cuts,ms);return cuts;
    }
    OverlapPlan(long[] lengths,int[] requested){
        if(requested.length!=Math.max(0,lengths.length-1))throw new IllegalArgumentException("Invalid cut count");
        for(int ms:requested)if(ms<0||ms>1000)throw new IllegalArgumentException("Crossfade 0–1000 ms.");
        starts=new long[lengths.length];ends=new long[lengths.length];overlaps=new long[Math.max(0,lengths.length-1)];
        long position=0;
        for(int i=0;i<lengths.length;i++){
            if(lengths[i]<=0)throw new IllegalArgumentException("Invalid clip length");
            if(i>0){overlaps[i-1]=Math.min(requested[i-1],Math.min(lengths[i-1],lengths[i])/2);position-=overlaps[i-1];}
            starts[i]=position;position=Math.addExact(position,lengths[i]);ends[i]=position;
        }
        duration=position;
    }
    boolean hasOverlap(){for(long ms:overlaps)if(ms>0)return true;return false;}
    int dominant(long ms){
        if(ms<0||ms>=duration)return -1;
        for(int i=starts.length-1;i>=0;i--)if(ms>=starts[i]&&ms<ends[i])return i;
        return -1;
    }
    float alphaTop(long timeUs){
        if(timeUs<0||timeUs>=duration*1000L)return 0;
        for(int i=0;i<starts.length;i+=2){
            long start=starts[i]*1000L,end=ends[i]*1000L;
            if(timeUs<start||timeUs>=end)continue;
            long in=i==0?0:overlaps[i-1]*1000L,out=i==overlaps.length?0:i<overlaps.length?overlaps[i]*1000L:0;
            float a=1;
            if(in>0&&timeUs<start+in)a=Math.min(a,(float)(timeUs-start)/in);
            if(out>0&&timeUs>=end-out)a=Math.min(a,(float)(end-timeUs)/out);
            return a;
        }
        return 0;
    }
    long fadeIn(int index){return index==0?0:overlaps[index-1];}
    long fadeOut(int index){return index<overlaps.length?overlaps[index]:0;}
}
