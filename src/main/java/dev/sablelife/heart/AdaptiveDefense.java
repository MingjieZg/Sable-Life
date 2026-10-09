package dev.sablelife.heart;

import java.util.*;

/** Bounded rolling pre-adaptive damage; game ticks define seconds, independent of hit count. */
public final class AdaptiveDefense {
    public static final double DEFAULT_THRESHOLD=100,DEFAULT_MAXIMUM=80;
    public static final int DEFAULT_WINDOW=20;
    private static final double RECOVERY_TICKS=6;
    private record Sample(long tick,double damage){}
    private final Deque<Sample> samples=new ArrayDeque<>();
    private boolean enabled;private double threshold=DEFAULT_THRESHOLD,maximum=DEFAULT_MAXIMUM;
    private int window=DEFAULT_WINDOW;private double total,reduction;private long last=Long.MIN_VALUE;
    public boolean enabled(){return enabled;} public double threshold(){return threshold;} public double maximum(){return maximum;} public int window(){return window;}
    public double dps(){return enabled?total*20/window:0;} public double reduction(){return enabled?reduction:0;}
    public boolean configure(boolean enabled,double threshold,double maximum,int window){
        if(!Double.isFinite(threshold)||threshold<.1||threshold>1000000||!Double.isFinite(maximum)||maximum<0||maximum>100||window<2||window>200)return false;
        if(this.enabled!=enabled||this.threshold!=threshold||this.maximum!=maximum||this.window!=window){this.enabled=enabled;this.threshold=threshold;this.maximum=maximum;this.window=window;reset();}return true;
    }
    public void reset(){samples.clear();total=reduction=0;last=Long.MIN_VALUE;}
    private double target(){double dps=dps();return dps<=threshold?0:Math.min(maximum/100,1-threshold/dps);}
    public void tick(long now){
        if(!enabled){reset();return;}if(last!=Long.MIN_VALUE&&now<last)reset();
        while(!samples.isEmpty()&&samples.peekFirst().tick<=now-window)total-=samples.removeFirst().damage;
        if(samples.isEmpty())total=0;else total=Math.max(0,total);
        double desired=target();
        if(last==Long.MIN_VALUE)reduction=desired;
        else if(now>last){double delta=(double)now-last;reduction=desired+(Math.max(reduction,desired)-desired)*Math.exp(-delta/RECOVERY_TICKS);}
        reduction=Math.clamp(Math.max(reduction,desired),0,maximum/100);if(reduction<.0001)reduction=0;last=now;
    }
    /** Include the proposed hit: large first bursts are not invisible to the rolling detector. */
    public float apply(float damage,long now){
        if(!Float.isFinite(damage)||damage<=0)return 0;if(!enabled)return damage;
        tick(now);
        if(!samples.isEmpty()&&samples.peekLast().tick==now){var previous=samples.removeLast();samples.addLast(new Sample(now,previous.damage+damage));}
        else samples.addLast(new Sample(now,damage));
        total+=damage;reduction=Math.max(reduction,target());
        return Math.max(Float.MIN_VALUE,(float)(damage*(1-reduction)));
    }
    int buckets(){return samples.size();}
}
