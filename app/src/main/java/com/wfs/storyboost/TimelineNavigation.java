package com.wfs.storyboost;
/** Shared, bounded source/timeline coordinate conversion. All intervals are half-open. */
final class TimelineNavigation {
    private TimelineNavigation(){}
    static long fromProgress(int value,long total){
        if(total<=1)return 0;
        return Math.round(Math.max(0,Math.min(10000,value))*(total-1)/10000d);
    }
    static int toProgress(long ms,long total){
        if(total<=1)return 0;
        return (int)Math.round(Math.max(0,Math.min(total-1,ms))*10000d/(total-1));
    }
    static long sourcePosition(TimelineProject project,int index,long globalMs){
        TimelineProject.Clip clip=project.clips.get(index);
        return clip.in+Math.max(0,Math.min(clip.length()-1,globalMs-project.startOf(index)));
    }
}
