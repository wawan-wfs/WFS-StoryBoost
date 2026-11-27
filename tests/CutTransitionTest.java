package com.wfs.storyboost;
import java.util.Arrays;
public final class CutTransitionTest {
    static int n;
    static void ok(boolean b){n++;if(!b)throw new AssertionError("cut "+n);}
    static TimelineProject.Clip clip(String id){return new TimelineProject.Clip(id,"content://qa/"+id,2000,0,2000);}
    public static void main(String[] args){
        TimelineProject base=TimelineProject.empty().append(clip("a")).append(clip("b")).append(clip("c"));
        TimelineProject edit=base.cut(0,500).cut(1,150);
        ok(edit.duration()==5350);ok(edit.startOf(1)==1500);ok(edit.startOf(2)==3350);
        ok(edit.overlap.hasOverlap());ok(edit.cutMs(0)==500&&edit.cutMs(1)==150);
        ok(edit.quality(0).cuts.size()==2);ok(edit.zoom(0,50,100).cuts.size()==2);
        ok(edit.move(0,2).cuts.size()==1);ok(edit.move(0,2).cutMs(0)==150);
        ok(edit.remove(1).cuts.isEmpty());ok(edit.cut(0,0).duration()==5850);
        ok(edit.fade(150).cuts.isEmpty());ok(edit.fade(150).duration()==6000);
        ok(edit.crossfade(300).cuts.isEmpty());ok(edit.crossfade(300).duration()==5400);
        TimelineProject.History h=new TimelineProject.History(edit);h.apply(edit.move(0,2));h.undo();ok(h.current()==edit);
        boolean bad=false;try{new TimelineProject(base.clips,null,1,null,null,0,0,null,Arrays.asList(new CutTransition("a","c",500)));}catch(IllegalArgumentException e){bad=true;}ok(bad);
        java.util.Random rng=new java.util.Random(23);
        for(int run=0;run<200;run++){
            long[] len={1+rng.nextInt(2000),1+rng.nextInt(2000),1+rng.nextInt(2000),1+rng.nextInt(2000)};
            int[] cuts={rng.nextInt(1001),rng.nextInt(1001),rng.nextInt(1001)};
            OverlapPlan plan=new OverlapPlan(len,cuts);long sum=0,overlap=0;
            for(int i=0;i<len.length;i++){sum+=len[i];overlap+=plan.fadeIn(i);ok(plan.ends[i]-plan.starts[i]==len[i]);if(i>1)ok(plan.starts[i]>=plan.ends[i-2]);}
            ok(plan.duration==sum-overlap);
            for(int i=0;i<cuts.length;i++)ok(plan.overlaps[i]==Math.min(cuts[i],Math.min(len[i],len[i+1])/2));
        }
        System.out.println("PASS "+n+" independent cut assertions");
    }
}
