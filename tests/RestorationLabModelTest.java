package com.wfs.storyboost;
import java.io.*;
import java.nio.file.*;
public final class RestorationLabModelTest {
    static int checks;
    static void ok(boolean b){checks++;if(!b)throw new AssertionError("model check "+checks);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);
        for(boolean sr:new boolean[]{false,true}){
            RestorationLab model;
            try(InputStream in=Files.newInputStream(root.resolve((sr?"sr2x":"deblur")+".weights.txt"))){model=RestorationLab.load(in,sr);}
            float[] x=new float[64];for(int i=0;i<x.length;i++)x[i]=i%8<4?.2f:.8f;
            float[] original=x.clone(),got=model.infer(x,8,8);
            ok(Math.abs(got[27]-(sr?.0696245f:.18993203f))<.000002f);
            ok(Math.abs(got[28]-(sr?.94676805f:.7977222f))<.000002f);
            ok(java.util.Arrays.equals(original,x));
            for(float v:got)ok(Float.isFinite(v)&&v>=0&&v<=1);
            boolean bad=false;try{RestorationLab.load(new ByteArrayInputStream(new byte[]{1}),sr);}catch(IOException expected){bad=true;}ok(bad);
            bad=false;try{model.infer(new float[]{Float.NaN},1,1);}catch(IllegalArgumentException expected){bad=true;}ok(bad);
            bad=false;Thread.currentThread().interrupt();try{model.infer(x,8,8);}catch(InterruptedException expected){bad=true;}finally{Thread.interrupted();}ok(bad);
        }
        float[] scaled=RestorationLab.bilinear2x(new float[]{0,1,0,1},2,2);
        ok(scaled.length==16);ok(Math.abs(scaled[1]-.25f)<.000001f);ok(Math.abs(scaled[2]-.75f)<.000001f);
        boolean bad=false;try{RestorationLab.bilinear2x(new float[257],257,1);}catch(IllegalArgumentException expected){bad=true;}ok(bad);
        System.out.println("PASS "+checks+" restoration lab assertions");
    }
}
