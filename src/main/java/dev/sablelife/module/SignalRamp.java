package dev.sablelife.module;

/** World-time ramp: stopping input cancels immediately; a new activation starts from the left endpoint. */
public final class SignalRamp {
    private boolean running;
    private long began;
    private int start, end, duration;
    public int tick(long now,boolean input,int from,int to,int ticks){
        if(!input){running=false;return 0;}
        from=Math.clamp(from,0,15);to=Math.clamp(to,0,15);ticks=Math.clamp(ticks,1,12000);
        if(!running||start!=from||end!=to||duration!=ticks||now<began){
            running=true;began=now;start=from;end=to;duration=ticks;
        }
        double progress=Math.clamp((double)(now-began)/duration,0,1);
        return (int)Math.round(start+(end-start)*progress);
    }
}
