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
    static final String DEBLUR_SHA="89e355238c2c233e6442800b0a8b3c2d1633ee6d195cdcc532a3d42a8d6dfcf4";
    static final String SR_SHA="dea96181a956528cfff99cdf94856f5b2a594ed72e467f025e0c4dab99483b01";
    private final float[] weights;
    private RestorationLab(float[] weights){this.weights=weights;}
    static RestorationLab load(InputStream stream,boolean sr)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[512];int n;
        while((n=stream.read(block))!=-1){if(out.size()+n>4096)throw new IOException("Oversized model");out.write(block,0,n);}
        byte[] raw=out.toByteArray();StringBuilder hex=new StringBuilder();
        for(byte v:MessageDigest.getInstance("SHA-256").digest(raw))hex.append(String.format(Locale.ROOT,"%02x",v&255));
        if(!hex.toString().equals(sr?SR_SHA:DEBLUR_SHA))throw new IOException("Invalid model SHA-256");
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
