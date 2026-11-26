package com.wfs.storyboost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Immutable edit instructions. Timestamps are milliseconds; intervals are [in, out). */
final class TimelineProject {
    static final int MAX_CLIPS=10;
    static final long MAX_SOURCE_MS=24L*60*60*1000;

    static final class Clip {
        final String id,uri;
        final long duration,in,out;
        final ClipAdjustments adjustments;

        Clip(String id,String uri,long duration,long in,long out){
            this(id,uri,duration,in,out,ClipAdjustments.NEUTRAL);
        }
        Clip(String id,String uri,long duration,long in,long out,ClipAdjustments adjustments){
            if(adjustments==null)throw new IllegalArgumentException("Pengaturan klip tidak tersedia.");
            if(id==null||id.isEmpty()||id.length()>64||
                    uri==null||uri.length()>4096||!uri.startsWith("content://")||
                    duration<=0||duration>MAX_SOURCE_MS||in<0||out<=in||out>duration)
                throw new IllegalArgumentException("Rentang atau sumber klip tidak valid.");
            this.id=id;this.uri=uri;this.duration=duration;this.in=in;this.out=out;this.adjustments=adjustments;
        }
        static Clip create(String uri,long duration,long in,long out){
            return new Clip(UUID.randomUUID().toString(),uri,duration,in,out);
        }
        Clip trim(long start,long end){return new Clip(id,uri,duration,start,end,adjustments);}
        Clip adjust(ClipAdjustments next){return adjustments.same(next)?this:new Clip(id,uri,duration,in,out,next);}
        long length(){return out-in;}
    }

    static final class Caption {
        final String text;
        final long in,out;
        final int color;
        Caption(String text,long in,long out,int color){
            if(text==null||text.length()>80||text.trim().isEmpty()||in<0||out<=in)
                throw new IllegalArgumentException("Teks atau rentang caption tidak valid.");
            this.text=text;this.in=in;this.out=out;this.color=color;
        }
        boolean atUs(long timeUs){return timeUs>=in*1000&&timeUs<out*1000;}
    }

    /** One independently trimmed soundtrack anchored to absolute timeline time. */
    static final class Audio {
        final String uri;
        final long duration,in,out,start;
        final int volume;
        Audio(String uri,long duration,long in,long out,long start,int volume){
            if(uri==null||!uri.startsWith("content://")||uri.length()>4096||
                    duration<=0||duration>MAX_SOURCE_MS||in<0||out<=in||out>duration||
                    start<0||start>MAX_SOURCE_MS*MAX_CLIPS||volume<1||volume>100)
                throw new IllegalArgumentException("Rentang atau volume audio tidak valid.");
            this.uri=uri;this.duration=duration;this.in=in;this.out=out;this.start=start;this.volume=volume;
        }
        long length(){return out-in;}
        long end(){return start+length();}
        Audio fit(long total){
            if(start>=total)return null;
            return end()<=total?this:new Audio(uri,duration,in,in+total-start,start,volume);
        }
    }
    final Audio audio;
    /** Fade-through-black on internal cuts; duration is the maximum per side. */
    final int fadeMs;
    final List<Clip> clips;
    final Caption caption;
    final int quality;

