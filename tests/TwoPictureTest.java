package com.wfs.storyboost;
public final class TwoPictureTest {
    static int checks;
    static void ok(boolean b){checks++;if(!b)throw new AssertionError("two picture "+checks);}
    public static void main(String[] args){
        TimelineProject base=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/base",2000,0,2000));
        PictureLayer a=new PictureLayer("content://qa/a",2000,0,1000,500,40,80,0,0,100,100);
        PictureLayer b=new PictureLayer("content://qa/b",2000,0,1500,250,30,60,100,0,0,100,PictureLayer.EASE_IN,1500);
        TimelineProject p=base.picture(a).picture2(b);
        ok(p.hasPictures());ok(p.picturesTopFirst().size()==2);ok(p.picturesTopFirst().get(0)==b);ok(p.picturesTopFirst().get(1)==a);
        ok(p.picture(null).picturesTopFirst().get(0)==b);ok(p.picture2(null).picturesTopFirst().get(0)==a);
        ok(p.quality(0).picture2==b);ok(p.zoom(0,50,100).picture2==b);ok(p.duplicate(0).crossfade(500).picture2==b);
        TimelineProject t=p.trim(0,0,1000);ok(t.picture.end()==1000);ok(t.picture2.end()==1000);ok(t.picture2.motionDuration==1500);
        ok(t.picture2.anchorX(800000)==b.anchorX(800000));ok(!p.remove(0).hasPictures());
        TimelineProject.History h=new TimelineProject.History(p);h.apply(p.picture2(null));h.undo();ok(h.current().picture2==b);h.redo();ok(h.current().picture2==null);
        boolean bad=false;try{base.trim(0,0,300).picture2(b);}catch(IllegalArgumentException ex){bad=true;}ok(bad);
        System.out.println("PASS "+checks+" independent two-picture assertions");
    }
}
