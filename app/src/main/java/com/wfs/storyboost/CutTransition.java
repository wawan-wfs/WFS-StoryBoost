package com.wfs.storyboost;
/** An override applies only to an ordered, adjacent pair of stable clip IDs. */
final class CutTransition {
    final String left,right;final int ms;
    CutTransition(String left,String right,int ms){
        if(left==null||right==null||left.equals(right)||left.isEmpty()||right.isEmpty()||ms<0||ms>1000)
            throw new IllegalArgumentException("Transisi tidak valid.");
        this.left=left;this.right=right;this.ms=ms;
    }
    boolean matches(String a,String b){return left.equals(a)&&right.equals(b);}
}
