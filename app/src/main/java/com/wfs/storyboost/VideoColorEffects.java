package com.wfs.storyboost;

import androidx.media3.common.Effect;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.RgbAdjustment;
import java.util.List;

/** Single deterministic color recipe shared by the editor and timeline renderer. */
@SuppressWarnings("UnsafeOptInUsageError")
final class VideoColorEffects {
    private VideoColorEffects(){}
    static void append(List<Effect> effects,ExportOptions settings){
        ColorLooks.Look look=ColorLooks.get(settings.preset);
        if(!ColorLooks.isOriginal(settings.preset)){
            float c=Math.max(-.5f,Math.min(.5f,look.contrast-1f));
            if(Math.abs(c)>.001f)effects.add(new Contrast(c));
            float sat=(look.saturation-1f)*65f;
            if(Math.abs(sat)>.1f)effects.add(
                    new HslAdjustment.Builder().adjustSaturation(sat).build());
            float lift=look.lift/255f;
            float r=Math.max(.6f,Math.min(1.5f,look.red+lift));
            float g=Math.max(.6f,Math.min(1.5f,look.green+lift));
            float b=Math.max(.6f,Math.min(1.5f,look.blue+lift));
            effects.add(new RgbAdjustment.Builder()
                    .setRedScale(r).setGreenScale(g).setBlueScale(b).build());
        }
        if(settings.hasColorAdjustments()){
            if(settings.contrast!=0f)effects.add(new Contrast(settings.contrast*.25f));
            if(settings.saturation!=0f)effects.add(new HslAdjustment.Builder()
                    .adjustSaturation(settings.saturation*35f).build());
            if(settings.brightness!=0f){
                float scale=Math.max(.7f,Math.min(1.3f,1f+settings.brightness*.25f));
                effects.add(new RgbAdjustment.Builder().setRedScale(scale)
                        .setGreenScale(scale).setBlueScale(scale).build());
            }
        }
    }
    static void append(List<Effect> effects,ClipAdjustments edits){
        append(effects,new ExportOptions(edits.look,AspectRatioProfile.ORIGINAL,AspectRatioProfile.FIT,false)
                .withAdjustments(edits.brightness/100f,edits.contrast/100f,edits.saturation/100f));
    }
}
