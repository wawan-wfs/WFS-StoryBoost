package com.wfs.storyboost;
public final class TimelineBatchTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void bad(Runnable action){boolean failed=false;try{action.run();}catch(IllegalArgumentException ex){failed=true;}check(failed);}
    public static void main(String[] args){
        TimelineProject p=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",4000,500,2500));
        ClipAdjustments edits=new ClipAdjustments(1,10,20,-10,true);
        p=p.adjust(0,edits);
        TimelineProject split=p.split(0,1500);
        check(split.clips.size()==2);check(split.duration()==p.duration());check(split.clips.get(0).id.equals(p.clips.get(0).id));
        check(!split.clips.get(1).id.equals(p.clips.get(0).id));check(split.clips.get(1).adjustments.same(edits));
        check(split.clips.get(0).out==1500);check(split.clips.get(1).in==1500);
        check(split.clipIndexAt(999)==0);check(split.clipIndexAt(1000)==1);
        check(TimelineNavigation.sourcePosition(split,1,1000)==1500);
        check(TimelineNavigation.fromProgress(10000,2000)==1999);check(TimelineNavigation.fromProgress(-1,2000)==0);
        check(TimelineNavigation.toProgress(1999,2000)==10000);check(TimelineNavigation.toProgress(0,0)==0);
        for(int i=0;i<=10000;i+=100){long ms=TimelineNavigation.fromProgress(i,864000000);check(Math.abs(TimelineNavigation.toProgress(ms,864000000)-i)<=1);}
        TimelineProject dup=split.duplicate(1);check(dup.duration()==3000);check(!dup.clips.get(1).id.equals(dup.clips.get(2).id));check(dup.clips.get(2).adjustments.same(edits));
        TimelineProject f=split.fade(300);check(f.fadeGain(1000000)==0);check(f.fadeGain(700000)==1);
        check(Math.abs(f.fadeGain(850000)-.5f)<.001);check(Math.abs(f.fadeGain(1150000)-.5f)<.001);
        check(f.fadeGain(1300000)==1);check(f.fade(0).fadeGain(1000000)==1);
        TimelineProject.Audio sound=new TimelineProject.Audio("content://qa/s",3000,200,1200,500,50);
        TimelineProject mixed=f.soundtrack(sound);check(mixed.audio.end()==1500);check(mixed.quality(0).audio==sound);
        check(mixed.caption(new TimelineProject.Caption("QA",100,900,0)).audio==sound);
        check(mixed.move(0,1).audio==sound);check(mixed.remove(1).audio.out==700);check(mixed.remove(1).remove(0).audio==null);
        check(mixed.fade(0).audio==sound);check(mixed.soundtrack(null).fadeMs==300);
        TimelineProject.History h=new TimelineProject.History(f);h.apply(mixed);h.undo();check(h.current().audio==null);h.redo();check(h.current().audio==sound);
        bad(()->split.split(0,500));bad(()->split.split(0,1500));bad(()->split.fade(1001));
        bad(()->new TimelineProject.Audio("file:///tmp/x",1000,0,500,0,50));
        bad(()->new TimelineProject.Audio("content://qa/s",1000,0,500,0,101));
        bad(()->split.soundtrack(new TimelineProject.Audio("content://qa/s",2000,0,1000,1500,50)));
        System.out.println("PASS "+checks+" integrated timeline assertions");
    }
}
