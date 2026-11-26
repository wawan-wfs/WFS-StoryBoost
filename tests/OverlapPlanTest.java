package com.wfs.storyboost;
public final class OverlapPlanTest {
    static int count;
    static void ok(boolean b){count++;if(!b)throw new AssertionError("overlap "+count);}
    public static void main(String[] args){
        OverlapPlan a=new OverlapPlan(new long[]{2000,2000,2000},500);
        ok(a.duration==5000);ok(a.starts[1]==1500);ok(a.starts[2]==3000);
        ok(a.alphaTop(500000)==1);ok(a.alphaTop(1750000)==.5f);ok(a.alphaTop(2500000)==0);ok(a.alphaTop(3250000)==.5f);ok(a.alphaTop(4250000)==1);
        ok(a.dominant(1499)==0);ok(a.dominant(1500)==1);ok(a.dominant(5000)==-1);
        java.util.Random rng=new java.util.Random(21);
        for(int k=0;k<100;k++){
            long[] sizes=new long[2+rng.nextInt(9)];long sum=0;
            for(int i=0;i<sizes.length;i++){sizes[i]=1+rng.nextInt(2000);sum+=sizes[i];}
            OverlapPlan plan=new OverlapPlan(sizes,rng.nextInt(1001));long overlap=0;
            for(int i=0;i<sizes.length;i++){
                ok(plan.ends[i]-plan.starts[i]==sizes[i]);
                ok(plan.fadeIn(i)+plan.fadeOut(i)<=sizes[i]);
                if(i>1)ok(plan.starts[i]>=plan.ends[i-2]);
                overlap+=plan.fadeIn(i);
            }
            ok(plan.duration==sum-overlap);
            for(long t=0;t<plan.duration;t+=31){
                float alpha=plan.alphaTop(t*1000);ok(alpha>=0&&alpha<=1);ok(plan.dominant(t)>=0);
            }
        }
        TimelineProject model=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/a",2000,0,2000))
            .append(TimelineProject.Clip.create("content://qa/b",2000,0,2000))
            .soundtrack(new TimelineProject.Audio("content://qa/c",4000,0,4000,0,50)).crossfade(500);
        ok(model.duration()==3500);ok(model.audio.out==3500);ok(model.startOf(1)==1500);
        ok(model.fade(300).duration()==4000);ok(model.fade(300).crossfadeMs==0);ok(model.crossfade(500)==model);
        ok(model.duplicate(0).crossfadeMs==500);ok(model.zoom(0,50,150).crossfadeMs==500);
        System.out.println("PASS "+count+" overlap schedule assertions");
    }
}