    TimelineProject(List<Clip> items,Caption caption,int quality){
        this(items,caption,quality,null,0);
    }
    TimelineProject(List<Clip> items,Caption caption,int quality,Audio audio,int fadeMs){
        if(fadeMs<0||fadeMs>1000)throw new IllegalArgumentException("Fade harus 0–1000 ms.");
        if(items.size()>MAX_CLIPS)throw new IllegalArgumentException("Maksimal 10 klip.");
        HashSet<String> ids=new HashSet<>();
        for(Clip c:items)if(!ids.add(c.id))throw new IllegalArgumentException("ID klip duplikat.");
        if(quality<0||quality>2)throw new IllegalArgumentException("Kualitas tidak valid.");
        clips=Collections.unmodifiableList(new ArrayList<>(items));this.quality=quality;
        if(caption!=null&&caption.out>duration())throw new IllegalArgumentException("Caption melebihi durasi timeline.");
        if(audio!=null&&audio.end()>duration())throw new IllegalArgumentException("Audio melebihi timeline.");
        this.caption=caption;this.audio=audio;this.fadeMs=fadeMs;
    }
    static TimelineProject empty(){return new TimelineProject(Collections.emptyList(),null,1);}
    long duration(){long end=0;for(Clip c:clips)end+=c.length();return end;}
    /** Half-open timeline intervals. At an exact cut, ownership starts in the next clip. */
    int clipIndexAt(long timelineMs){
        if(timelineMs<0)return -1;
        long end=0;
        for(int i=0;i<clips.size();i++){
            end+=clips.get(i).length();
            if(timelineMs<end)return i;
        }
        return -1;
    }
    private TimelineProject withClips(List<Clip> items){
        long end=0;for(Clip c:items)end+=c.length();
        Caption next=caption;
        if(next!=null&&next.in>=end)next=null;
        else if(next!=null&&next.out>end)next=new Caption(next.text,next.in,end,next.color);
        return new TimelineProject(items,next,quality,audio==null?null:audio.fit(end),fadeMs);
    }
    TimelineProject append(Clip clip){List<Clip> next=new ArrayList<>(clips);next.add(clip);return withClips(next);}
    TimelineProject remove(int index){List<Clip> next=new ArrayList<>(clips);next.remove(index);return withClips(next);}
    TimelineProject move(int index,int target){
        if(index==target||index<0||target<0||index>=clips.size()||target>=clips.size())return this;
        List<Clip> next=new ArrayList<>(clips);next.add(target,next.remove(index));return withClips(next);
    }
    TimelineProject trim(int index,long in,long out){
        if(clips.get(index).in==in&&clips.get(index).out==out)return this;
        List<Clip> next=new ArrayList<>(clips);next.set(index,next.get(index).trim(in,out));return withClips(next);
    }
    long startOf(int index){
        if(index<0||index>=clips.size())throw new IllegalArgumentException("Klip tidak tersedia.");
        long start=0;for(int i=0;i<index;i++)start+=clips.get(i).length();return start;
    }
    TimelineProject adjust(int index,ClipAdjustments next){
        Clip clip=clips.get(index),edited=clip.adjust(next);
        if(clip==edited)return this;
        List<Clip> items=new ArrayList<>(clips);items.set(index,edited);return withClips(items);
    }
    TimelineProject caption(Caption c){return new TimelineProject(clips,c,quality,audio,fadeMs);}
    TimelineProject quality(int mode){return mode==quality?this:new TimelineProject(clips,caption,mode,audio,fadeMs);}
    TimelineProject soundtrack(Audio next){return next==audio?this:new TimelineProject(clips,caption,quality,next,fadeMs);}
    TimelineProject fade(int ms){return ms==fadeMs?this:new TimelineProject(clips,caption,quality,audio,ms);}
    /** Split is one immutable edit; original ID stays with the left segment. */
    TimelineProject split(int index,long sourceMs){
        Clip clip=clips.get(index);
        if(sourceMs<=clip.in||sourceMs>=clip.out)throw new IllegalArgumentException("Split harus di dalam klip.");
        List<Clip> next=new ArrayList<>(clips);
        next.set(index,clip.trim(clip.in,sourceMs));
        next.add(index+1,new Clip(UUID.randomUUID().toString(),clip.uri,clip.duration,sourceMs,clip.out,clip.adjustments));
        return withClips(next);
    }
    TimelineProject duplicate(int index){
        Clip clip=clips.get(index);List<Clip> next=new ArrayList<>(clips);
        next.add(index+1,new Clip(UUID.randomUUID().toString(),clip.uri,clip.duration,clip.in,clip.out,clip.adjustments));
        return withClips(next);
    }
    /** Dip to black without shortening or overlapping clips, with short-clip clamping. */
    float fadeGain(long timeUs){
        if(fadeMs==0||clips.size()<2)return 1f;
        long boundary=0;
        float gain=1f;
        for(int i=0;i<clips.size()-1;i++){
            boundary+=clips.get(i).length()*1000L;
            long width=Math.min(fadeMs*1000L,Math.min(clips.get(i).length(),clips.get(i+1).length())*500L);
            long distance=Math.abs(timeUs-boundary);
            if(width>0&&distance<width)gain=Math.min(gain,(float)distance/width);
        }
        return gain;
    }

    /** Undo/redo is session-local; every resulting approved snapshot is saved by the UI. */
    static final class History {
        private final ArrayDeque<TimelineProject> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
        private TimelineProject state;
        History(TimelineProject state){this.state=state;}
        TimelineProject current(){return state;}
        boolean canUndo(){return !undo.isEmpty();}
        boolean canRedo(){return !redo.isEmpty();}
        TimelineProject undoTarget(){return canUndo()?undo.peekLast():state;}
        TimelineProject redoTarget(){return canRedo()?redo.peekLast():state;}
        void apply(TimelineProject next){
            if(state==next)return;
            if(undo.size()==35)undo.removeFirst();
            undo.addLast(state);redo.clear();state=next;
        }
        void undo(){if(canUndo()){redo.addLast(state);state=undo.removeLast();}}
        void redo(){if(canRedo()){undo.addLast(state);state=redo.removeLast();}}
    }
}
