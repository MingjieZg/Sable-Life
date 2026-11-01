package dev.sablelife.module;

/** One cycle: accumulate, latch one output, ignore damage until the output finishes, then rearm. */
public final class DamageAccumulator {
    private double accumulated;
    private boolean pending,latched,high;
    private long lastTick=Long.MIN_VALUE;
    private int remaining;
    public void add(double damage,double threshold){
        if(latched||!Double.isFinite(damage)||damage<=0||!Double.isFinite(threshold)||threshold<1)return;
        accumulated=Math.min(threshold,accumulated+damage);
        if(accumulated>=threshold){latched=true;pending=true;}
    }
    private void finish(){accumulated=0;pending=latched=high=false;remaining=0;}
    public boolean tick(long now,boolean active,int duration){
        if(!active){if(high||remaining>0)finish();high=false;lastTick=now;return false;}
        if(lastTick==now)return high;lastTick=now;
        if(remaining>0){remaining--;return high=true;}
        if(high){finish();return false;}
        if(!pending)return high=false;
        pending=false;remaining=Math.max(1,duration)-1;return high=true;
    }
    public double accumulated(){return accumulated;}
    public long pending(){return pending?1:0;}
    public boolean high(){return high;}
}
