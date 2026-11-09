package dev.sablelife.ownership;

/** Player-to-spawn-origin distances, including clearance for the template's footprint. */
public final class WildSpawnDistances {
    public static int minimum(double width,double depth,int configuredMinimum){
        return Math.max(configuredMinimum,(int)Math.ceil(Math.hypot(width,depth)/2+8));
    }
    /** Zero means no legal distance. The caller retries later without disabling the rule. */
    public static int choices(int minimum,int configuredMaximum){
        return Math.max(0,configuredMaximum-minimum+1);
    }
    private WildSpawnDistances(){}
}
