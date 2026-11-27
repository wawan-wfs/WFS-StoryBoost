// SPDX-License-Identifier: MIT
package com.wfs.storyboost;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Original tiny residual CNN, synthetic-only research, CPU and network-free. */
final class RestorationLab {
    static final int MAX_SIDE=512, PARAMETERS=77;
    static final int MAX_PREVIEW_SIDE=1024,TILE=128,HALO=2;
    static final String DEBLUR_SHA="89e355238c2c233e6442800b0a8b3c2d1633ee6d195cdcc532a3d42a8d6dfcf4";
    static final String SR_SHA="dea96181a956528cfff99cdf94856f5b2a594ed72e467f025e0c4dab99483b01";
    static final String REFINED_DEBLUR_SHA="1d8b2f6b4899ec41bcea862d293c652afb859e67e03417a577033574422419c5";
    static final String REFINED_SR_SHA="972a3deaaa563a00f0d42c42c27518b13de386b2a9e07c57aacb7f70d743f0ae";
    private final float[] weights;
    private RestorationLab(float[] weights){this.weights=weights;}
    static RestorationLab load(InputStream stream,boolean sr)throws Exception{return load(stream,sr,false);}
    static RestorationLab load(InputStream stream,boolean sr,boolean refined)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[512];int n;
        while((n=stream.read(block))!=-1){if(out.size()+n>4096)throw new IOException("Oversized model");out.write(block,0,n);}
        byte[] raw=out.toByteArray();StringBuilder hex=new StringBuilder();
        for(byte v:MessageDigest.getInstance("SHA-256").digest(raw))hex.append(String.format(Locale.ROOT,"%02x",v&255));
        if(!hex.toString().equals(refined?(sr?REFINED_SR_SHA:REFINED_DEBLUR_SHA):(sr?SR_SHA:DEBLUR_SHA)))throw new IOException("Invalid model SHA-256");
        String[] tokens=new String(raw,StandardCharsets.US_ASCII).trim().split("\\s+");
        if(tokens.length!=PARAMETERS)throw new IOException("Invalid model shape");
        float[] w=new float[PARAMETERS];for(int i=0;i<w.length;i++){w[i]=Float.parseFloat(tokens[i]);if(!Float.isFinite(w[i]))throw new IOException("Invalid model weight");}
        return new RestorationLab(w);
    }
    private static void validate(float[] pixels,int width,int height){
        if(width<1||height<1||width>MAX_SIDE||height>MAX_SIDE||pixels.length!=width*height)throw new IllegalArgumentException("Invalid preview dimensions");
        for(float x:pixels)if(!Float.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Invalid luma");
    }
    private static int clamp(int n,int limit){return Math.max(0,Math.min(limit-1,n));}
    private static float clip(float n){return Math.max(0,Math.min(1,n));}
    static float[] bilinear2x(float[] input,int width,int height){
        validate(input,width,height);if(width>MAX_SIDE/2||height>MAX_SIDE/2)throw new IllegalArgumentException("SR preview limit");
        int w=width*2,h=height*2;float[] result=new float[w*h];
        for(int y=0;y<h;y++){
            float fy=Math.max(0,Math.min(height-1,(y+.5f)/2-.5f));int y0=(int)fy,y1=Math.min(height-1,y0+1);
            for(int x=0;x<w;x++){
                float fx=Math.max(0,Math.min(width-1,(x+.5f)/2-.5f));int x0=(int)fx,x1=Math.min(width-1,x0+1);
                float a=input[y0*width+x0]*(1-fx+x0)+input[y0*width+x1]*(fx-x0);
                float b=input[y1*width+x0]*(1-fx+x0)+input[y1*width+x1]*(fx-x0);
                result[y*w+x]=clip(a*(1-fy+y0)+b*(fy-y0));
            }
        }return result;
    }
    /** Exact tiled convolution: two 3x3 layers require a two-pixel source halo.
     * Hidden activations are bounded to (TILE+2*HALO)^2*4 floats, independent
     * of image area. True outer boundaries retain replicated padding.
     */
    float[] inferTiled(float[] input,int width,int height)throws InterruptedException{
        if(width<1||height<1||width>MAX_PREVIEW_SIDE||height>MAX_PREVIEW_SIDE||input.length!=width*height)
            throw new IllegalArgumentException("Invalid tiled dimensions");
        for(float x:input)if(!Float.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Invalid luma");
        float[] output=new float[input.length];
        for(int y=0;y<height;y+=TILE)for(int x=0;x<width;x+=TILE){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            int coreW=Math.min(TILE,width-x),coreH=Math.min(TILE,height-y);
            int left=Math.max(0,x-HALO),top=Math.max(0,y-HALO);
            int right=Math.min(width,x+coreW+HALO),bottom=Math.min(height,y+coreH+HALO);
            int tw=right-left,th=bottom-top;
            float[] tile=new float[tw*th];
            for(int row=0;row<th;row++)System.arraycopy(input,(top+row)*width+left,tile,row*tw,tw);
            float[] restored=infer(tile,tw,th);
            for(int row=0;row<coreH;row++)System.arraycopy(restored,(y-top+row)*tw+x-left,output,(y+row)*width+x,coreW);
        }
        return output;
    }
    /** Infer on degraded input or pre-upscaled bilinear luma, same size output. */
    float[] infer(float[] input,int width,int height)throws InterruptedException{
        validate(input,width,height);int n=width*height;float[] hidden=new float[n*4],result=new float[n];
        for(int y=0;y<height;y++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            for(int x=0;x<width;x++)for(int c=0;c<4;c++){
                float sum=weights[36+c];int k=c*9;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)sum+=input[clamp(y+dy,height)*width+clamp(x+dx,width)]*weights[k++];
                hidden[c*n+y*width+x]=Math.max(0,sum);
            }
        }
        for(int y=0;y<height;y++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            for(int x=0;x<width;x++){
                float sum=weights[76];int k=40;
                for(int c=0;c<4;c++)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)sum+=hidden[c*n+clamp(y+dy,height)*width+clamp(x+dx,width)]*weights[k++];
                result[y*width+x]=clip(input[y*width+x]+sum);
            }
        }return result;
    }
}
