package dev.sablelife.heart;

/** Minecraft's cumulative XP from level zero. Conversions are for authoring; level-mode pickups add levels directly. */
public final class ExperienceUnits {
    public static final int MAX_POINTS=30970,MAX_LEVELS=100;
    public static int points(int levels){
        int n=Math.clamp(levels,0,MAX_LEVELS);
        return n<=16?n*n+6*n:n<=31?(5*n*n-81*n+720)/2:(9*n*n-325*n+4440)/2;
    }
    public static int levels(int points){
        int amount=Math.clamp(points,0,MAX_POINTS),low=0,high=MAX_LEVELS;
        while(low<high){int mid=(low+high+1)/2;if(points(mid)<=amount)low=mid;else high=mid-1;}return low;
    }
    private ExperienceUnits(){}
}
