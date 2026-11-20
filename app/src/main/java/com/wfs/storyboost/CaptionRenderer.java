package com.wfs.storyboost;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.BreakIterator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import java.util.Locale;

/** Shared, resolution-proportional caption drawing for Editor preview, JPEG, and video. */
final class CaptionRenderer {
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);

    CaptionRenderer(){
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
    }

    void draw(Canvas canvas,String text,int color,int profile,int width,int height){
        String[] lines=layoutLines(text,width,height);
        if(lines.length==0)return;
        // Font hinting at tiny display sizes changes measured advances and
        // wrapping. Lay out in a fixed width, then scale the same glyph run.
        float scale=width/1080f;
        float logicalWidth=1080f, logicalHeight=height/scale;
        float px=Math.min(logicalWidth*.058f,logicalHeight*.04f);
        paint.setColor(color);
        paint.setShadowLayer(px*.11f,px*.03f,px*.10f,0xEE000000);
        boolean rtl=TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text,0,text.length());
        TextDirectionHeuristic direction=rtl?TextDirectionHeuristics.RTL:TextDirectionHeuristics.LTR;
        float y=logicalHeight*SocialExportProfile.captionCenterY(profile)
                -(lines.length-1)*px*.65f;
        canvas.save();
        canvas.scale(scale,scale);
        for(String line:lines){
            // Fit even long captions within the frame, never clip at the sides.
            float advance=measure(line);
            float fit=advance>0?Math.min(1f,logicalWidth*.9f/advance):1f;
            // Android's paragraph layout shapes/reorders runs rather than
            // painting mixed Latin/Arabic/Hebrew as a single-direction run.
            int layoutWidth=Math.max(1,(int)Math.ceil(advance)+4);
            StaticLayout layout=StaticLayout.Builder.obtain(line,0,line.length(),paint,layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(direction).setIncludePad(false).setMaxLines(1).build();
            canvas.save();canvas.translate(logicalWidth/2f,y);canvas.scale(fit,1f);
            canvas.translate(-layoutWidth/2f,-layout.getLineBaseline(0));
            layout.draw(canvas);canvas.restore();
            y+=px*1.25f;
        }
        canvas.restore();
    }

    String[] layoutLines(String text,int width,int height) {
        String caption=ExportOptions.normalizeCaption(text);
        if(caption.isEmpty())return new String[0];
        float logicalHeight=height/(width/1080f);
        paint.setTextSize(Math.min(1080f*.058f,logicalHeight*.04f));
        float budget=1080f*.88f;
        if(measure(caption)<=budget)return new String[]{caption};
        BreakIterator graphemes=BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(caption);
        BreakIterator words=BreakIterator.getLineInstance(Locale.ROOT);
        int split=balancedBreak(caption,words,graphemes);
        // Prefer natural line breaks. Fall back to grapheme boundaries for
        // unbroken text or an unbalanced break that cannot fit the canvas.
        if(split<0||widestLine(caption,split)>budget){
            int grapheme=balancedBreak(caption,graphemes,null);
            if(grapheme>0&&(split<0||widestLine(caption,grapheme)<widestLine(caption,split)))
                split=grapheme;
        }
        return split>0?new String[]{caption.substring(0,split).trim(),caption.substring(split).trim()}
                :new String[]{caption};
    }

    private int balancedBreak(String caption,BreakIterator boundaries,BreakIterator required) {
        boundaries.setText(caption);
        int best=-1;
        float smallest=Float.POSITIVE_INFINITY;
        for(int at=boundaries.next();at!=BreakIterator.DONE&&at<caption.length();at=boundaries.next()){
            if(at==0||(required!=null&&!required.isBoundary(at)))continue;
            float width=widestLine(caption,at);
            if(width<smallest){best=at;smallest=width;}
        }
        return best;
    }

    private float widestLine(String caption,int at) {
        return Math.max(measure(caption.substring(0,at).trim()),measure(caption.substring(at).trim()));
    }

    private float measure(String text){return Layout.getDesiredWidth(text,paint);}
}
