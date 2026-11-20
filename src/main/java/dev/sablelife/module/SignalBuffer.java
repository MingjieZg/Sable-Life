package dev.sablelife.module;

/** Final-output off delay. Only a positive value arms it; its clock never survives runtime cleanup. */
public final class SignalBuffer {
    private long lastTick=Long.MIN_VALUE, deadline=Long.MIN_VALUE;
    private int held, duration;

    public int tick(long now,int power,int ticks){
        power=Math.clamp(power,0,15);ticks=Math.clamp(ticks,0,12000);
        if(now<lastTick||ticks!=duration){held=0;deadline=Long.MIN_VALUE;}
        lastTick=now;duration=ticks;
        if(ticks==0){held=0;deadline=Long.MIN_VALUE;return power;}
        if(power>0){held=power;deadline=Long.MIN_VALUE;return power;}
        if(held==0)return 0;
        if(deadline==Long.MIN_VALUE)deadline=now>Long.MAX_VALUE-ticks?Long.MAX_VALUE:now+ticks;
        if(now>=deadline){held=0;deadline=Long.MIN_VALUE;return 0;}
        return held;
    }
}
