package com.wfs.storyboost;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.Locale;

import static org.junit.Assert.*;

/** Independent one-line reference: manually place known visual runs, without StaticLayout or CaptionRenderer. */
final class CaptionGlyphOracle {
    private static final int GOLD=0xffffd786;
    private static final String AR="سلام", HE="שָׁלוֹם", LATIN="A7";
    private static final String ACUTE="A\u0301", HEART="💙", LAST="R";
    static final String BIDI=AR+" "+LATIN+" "+HE;
    static final String ACCENT_EMOJI=ACUTE+" "+HEART+" "+LAST;
    private static final int GOLD_INK=0, BLUE_INK=1;

    static void verify(Bitmap actual,String title,String medium) {
        assertEquals(432,actual.getWidth());
        assertEquals(720,actual.getHeight());
        if(BIDI.equals(title)){
            try (Reference good=reference(new String[]{HE,LATIN,AR},new boolean[]{true,false,true});
                 Reference wrong=reference(new String[]{AR,LATIN,HE},new boolean[]{true,false,true});
                 Reference unjoined=reference(new String[]{HE,LATIN,"ﺱﻝﺍﻡ"},new boolean[]{true,false,true})){
                float match=best(actual,good.bitmap,good.all,GOLD_INK);
                float wrongOrder=best(actual,wrong.bitmap,wrong.all,GOLD_INK);
                float noJoin=best(actual,unjoined.bitmap,unjoined.runs[2],GOLD_INK);
                float shaped=best(actual,good.bitmap,good.runs[2],GOLD_INK);
                report(medium,"bidi",match,wrongOrder,shaped,noJoin);
                assertTrue("Independent oracle rejected visual order: "+match+" vs "+wrongOrder,
                        match>.60f&&match>wrongOrder+.15f);
                assertTrue("Contextual Arabic must beat isolated glyphs: "+shaped+" vs "+noJoin,
                        shaped>.58f&&shaped>noJoin+.12f);
                assertTrue("Hebrew vowel points/order mismatch",best(actual,good.bitmap,good.runs[0],GOLD_INK)>.58f);
            }
        }else{
            try (Reference good=reference(new String[]{ACUTE,HEART,LAST},new boolean[3]);
                 Reference plain=reference(new String[]{"A",HEART,LAST},new boolean[3])){
                Rect accents=new Rect(good.runs[0]);
                Rect emoji=new Rect(good.runs[1]);
                float combined=best(actual,good.bitmap,accents,GOLD_INK);
                // Inspect only the pixels above the cap top of the bare A.
                // Whole-letter overlap hides a tiny but semantically required mark.
                Rect bareLetter=bounds(plain.bitmap,accents,GOLD_INK);
                Rect accentOnly=new Rect(accents.left,accents.top,accents.right,bareLetter.top);
                assertFalse("Reference bare A not found",bareLetter.isEmpty());
                assertFalse("Reference needs a distinct acute mark",bounds(good.bitmap,accentOnly,GOLD_INK).isEmpty());
                float mark=best(actual,good.bitmap,accentOnly,GOLD_INK);
                float bare=best(actual,plain.bitmap,accentOnly,GOLD_INK);
                float blue=best(actual,good.bitmap,emoji,BLUE_INK);
                Rect actualEmoji=bounds(actual,emoji,BLUE_INK);
                Rect expectedEmoji=bounds(good.bitmap,emoji,BLUE_INK);
                report(medium,"acute-anchor",mark,bare,combined,0f);
                report(medium,"emoji-baseline",blue,0f,actualEmoji.bottom-expectedEmoji.bottom,0f);
                assertTrue("Combining anchor/shape mismatch",combined>.58f&&mark>.35f&&mark>bare+.25f);
                assertTrue("Blue emoji was clipped or substituted",blue>.55f&&!expectedEmoji.isEmpty());
                assertTrue("Emoji baseline drift",Math.abs(actualEmoji.bottom-expectedEmoji.bottom)<=3);
            }
        }
    }

    private static void report(String medium,String id,float a,float b,float c,float d){
        android.util.Log.i("WfsBeta14GlyphQA",String.format(Locale.US,
                "medium=%s case=%s positive=%.3f negative=%.3f secondary=%.3f control=%.3f",medium,id,a,b,c,d));
    }

    private static final class Reference implements AutoCloseable {
        final Bitmap bitmap=Bitmap.createBitmap(432,720,Bitmap.Config.ARGB_8888);
        final Rect[] runs=new Rect[3];
        final Rect all=new Rect();
        @Override public void close(){bitmap.recycle();}
    }
    private static Reference reference(String[] visual,boolean[] rtl){
        Reference ref=new Reference();
        Canvas c=new Canvas(ref.bitmap);
        c.drawColor(Color.rgb(72,72,72));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(GOLD);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        // Fixed public caption contract for a 432x720 manual canvas.
        float scale=.4f;
        paint.setTextSize(62.64f);
        float y=597.6f;
        float gap=paint.measureText(" "), width=gap*2;
        float[] advance=new float[3];
        for(int i=0;i<3;i++){
            String run=visual[i];
            advance[i]=paint.getRunAdvance(run,0,run.length(),0,run.length(),rtl[i],run.length());
            width+=advance[i];
        }
        float x=540-width/2;
        c.save();
        c.scale(scale,scale);
        for(int i=0;i<3;i++){
            String run=visual[i];
            c.drawTextRun(run,0,run.length(),0,run.length(),x,y/scale,rtl[i],paint);
            ref.runs[i]=new Rect((int)(x*scale)-2,550,(int)Math.ceil((x+advance[i])*scale)+2,620);
            ref.all.union(ref.runs[i]);
            x+=advance[i]+gap;
        }
        c.restore();
        return ref;
    }

    // Binary foreground overlap with <=2 pixel registration tolerance. Evaluate
    // against a fixed independent visual order and against broken controls.
    private static float best(Bitmap actual,Bitmap ref,Rect region,int ink){
        float best=0;
        for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
            int both=0,total=0;
            for(int y=region.top;y<region.bottom;y++)for(int x=region.left;x<region.right;x++){
                boolean a=ink(actual.getPixel(x+dx,y+dy),ink), b=ink(ref.getPixel(x,y),ink);
                if(a&&b)both++;
                if(a||b)total++;
            }
            if(total>0)best=Math.max(best,both/(float)total);
        }
        return best;
    }
    private static Rect bounds(Bitmap b,Rect region,int ink){
        Rect bounds=new Rect();
        for(int y=region.top;y<region.bottom;y++)for(int x=region.left;x<region.right;x++)
            if(ink(b.getPixel(x,y),ink))bounds.union(x,y,x+1,y+1);
        return bounds;
    }
    private static boolean ink(int rgb,int color){
        int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
        return color==BLUE_INK?b>120&&b-r>55&&b-g>21:r>165&&g>125&&r-b>58&&g-b>34;
    }
}
