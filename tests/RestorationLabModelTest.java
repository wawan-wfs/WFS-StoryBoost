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
        for(boolean sr:new boolean[]{false,true}){
            String name=sr?"sr2x":"deblur";
            RestorationLab model;
            try(InputStream in=Files.newInputStream(root.resolve(name+"-refined.weights.txt"))){model=RestorationLab.load(in,sr,true);}
            float[] x=new float[64];for(int i=0;i<x.length;i++)x[i]=i%8<4?.2f:.8f;
            float[] got=model.infer(x,8,8);ok(Math.abs(got[27]-(sr?0f:.26754335f))<.000002f);ok(Math.abs(got[28]-(sr?.9894026f:.530282f))<.000002f);
            for(float v:got)ok(Float.isFinite(v)&&v>=0&&v<=1);
            boolean wrong=false;try(InputStream in=Files.newInputStream(root.resolve(name+".weights.txt"))){try{RestorationLab.load(in,sr,true);}catch(IOException expected){wrong=true;}}ok(wrong);
        }
        for(boolean sr:new boolean[]{false,true})for(boolean refined:new boolean[]{false,true}){
            String name=sr?"sr2x":"deblur";RestorationLab model;
            try(InputStream in=Files.newInputStream(root.resolve(name+(refined?"-refined":"")+".weights.txt"))){model=RestorationLab.load(in,sr,refined);}
            java.util.Random random=new java.util.Random(2301);
            for(int[] shape:new int[][]{{1,1},{129,1},{1,129},{127,131},{257,263}}){
                int w=shape[0],h=shape[1];float[] x=new float[w*h];for(int i=0;i<x.length;i++)x[i]=random.nextFloat();
                ok(java.util.Arrays.equals(model.infer(x,w,h),model.inferTiled(x,w,h)));
            }
            if(refined){
                float[] x=new float[1024*720];for(int i=0;i<x.length;i++)x[i]=random.nextFloat();
                float[] y=model.inferTiled(x,1024,720);ok(y.length==x.length);for(float v:y)ok(Float.isFinite(v)&&v>=0&&v<=1);
                boolean interrupted=false;Thread.currentThread().interrupt();try{model.inferTiled(x,1024,720);}catch(InterruptedException expected){interrupted=true;}finally{Thread.interrupted();}ok(interrupted);
            }
        }
        for(int a=0;a<=255;a+=5)for(int b=0;b<=255;b+=5){
            float source=a/255f,restored=b/255f;
            ok(RestorationLab.residualDelta(source,restored,0)==0);
            ok(RestorationLab.residualDelta(source,restored,100)==Math.round((restored-source)*255));
            int previous=0;
            for(int strength=0;strength<=100;strength++){
                int delta=RestorationLab.residualDelta(source,restored,strength);
                ok(Math.abs(delta)>=Math.abs(previous));ok(Math.abs(delta)<=Math.abs(RestorationLab.residualDelta(source,restored,100)));
                previous=delta;
            }
        }
        float[] scaled=RestorationLab.bilinear2x(new float[]{0,1,0,1},2,2);
        ok(scaled.length==16);ok(Math.abs(scaled[1]-.25f)<.000001f);ok(Math.abs(scaled[2]-.75f)<.000001f);
        boolean bad=false;try{RestorationLab.bilinear2x(new float[257],257,1);}catch(IllegalArgumentException expected){bad=true;}ok(bad);
        System.out.println("PASS "+checks+" restoration lab assertions");
    }
}
