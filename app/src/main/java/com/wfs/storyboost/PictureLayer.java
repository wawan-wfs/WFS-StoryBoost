package com.wfs.storyboost;

/** One independent muted video overlay; immutable timeline-relative geometry.
 * Coordinates express percent of available travel (0=left/top, 100=right/bottom).
 */
final class PictureLayer {
    final String uri;
    final long duration,in,out,start;
    final int scale,opacity,xStart,yStart,xEnd,yEnd;
    PictureLayer(String uri,long duration,long in,long out,long start,int scale,int opacity,int xStart,int yStart,int xEnd,int yEnd){
        if(uri==null||!uri.startsWith("content://")||uri.length()>4096||duration<=0||duration>TimelineProject.MAX_SOURCE_MS||
                in<0||out<=in||out>duration||start<0||start>TimelineProject.MAX_SOURCE_MS*TimelineProject.MAX_CLIPS||
                scale<20||scale>60||opacity<25||opacity>100||!percent(xStart)||!percent(yStart)||!percent(xEnd)||!percent(yEnd))
            throw new IllegalArgumentException("Rentang atau posisi PiP tidak valid.");
        this.uri=uri;this.duration=duration;this.in=in;this.out=out;this.start=start;
        this.scale=scale;this.opacity=opacity;this.xStart=xStart;this.yStart=yStart;this.xEnd=xEnd;this.yEnd=yEnd;
    }
    private static boolean percent(int value){return value>=0&&value<=100;}
    long length(){return out-in;}
    long end(){return start+length();}
    boolean visible(long us){return us>=start*1000L&&us<end()*1000L;}
    private double fraction(long us){return Math.max(0,Math.min(1,(us-start*1000L)/(double)(length()*1000L)));}
    private double x(long us){return xStart+(xEnd-xStart)*fraction(us);}
    private double y(long us){return yStart+(yEnd-yStart)*fraction(us);}
    float anchorX(long us){return (float)((x(us)/50-1)*(1-scale/100d));}
    float anchorY(long us){return (float)((1-y(us)/50)*(1-scale/100d));}
    PictureLayer fit(long total){
        if(start>=total)return null;if(end()<=total)return this;
        return new PictureLayer(uri,duration,in,in+total-start,start,scale,opacity,xStart,yStart,
                (int)Math.round(x(total*1000L)),(int)Math.round(y(total*1000L)));
    }
}
