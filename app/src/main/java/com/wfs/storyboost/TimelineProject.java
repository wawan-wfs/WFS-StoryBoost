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

        Clip(String id,String uri,long duration,long in,long out){
            if(id==null||id.isEmpty()||id.length()>64||
                    uri==null||uri.length()>4096||!uri.startsWith("content://")||
                    duration<=0||duration>MAX_SOURCE_MS||in<0||out<=in||out>duration)
                throw new IllegalArgumentException("Rentang atau sumber klip tidak valid.");
            this.id=id;this.uri=uri;this.duration=duration;this.in=in;this.out=out;
        }
        static Clip create(String uri,long duration,long in,long out){
            return new Clip(UUID.randomUUID().toString(),uri,duration,in,out);
        }
        Clip trim(long start,long end){return new Clip(id,uri,duration,start,end);}
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

    final List<Clip> clips;
    final Caption caption;
    final int quality;

    TimelineProject(List<Clip> items,Caption caption,int quality){
        if(items.size()>MAX_CLIPS)throw new IllegalArgumentException("Maksimal 10 klip.");
        HashSet<String> ids=new HashSet<>();
        for(Clip c:items)if(!ids.add(c.id))throw new IllegalArgumentException("ID klip duplikat.");
        if(quality<0||quality>2)throw new IllegalArgumentException("Kualitas tidak valid.");
        clips=Collections.unmodifiableList(new ArrayList<>(items));this.quality=quality;
        if(caption!=null&&caption.out>duration())throw new IllegalArgumentException("Caption melebihi durasi timeline.");
        this.caption=caption;
    }
    static TimelineProject empty(){return new TimelineProject(Collections.emptyList(),null,1);}
    long duration(){long end=0;for(Clip c:clips)end+=c.length();return end;}
    private TimelineProject withClips(List<Clip> items){
        long end=0;for(Clip c:items)end+=c.length();
        Caption next=caption;
        if(next!=null&&next.in>=end)next=null;
        else if(next!=null&&next.out>end)next=new Caption(next.text,next.in,end,next.color);
        return new TimelineProject(items,next,quality);
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
    TimelineProject caption(Caption c){return new TimelineProject(clips,c,quality);}
    TimelineProject quality(int mode){return mode==quality?this:new TimelineProject(clips,caption,mode);}

    /** Undo/redo is session-local; every resulting approved snapshot is saved by the UI. */
    static final class History {
        private final ArrayDeque<TimelineProject> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
        private TimelineProject state;
        History(TimelineProject state){this.state=state;}
        TimelineProject current(){return state;}
        boolean canUndo(){return !undo.isEmpty();}
        boolean canRedo(){return !redo.isEmpty();}
        void apply(TimelineProject next){
            if(state==next)return;
            if(undo.size()==35)undo.removeFirst();
            undo.addLast(state);redo.clear();state=next;
        }
        void undo(){if(canUndo()){redo.addLast(state);state=undo.removeLast();}}
        void redo(){if(canRedo()){undo.addLast(state);state=redo.removeLast();}}
    }
}
