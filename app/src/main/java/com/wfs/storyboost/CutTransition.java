package com.wfs.storyboost;
/** An override applies only to an ordered, adjacent pair of stable clip IDs. */
final class CutTransition {
    final String left,right;final int ms,curve;
    CutTransition(String left,String right,int ms){this(left,right,ms,0);}
    CutTransition(String left,String right,int ms,int curve){
        if(left==null||right==null||left.equals(right)||left.isEmpty()||right.isEmpty()||ms<0||ms>1000||curve<0||curve>3||(ms==0&&curve!=0))
            throw new IllegalArgumentException("Transisi tidak valid.");
        this.left=left;this.right=right;this.ms=ms;this.curve=curve;
    }
    boolean matches(String a,String b){return left.equals(a)&&right.equals(b);}
}
