package com.wfs.storyboost;

final class TimelineProjectTest {
    private static int checks;
    private static void check(boolean condition){
        if(!condition)throw new AssertionError("Timeline check "+(checks+1));
        checks++;
    }
    private static void rejects(Runnable test){
        try{test.run();}catch(IllegalArgumentException expected){checks++;return;}
        throw new AssertionError("Invalid timeline accepted");
    }
    public static void main(String[] args){
        String source="content://provider/videos/a";
        TimelineProject.Clip a=TimelineProject.Clip.create(source,2000,500,1500);
        TimelineProject.Clip repeat=TimelineProject.Clip.create(source,2000,300,1000);
        TimelineProject base=TimelineProject.empty();
        check(base.clips.isEmpty());
        check(base.clipIndexAt(0)==-1);
        check(!a.id.equals(repeat.id));
        TimelineProject project=base.append(a).append(repeat);
        check(project.duration()==1700);
        check(project.clipIndexAt(-1)==-1);
        check(project.clipIndexAt(0)==0);
        check(project.clipIndexAt(999)==0);
        check(project.clipIndexAt(1000)==1);
        check(project.clipIndexAt(1699)==1);
        check(project.clipIndexAt(1700)==-1);
        check(project.clipIndexAt(Long.MAX_VALUE)==-1);
        check(project.move(1,0).clipIndexAt(700)==1);
        check(project.trim(0,800,900).clipIndexAt(100)==1);
        check(project.clips.get(0).uri.equals(project.clips.get(1).uri));
        TimelineProject.Caption caption=new TimelineProject.Caption("Á سلام",600,1450,0xffffd786);
        project=project.caption(caption);
        check(!caption.atUs(599999));check(caption.atUs(600000));check(caption.atUs(1449999));check(!caption.atUs(1450000));
        TimelineProject.History history=new TimelineProject.History(project);
        history.apply(project.move(1,0));
        check(history.current().clips.get(0).id.equals(repeat.id));
        check(history.current().caption==caption);
        history.apply(history.current().trim(0,400,800));
        check(history.current().clips.get(0).id.equals(repeat.id));
        check(history.current().duration()==1400);
        check(history.current().caption.out==1400);
        history.undo();check(history.current().duration()==1700);
        history.undo();check(history.current()==project);
        history.redo();check(history.current().clips.get(0).id.equals(repeat.id));
        history.apply(history.current().remove(1));check(!history.canRedo());
        check(history.current().duration()==700&&history.current().caption.out==700);
        history.apply(history.current().remove(0));check(history.current().caption==null);
        check(project.duration()==1700&&project.caption.out==1450);
        rejects(()->a.trim(10,10));rejects(()->a.trim(-1,100));rejects(()->a.trim(100,3000));
        rejects(()->base.append(a).append(a));
        rejects(()->base.append(a).caption(caption));
        rejects(()->base.quality(3));
        rejects(()->TimelineProject.Clip.create("file:///secret",1000,0,1000));
        TimelineProject ten=base;
        for(int i=0;i<10;i++)ten=ten.append(TimelineProject.Clip.create(source,1000,0,1000));
        final TimelineProject full=ten;
        check(full.clips.size()==10&&full.duration()==10000);
        rejects(()->full.append(a));
        for(int i=0;i<42;i++)history.apply(history.current().quality((history.current().quality+1)%3));
        int n=0;while(history.canUndo()){history.undo();n++;}
        check(n==35);
        System.out.println("PASS "+checks+" timeline assertions");
    }
}
