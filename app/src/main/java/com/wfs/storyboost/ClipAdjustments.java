package com.wfs.storyboost;

/** Immutable bounded edits, with integer percentages for lossless JSON round trips. */
final class ClipAdjustments {
    static final ClipAdjustments NEUTRAL=new ClipAdjustments(ColorLooks.ORIGINAL,0,0,0,false);
    final int look,brightness,contrast,saturation;
    final boolean mute;
    ClipAdjustments(int look,int brightness,int contrast,int saturation,boolean mute){
        ColorLooks.get(look);
        validate(brightness);validate(contrast);validate(saturation);
        this.look=look;this.brightness=brightness;this.contrast=contrast;
        this.saturation=saturation;this.mute=mute;
    }
    private static void validate(int value){
        if(value < -100 || value > 100)throw new IllegalArgumentException("Penyesuaian warna -100 sampai +100");
    }
    boolean hasColor(){return look!=ColorLooks.ORIGINAL||brightness!=0||contrast!=0||saturation!=0;}
    boolean same(ClipAdjustments other){
        return other!=null&&look==other.look&&brightness==other.brightness&&
                contrast==other.contrast&&saturation==other.saturation&&mute==other.mute;
    }
    boolean keepsAudio(boolean inputHasAudio){return inputHasAudio&&!mute;}
    String summary(){
        if(!hasColor()&&!mute)return "Original • Audio asli";
        return ColorLooks.get(look).name+" • B "+brightness+" / C "+contrast+" / S "+saturation+
                (mute?" • Bisu":" • Audio asli");
    }
}
