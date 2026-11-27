package com.wfs.storyboost;
public final class PictureLayerTest{
    static int checks;
    static void ok(boolean b){checks++;if(!b)throw new AssertionError("PiP "+checks);}
    static void bad(Runnable action){boolean threw=false;try{action.run();}catch(IllegalArgumentException e){threw=true;}ok(threw);}
    public static void main(String[] args){
        PictureLayer layer=new PictureLayer("content://qa/p",2000,0,1000,500,40,100,0,0,100,100);
        ok(!layer.visible(499999));ok(layer.visible(500000));ok(layer.visible(1499999));ok(!layer.visible(1500000));
        ok(Math.abs(layer.anchorX(500000)+.6f)<1e-6);ok(Math.abs(layer.anchorY(500000)-.6f)<1e-6);
        ok(layer.anchorX(1000000)==0);ok(layer.anchorY(1000000)==0);
        ok(Math.abs(layer.anchorX(1500000)-.6f)<1e-6);
        PictureLayer clipped=layer.fit(1000);ok(clipped.out==500);ok(clipped.xEnd==50);ok(clipped.yEnd==50);
        ok(layer.fit(500)==null);ok(layer.fit(1500)==layer);
        TimelineProject p=TimelineProject.empty().append(TimelineProject.Clip.create("content://qa/b",2000,0,2000)).picture(layer);
        ok(p.duration()==2000);ok(p.zoom(0,50,150).picture==layer);ok(p.quality(0).picture==layer);ok(p.duplicate(0).crossfade(300).picture==layer);
        ok(p.trim(0,0,1000).picture.out==500);ok(p.remove(0).picture==null);
        TimelineProject.History h=new TimelineProject.History(p);h.apply(p.picture(null));h.undo();ok(h.current().picture==layer);h.redo();ok(h.current().picture==null);
        bad(()->new PictureLayer("file:///tmp/b",2000,0,1000,500,40,100,0,0,100,100));
        bad(()->new PictureLayer("content://qa/b",2000,0,1000,500,61,100,0,0,100,100));
        bad(()->new PictureLayer("content://qa/b",2000,0,1000,500,40,101,0,0,100,100));
        for(int scale=20;scale<=60;scale++)for(int x=0;x<=100;x+=5){
            PictureLayer a=new PictureLayer("content://qa/p",2000,0,1000,0,scale,50,x,x,100-x,100-x);
            for(long time=0;time<=1000000;time+=50000){
                ok(Math.abs(a.anchorX(time))+scale/100d<=1.000001);ok(Math.abs(a.anchorY(time))+scale/100d<=1.000001);
            }
        }
        for(int curve=1;curve<=3;curve++){
            PictureLayer motion=new PictureLayer("content://qa/p",2000,0,1000,500,40,80,0,0,100,100,curve,1000);
            PictureLayer shorter=motion.fit(1200);ok(shorter.motionDuration==1000);ok(shorter.out==700);
            for(long t=500000;t<1200000;t+=1000){ok(motion.anchorX(t)==shorter.anchorX(t));ok(motion.anchorY(t)==shorter.anchorY(t));}
            ok(motion.anchorX(500000)<motion.anchorX(750000));ok(motion.anchorX(750000)<motion.anchorX(1250000));
        }
        PictureLayer eased=new PictureLayer("content://qa/p",2000,0,1000,500,40,80,0,0,100,100,PictureLayer.EASE_IN,1000);
        ok(Math.abs(eased.anchorX(1000000)+.3f)<1e-6);
        bad(()->new PictureLayer("content://qa/p",2000,0,1000,0,40,80,0,0,100,100,4,1000));
        bad(()->new PictureLayer("content://qa/p",2000,0,1000,0,40,80,0,0,100,100,PictureLayer.EASE_IN,999));
        System.out.println("PASS "+checks+" picture layer assertions");
    }
}
