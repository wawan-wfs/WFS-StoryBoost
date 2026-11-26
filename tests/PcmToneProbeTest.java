package com.wfs.storyboost;
public final class PcmToneProbeTest {
    private static int checks;
    private static void ok(boolean b){checks++;if(!b)throw new AssertionError("tone check "+checks);}
    public static void main(String[] args){
        for(int phase=0;phase<12;phase++){
            PcmToneProbe a=new PcmToneProbe(),b=new PcmToneProbe();
            for(int i=0;i<28800;i++){
                long us=i*1000000L/48000;double t=us/1000000d;
                double x=.04*Math.sin(2*Math.PI*780*t+.2*phase);
                a.add(us,x);b.add(us,x+.04*Math.sin(2*Math.PI*440*t+phase));
            }
            ok(Math.abs(a.tone780()-.04)<.0001);ok(a.tone440()<.0001);
            ok(PcmToneProbe.provesOverlap(a.tone780(),a.tone440(),b.tone780(),b.tone440()));
            ok(!PcmToneProbe.provesOverlap(a.tone780(),a.tone440(),a.tone780(),a.tone440()));
        }
        ok(!PcmToneProbe.provesOverlap(.04,.02,.04,.04));
        ok(!PcmToneProbe.provesOverlap(.04,0,0,.04));
        System.out.println("PASS "+checks+" phase-independent tone assertions");
    }
}
