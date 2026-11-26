package com.wfs.storyboost;
/** Phase-independent tone projection over decoded PCM interior windows. */
final class PcmToneProbe {
    private long count;
    private double s440,c440,s780,c780;
    void add(long timeUs,double sample){
        double a=2*Math.PI*440*timeUs/1000000d,b=2*Math.PI*780*timeUs/1000000d;
        s440+=sample*Math.sin(a);c440+=sample*Math.cos(a);
        s780+=sample*Math.sin(b);c780+=sample*Math.cos(b);count++;
    }
    double tone440(){return count==0?0:2*Math.hypot(s440,c440)/count;}
    double tone780(){return count==0?0:2*Math.hypot(s780,c780)/count;}
    static boolean provesOverlap(double early780,double early440,double late780,double late440){
        return early780>.015&&early780<=1&&early440>=0&&early440<early780*.1
                &&late780>early780*.65&&late780<early780*1.35
                &&late440>early780*.65&&late440<early780*1.35;
    }
}
